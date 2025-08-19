#include <jni.h>
#include <stdlib.h>
#include "include/lame.h"

static lame_t gLame = NULL;

JNIEXPORT jint JNICALL
Java_com_encoder_lame_LameNative_initEncoder(JNIEnv *env, jobject thiz,
                                             jint numChannels, jint sampleRate,
                                             jint bitRate, jint mode, jint quality) {
    if (gLame != NULL) {
        lame_close(gLame);
        gLame = NULL;
    }
    gLame = lame_init();
    if (!gLame) return -1;

    lame_set_num_channels(gLame, numChannels);
    lame_set_in_samplerate(gLame, sampleRate);
    lame_set_brate(gLame, bitRate);
    lame_set_mode(gLame, mode);
    lame_set_quality(gLame, quality);

    if (lame_init_params(gLame) < 0) {
        lame_close(gLame);
        gLame = NULL;
        return -2;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_encoder_lame_LameNative_encode(JNIEnv *env, jobject thiz,
                                        jshortArray pcm, jint numSamples,
                                        jbyteArray mp3buf) {
    if (!gLame) return -1;

    jshort *pcmPtr = (*env)->GetShortArrayElements(env, pcm, NULL);
    jbyte *mp3Ptr = (*env)->GetByteArrayElements(env, mp3buf, NULL);

    int bytesWritten = lame_encode_buffer_interleaved(
            gLame, pcmPtr, numSamples, (unsigned char *)mp3Ptr, (*env)->GetArrayLength(env, mp3buf)
    );

    (*env)->ReleaseShortArrayElements(env, pcm, pcmPtr, 0);
    (*env)->ReleaseByteArrayElements(env, mp3buf, mp3Ptr, 0);

    return bytesWritten;
}

JNIEXPORT jint JNICALL
Java_com_encoder_lame_LameNative_flush(JNIEnv *env, jobject thiz, jbyteArray mp3buf) {
    if (!gLame) return -1;

    jbyte *mp3Ptr = (*env)->GetByteArrayElements(env, mp3buf, NULL);
    int bytesFlushed = lame_encode_flush(gLame, (unsigned char *)mp3Ptr, (*env)->GetArrayLength(env, mp3buf));
    (*env)->ReleaseByteArrayElements(env, mp3buf, mp3Ptr, 0);

    return bytesFlushed;
}

JNIEXPORT void JNICALL
Java_com_encoder_lame_LameNative_close(JNIEnv *env, jobject thiz) {
    if (gLame) {
        lame_close(gLame);
        gLame = NULL;
    }
}
