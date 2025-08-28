#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "include/lame.h"
#include "lame_jni.h"

// Global LAME state
static lame_global_flags *glf = NULL;

JNIEXPORT void JNICALL Java_com_encoder_lame_LameNative_init(
        JNIEnv *env, jclass cls, jint inSamplerate, jint outChannel,
        jint outSamplerate, jint outBitrate, jint quality) {
    if (glf != NULL) {
        lame_close(glf);
        glf = NULL;
    }
    glf = lame_init();
    lame_set_in_samplerate(glf, inSamplerate);
    lame_set_num_channels(glf, outChannel);
    lame_set_out_samplerate(glf, outSamplerate);
    lame_set_brate(glf, outBitrate);
    lame_set_quality(glf, quality);
    lame_init_params(glf);
}

JNIEXPORT jint JNICALL Java_com_encoder_lame_LameNative_encodeInterleaved(
        JNIEnv *env, jclass cls, jshortArray pcm, jint samplesPerChannel, jbyteArray mp3buf) {
    if (glf == NULL) return -1;

    jshort *j_pcm = (*env)->GetShortArrayElements(env, pcm, NULL);
    const jsize mp3buf_size = (*env)->GetArrayLength(env, mp3buf);
    jbyte *j_mp3buf = (*env)->GetByteArrayElements(env, mp3buf, NULL);

    int result = lame_encode_buffer_interleaved(glf,
                                                (short *) j_pcm,
                                                samplesPerChannel,
                                                (unsigned char *) j_mp3buf,
                                                mp3buf_size);
    (*env)->ReleaseShortArrayElements(env, pcm, j_pcm, 0);
    (*env)->ReleaseByteArrayElements(env, mp3buf, j_mp3buf, 0);

    return result;
}

JNIEXPORT jint JNICALL Java_com_encoder_lame_LameNative_encodeMono(
        JNIEnv *env, jclass cls, jshortArray pcm, jint samples, jbyteArray mp3buf) {
    if (glf == NULL) return -1;

    jshort *j_pcm = (*env)->GetShortArrayElements(env, pcm, NULL);
    const jsize mp3buf_size = (*env)->GetArrayLength(env, mp3buf);
    jbyte *j_mp3buf = (*env)->GetByteArrayElements(env, mp3buf, NULL);

    int result = lame_encode_buffer(glf,
                                    (short *) j_pcm,
                                    NULL,
                                    samples,
                                    (unsigned char *) j_mp3buf,
                                    mp3buf_size);
    (*env)->ReleaseShortArrayElements(env, pcm, j_pcm, 0);
    (*env)->ReleaseByteArrayElements(env, mp3buf, j_mp3buf, 0);

    return result;
}

JNIEXPORT jint JNICALL Java_com_encoder_lame_LameNative_flush(
        JNIEnv *env, jclass cls, jbyteArray mp3buf) {
    if (glf == NULL) return -1;

    const jsize mp3buf_size = (*env)->GetArrayLength(env, mp3buf);
    jbyte* j_mp3buf = (*env)->GetByteArrayElements(env, mp3buf, NULL);

    int result = lame_encode_flush(glf, (unsigned char*) j_mp3buf, mp3buf_size);

    (*env)->ReleaseByteArrayElements(env, mp3buf, j_mp3buf, 0);
    return result;
}

JNIEXPORT void JNICALL Java_com_encoder_lame_LameNative_close(
        JNIEnv *env, jclass cls) {
    if (glf != NULL) {
        lame_close(glf);
        glf = NULL;
    }
}
