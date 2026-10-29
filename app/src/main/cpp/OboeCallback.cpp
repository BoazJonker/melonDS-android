#include "OboeCallback.h"
#include "types.h"
#include "Platform.h"
#include "SPU.h"
#include <algorithm>

using namespace melonDS;

#define INTERNAL_FRAME_RATE 59.8260982880808f

OboeCallback::OboeCallback(int volume, void (*onErrorCallback)(void), std::ostream* recordingStream) : _volume(volume), onErrorCallback(onErrorCallback), _recordingStream(recordingStream) {
    audioSampleFrac = 0;
}

oboe::DataCallbackResult
OboeCallback::onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) {
    auto currentInstance = activeInstance.lock();

    if (!currentInstance)
    {
        memset(audioData, 0, numFrames * sizeof(u16) * 2);
        return oboe::DataCallbackResult::Continue;
    }

    int len = numFrames;

    double skew = std::clamp(60.0 / INTERNAL_FRAME_RATE, 0.995, 1.005);
    currentInstance->setAudioOutputSkew(skew);

    int len_in = getNumSamplesOut(len);
    if (len_in > numFrames) len_in = numFrames;

    int num_in = currentInstance->readAudioOutput((s16*) audioData, len_in);

    if (num_in < 1)
    {
        memset(audioData, 0, len * sizeof(s16) * 2);
        return oboe::DataCallbackResult::Continue;
    }

    if (_volume < 256)
    {
        s16* samples = (s16*) audioData;
        for (int i = 0; i < num_in * 2; i++)
            samples[i] = ((s32) samples[i] * _volume) >> 8;
    }

    if (num_in < len_in)
    {
        // The emulator is running slower than real time, so there are fewer samples than needed. Stretch the samples received over the whole buffer, so
        // that the sound plays a little slower but stays smooth, instead of repeating the last sample (which sounds like stuttering)
        s16* samples = (s16*) audioData;
        stretchBuffer.assign(samples, samples + num_in * 2);
        for (int i = 0; i < len_in; i++)
        {
            float position = (float) i * (float) (num_in - 1) / (float) std::max(len_in - 1, 1);
            int index = (int) position;
            int nextIndex = std::min(index + 1, num_in - 1);
            float fraction = position - (float) index;
            for (int channel = 0; channel < 2; channel++)
            {
                float a = stretchBuffer[index * 2 + channel];
                float b = stretchBuffer[nextIndex * 2 + channel];
                samples[i * 2 + channel] = (s16) (a + (b - a) * fraction);
            }
        }
    }

    if (_recordingStream) [[unlikely]]
        _recordingStream->write((char*) audioData, numFrames * sizeof(s16) * 2);

    return oboe::DataCallbackResult::Continue;
}

void OboeCallback::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result result)
{
    if (result == oboe::Result::ErrorDisconnected && onErrorCallback != nullptr) {
        onErrorCallback();
    }
}

int OboeCallback::getNumSamplesOut(int len)
{
    // TODO: adjust to game speed
    float f_len_in = len /* * (curFPS/60.0)*/;
    f_len_in += audioSampleFrac;
    int len_in = (int) floor(f_len_in);
    audioSampleFrac = f_len_in - len_in;

    return len_in;
}