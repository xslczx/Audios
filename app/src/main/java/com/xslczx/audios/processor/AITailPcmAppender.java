package com.xslczx.audios.processor;

import com.xslczx.audios.datas.Config;
import com.xslczx.audios.morse.MorseAudio;

import java.io.IOException;

/**
 * 在尾部添加AI摩斯码
 */
public class AITailPcmAppender implements PcmEffectProcessor {

    private int sampleRate;
    private final int wpm;
    private final double volume;
    private final int frequency;
    private int channelCount;
    private byte[] main;

    public AITailPcmAppender(Config config) {
        this.wpm = config.wpm;
        this.volume = config.volume;
        this.frequency = config.frequency;
    }

    @Override
    public void prepare(int sampleRate, int channelCount, int bitDepth, int bitrate) {
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
    }

    @Override
    public void write(byte[] pcmData) throws IOException {
        main = pcmData;
    }

    @Override
    public byte[] flush() {
        byte[] extraPcm = new MorseAudio().morseWord2Sound("AI", frequency, wpm, sampleRate, volume, channelCount);

        int mainLength = main.length;
        int extraLength = extraPcm.length;

        byte[] result = new byte[mainLength + extraLength];
        System.arraycopy(main, 0, result, 0, mainLength);
        System.arraycopy(extraPcm, 0, result, mainLength, extraLength);

        return result;
    }
}
