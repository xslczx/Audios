package com.xslczx.audios.processor;

import com.xslczx.audios.morse.MorseAudio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 在尾部添加AI摩斯码
 */
public class AITailPcmAppender implements PcmEffectProcessor {

    private int sampleRate;
    private final int wpm;

    public AITailPcmAppender(int wpm) {
        this.wpm = wpm;
    }

    public AITailPcmAppender() {
        this(13);
    }

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    @Override
    public void prepare(int sampleRate, int channelCount, int bitDepth, int bitrate) {
        this.sampleRate = sampleRate;
    }

    @Override
    public void write(byte[] pcmData) throws IOException{
        buffer.write(pcmData);
    }

    @Override
    public byte[] flush() {
        byte[] main = buffer.toByteArray();
        byte[] extraPcm = new MorseAudio().morseWord2Sound("AI", 500, wpm, sampleRate, 0.1);

        if (extraPcm == null || extraPcm.length == 0) return main;

        byte[] result = new byte[main.length + extraPcm.length];
        System.arraycopy(main, 0, result, 0, main.length);
        System.arraycopy(extraPcm, 0, result, main.length, extraPcm.length);
        return result;
    }
}
