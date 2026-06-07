package com.xslczx.audios.processor;

import com.xslczx.audios.datas.Config;
import com.xslczx.audios.morse.MorseAudio;

import java.io.IOException;

/**
 * 在尾部添加AI摩斯码
 */
public class AITailPcmAppender implements PcmEffectProcessor {
    private static final String AI_MARKER = "AI";

    private int sampleRate;
    private final int wpm;
    private final double volume;
    private final int frequency;
    private int channelCount;
    private byte[] sourcePcm;

    public AITailPcmAppender(Config config) {
        this.wpm = config.wpm;
        this.volume = config.volume;
        this.frequency = config.frequency;
    }

    @Override
    public void prepare(int sampleRate, int channelCount, int bitDepth, int bitrate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be greater than 0");
        }
        if (channelCount <= 0) {
            throw new IllegalArgumentException("channelCount must be greater than 0");
        }
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
    }

    @Override
    public void write(byte[] pcmData) throws IOException {
        if (pcmData == null) {
            throw new IOException("pcmData must not be null");
        }
        sourcePcm = pcmData;
    }

    @Override
    public byte[] flush() {
        if (sourcePcm == null) {
            throw new IllegalStateException("PCM data must be written before flush");
        }
        byte[] extraPcm = new MorseAudio().morseWord2Sound(AI_MARKER, frequency, wpm, sampleRate, volume, channelCount);

        int mainLength = sourcePcm.length;
        int extraLength = extraPcm.length;

        byte[] result = new byte[mainLength + extraLength];
        System.arraycopy(sourcePcm, 0, result, 0, mainLength);
        System.arraycopy(extraPcm, 0, result, mainLength, extraLength);

        return result;
    }
}
