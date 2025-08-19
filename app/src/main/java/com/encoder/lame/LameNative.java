package com.encoder.lame;

public class LameNative {

    static {
        // 先加载交叉编译好的 libmp3lame.so
        System.loadLibrary("mp3lame");
        // 再加载 JNI 封装库 liblamejni.so
        System.loadLibrary("lamejni");
    }

    // 初始化编码器
    public static native int initEncoder(int numChannels, int sampleRate, int bitRate, int mode, int quality);

    // PCM 编码为 MP3
    public static native int encode(short[] pcm, int numSamples, byte[] mp3buf);

    // 冲刷剩余 MP3 数据
    public static native int flush(byte[] mp3buf);

    // 关闭编码器
    public static native void close();
}
