package com.encoder.lame;

public class LameNative {

    static {
        // 先加载交叉编译好的 libmp3lame.so
        System.loadLibrary("mp3lame");
        // 再加载 JNI 封装库 liblamejni.so
        System.loadLibrary("lamejni");
    }

    public static native void init(int inSamplerate, int outChannel, int outSamplerate, int outBitrate, int quality);

    public static native int encodeInterleaved(short[] pcm, int samplesPerChannel, byte[] mp3buf);

    public static native int encodeMono(short[] pcmLeft, int samples, byte[] mp3buf);

    public static native int flush(byte[] mp3buf);

    public static native void close();
}
