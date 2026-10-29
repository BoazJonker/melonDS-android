#ifndef MELONDS_OBOECALLBACK_H
#define MELONDS_OBOECALLBACK_H

#include <oboe/Oboe.h>
#include <fstream>
#include <vector>
#include "MelonInstance.h"

class OboeCallback : public oboe::AudioStreamCallback {
private:
    int _volume;
    void (*onErrorCallback)(void);
    std::ostream* _recordingStream;
    float audioSampleFrac;
    // Copy of the samples received, used to stretch them when the emulator falls behind
    std::vector<int16_t> stretchBuffer;

public:
    std::weak_ptr<MelonDSAndroid::MelonInstance> activeInstance;

    OboeCallback(int volume, void (*onErrorCallback)(void)) : OboeCallback(volume, onErrorCallback, nullptr) { };
    OboeCallback(int volume, void (*onErrorCallback)(void), std::ostream* recordingStream);
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result result) override;
    
private:
    int getNumSamplesOut(int len);
};


#endif //MELONDS_OBOECALLBACK_H
