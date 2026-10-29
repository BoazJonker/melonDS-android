// Bridge between the app and the mGBA core. One game runs at a time; the app calls runFrame() from its emulation thread and copyFrame() from its
// rendering thread.
#include <jni.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>

#include <mgba/core/blip_buf.h>
#include <mgba/core/config.h>
#include <mgba/core/core.h>
#include <mgba/core/log.h>
#include <mgba/core/serialize.h>
#include <mgba/gba/interface.h>
#include <mgba-util/vfs.h>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_ERROR(...) __android_log_print(ANDROID_LOG_ERROR, "Unbound", __VA_ARGS__)
#else
#define LOG_ERROR(...) fprintf(stderr, __VA_ARGS__)
#endif

#define JNI_FN(name) Java_com_boazjonker_unbound_emu_NativeCore_##name

static struct mCore* core;
static color_t* backBuffer;
static color_t* frontBuffer;
static unsigned videoWidth;
static unsigned videoHeight;
static pthread_mutex_t frameMutex = PTHREAD_MUTEX_INITIALIZER;
static int16_t audioScratch[8192];

static void logMessage(struct mLogger* logger, int category, enum mLogLevel level, const char* format, va_list args) {
    (void) logger;
    (void) category;
    if (level & (mLOG_FATAL | mLOG_ERROR)) {
        char message[256];
        vsnprintf(message, sizeof(message), format, args);
        LOG_ERROR("%s", message);
    }
}

static struct mLogger logger = { .log = logMessage };

static void unloadCore(void) {
    if (!core) {
        return;
    }
    // Deinit closes the save file, flushing any pending in-game save
    core->unloadROM(core);
    mCoreConfigDeinit(&core->config);
    core->deinit(core);
    core = NULL;
    free(backBuffer);
    free(frontBuffer);
    backBuffer = NULL;
    frontBuffer = NULL;
}

static char* copyString(JNIEnv* env, jstring string) {
    if (!string) {
        return NULL;
    }
    const char* chars = (*env)->GetStringUTFChars(env, string, NULL);
    char* copy = strdup(chars);
    (*env)->ReleaseStringUTFChars(env, string, chars);
    return copy;
}

JNIEXPORT jboolean JNICALL JNI_FN(load)(JNIEnv* env, jclass clazz, jstring romPath, jstring patchPath, jstring savePath, jint sampleRate) {
    (void) clazz;
    unloadCore();
    mLogSetDefaultLogger(&logger);

    char* rom = copyString(env, romPath);
    char* patch = copyString(env, patchPath);
    char* save = copyString(env, savePath);
    jboolean result = JNI_FALSE;

    struct VFile* romFile = VFileOpen(rom, O_RDONLY);
    if (!romFile) {
        LOG_ERROR("Could not open ROM %s", rom);
        goto done;
    }

    core = mCoreFindVF(romFile);
    if (!core || !core->init(core)) {
        LOG_ERROR("Not a GBA ROM");
        romFile->close(romFile);
        core = NULL;
        goto done;
    }

    mCoreInitConfig(core, NULL);
    struct mCoreOptions options = {
        .useBios = false,
        .skipBios = true,
        .volume = 0x100,
        .sampleRate = (unsigned) sampleRate,
    };
    mCoreConfigLoadDefaults(&core->config, &options);
    mCoreLoadConfig(core);

    core->desiredVideoDimensions(core, &videoWidth, &videoHeight);
    backBuffer = calloc(videoWidth * videoHeight, sizeof(color_t));
    frontBuffer = calloc(videoWidth * videoHeight, sizeof(color_t));
    core->setVideoBuffer(core, backBuffer, videoWidth);

    if (!core->loadROM(core, romFile)) {
        LOG_ERROR("Could not load ROM");
        romFile->close(romFile);
        unloadCore();
        goto done;
    }

    if (patch) {
        struct VFile* patchFile = VFileOpen(patch, O_RDONLY);
        if (patchFile) {
            if (!core->loadPatch(core, patchFile)) {
                LOG_ERROR("Could not apply patch %s", patch);
            }
            patchFile->close(patchFile);
        }
    }

    struct VFile* saveFile = VFileOpen(save, O_CREAT | O_RDWR);
    if (saveFile) {
        core->loadSave(core, saveFile);
    } else {
        LOG_ERROR("Could not open save file %s", save);
    }

    core->setAudioBufferSize(core, 1024);
    blip_set_rates(core->getAudioChannel(core, 0), core->frequency(core), sampleRate);
    blip_set_rates(core->getAudioChannel(core, 1), core->frequency(core), sampleRate);

    core->reset(core);
    result = JNI_TRUE;

done:
    free(rom);
    free(patch);
    free(save);
    return result;
}

JNIEXPORT void JNICALL JNI_FN(unload)(JNIEnv* env, jclass clazz) {
    (void) env;
    (void) clazz;
    pthread_mutex_lock(&frameMutex);
    unloadCore();
    pthread_mutex_unlock(&frameMutex);
}

JNIEXPORT void JNICALL JNI_FN(reset)(JNIEnv* env, jclass clazz) {
    (void) env;
    (void) clazz;
    if (core) {
        core->reset(core);
    }
}

/**
 * Runs one frame with the given GBA keys held and writes the produced audio, as interleaved stereo samples, into audioOut. Returns the number of stereo
 * samples written.
 */
JNIEXPORT jint JNICALL JNI_FN(runFrame)(JNIEnv* env, jclass clazz, jint keys, jshortArray audioOut) {
    (void) clazz;
    if (!core) {
        return 0;
    }

    core->setKeys(core, (uint32_t) keys);
    core->runFrame(core);

    pthread_mutex_lock(&frameMutex);
    memcpy(frontBuffer, backBuffer, videoWidth * videoHeight * sizeof(color_t));
    pthread_mutex_unlock(&frameMutex);

    blip_t* left = core->getAudioChannel(core, 0);
    blip_t* right = core->getAudioChannel(core, 1);
    int capacity = (*env)->GetArrayLength(env, audioOut) / 2;
    if (capacity > (int) (sizeof(audioScratch) / sizeof(*audioScratch) / 2)) {
        capacity = sizeof(audioScratch) / sizeof(*audioScratch) / 2;
    }
    int available = blip_samples_avail(left);
    if (available > capacity) {
        available = capacity;
    }
    int produced = blip_read_samples(left, audioScratch, available, true);
    blip_read_samples(right, audioScratch + 1, available, true);
    if (produced > 0) {
        (*env)->SetShortArrayRegion(env, audioOut, 0, produced * 2, audioScratch);
    }
    return produced;
}

/**
 * Copies the latest frame into the given direct buffer, as RGBA pixels. Returns false if no game is running.
 */
JNIEXPORT jboolean JNICALL JNI_FN(copyFrame)(JNIEnv* env, jclass clazz, jobject buffer) {
    (void) clazz;
    void* destination = (*env)->GetDirectBufferAddress(env, buffer);
    jlong capacity = (*env)->GetDirectBufferCapacity(env, buffer);
    jboolean copied = JNI_FALSE;
    pthread_mutex_lock(&frameMutex);
    size_t size = videoWidth * videoHeight * sizeof(color_t);
    if (core && frontBuffer && destination && capacity >= (jlong) size) {
        // The core leaves the unused byte of each pixel at 0, so make every pixel opaque
        uint32_t* out = destination;
        for (size_t i = 0; i < videoWidth * videoHeight; ++i) {
            out[i] = frontBuffer[i] | 0xFF000000;
        }
        copied = JNI_TRUE;
    }
    pthread_mutex_unlock(&frameMutex);
    return copied;
}

JNIEXPORT jboolean JNICALL JNI_FN(saveState)(JNIEnv* env, jclass clazz, jstring path) {
    (void) clazz;
    if (!core) {
        return JNI_FALSE;
    }
    char* file = copyString(env, path);
    struct VFile* vf = VFileOpen(file, O_CREAT | O_TRUNC | O_RDWR);
    free(file);
    if (!vf) {
        return JNI_FALSE;
    }
    bool success = mCoreSaveStateNamed(core, vf, SAVESTATE_RTC);
    vf->close(vf);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL JNI_FN(loadState)(JNIEnv* env, jclass clazz, jstring path) {
    (void) clazz;
    if (!core) {
        return JNI_FALSE;
    }
    char* file = copyString(env, path);
    struct VFile* vf = VFileOpen(file, O_RDONLY);
    free(file);
    if (!vf) {
        return JNI_FALSE;
    }
    bool success = mCoreLoadStateNamed(core, vf, SAVESTATE_RTC);
    vf->close(vf);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL JNI_FN(getGameCode)(JNIEnv* env, jclass clazz) {
    (void) clazz;
    char code[16] = { 0 };
    if (core) {
        core->getGameCode(core, code);
    }
    return (*env)->NewStringUTF(env, code);
}

JNIEXPORT jint JNICALL JNI_FN(getVideoWidth)(JNIEnv* env, jclass clazz) {
    (void) env;
    (void) clazz;
    return (jint) videoWidth;
}

JNIEXPORT jint JNICALL JNI_FN(getVideoHeight)(JNIEnv* env, jclass clazz) {
    (void) env;
    (void) clazz;
    return (jint) videoHeight;
}
