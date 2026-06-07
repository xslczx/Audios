package com.xslczx.audios.morse;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class MorseAudio {
    private static final String LETTER_SEPARATOR = "@";
    private static final String WORD_SEPARATOR = "/";
    private static final String DEFAULT_AI_LABEL = "AI";
    private static final double MILLISECONDS_PER_MINUTE = 1200.0;

    /**
     * 点
     */
    private static final int DOT_RATIO = 1;
    /**
     * 划
     */
    private static final int DASH_RATIO = 3;
    /**
     * 大间隔
     */
    private static final int WORD_GAP_RATIO = 7;

    public byte[] morseWord2Sound(String word, int frequency, int wpm, int sampleRate, double volume, int channelCount) {
        validateAudioConfig(frequency, wpm, sampleRate, volume, channelCount);
        return codeConvert2Sound(
                encodeWord(word),
                frequency,
                toUnitDurationMs(wpm),
                sampleRate,
                volume,
                channelCount
        );
    }

    public byte[] morsePhrase2Sound(List<String> words, int frequency, int wpm, int sampleRate, double volume, int channelCount) {
        validateAudioConfig(frequency, wpm, sampleRate, volume, channelCount);
        return codeConvert2Sound(
                encodePhrase(words),
                frequency,
                toUnitDurationMs(wpm),
                sampleRate,
                volume,
                channelCount
        );
    }


    /**
     * 将单个单词编码成摩斯码（带 @ 间隔）
     */
    public String encodeWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            throw new IllegalArgumentException("word must not be blank");
        }

        String normalizedWord = word.trim();
        StringBuilder result = new StringBuilder(normalizedWord.length() * 4);
        for (int index = 0; index < normalizedWord.length(); index++) {
            String letter = String.valueOf(normalizedWord.charAt(index)).toUpperCase();
            String morse = MorseCoder.codeMap.get(letter);
            if (morse == null) {
                throw new IllegalArgumentException(letter + " 无法编码为摩尔斯电码");
            }
            result.append(morse);
            if (index < normalizedWord.length() - 1) {
                result.append(LETTER_SEPARATOR);
            }
        }
        return result.toString();
    }

    /**
     * 将词组编码成摩斯码（带 @ 和 / 间隔）
     */
    public String encodePhrase(List<String> words) {
        if (words == null || words.isEmpty()) {
            throw new IllegalArgumentException("words must not be empty");
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            result.append(encodeWord(words.get(i)));
            if (i < words.size() - 1) {
                result.append(WORD_SEPARATOR);
            }
        }
        return result.toString();
    }

    /**
     * 将摩尔斯电码转成 PCM 音频
     *
     * @param codeString 摩尔斯电码
     * @param frequency  频率
     * @param unitMs     单位时间 (ms)
     * @return PCM 音频字节数组 (16bit PCM, little endian)
     */
    public byte[] codeConvert2Sound(String codeString, int frequency, int unitMs, int sampleRate, double volume, int channelCount) {
        if (codeString == null || codeString.isEmpty()) {
            throw new IllegalArgumentException("codeString must not be empty");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int dot = unitMs * DOT_RATIO;       // 1 单位
        int dash = unitMs * DASH_RATIO;      // 3 单位
        int letterSpace = unitMs * 3;      // 字母间隔
        int wordSpace = unitMs * WORD_GAP_RATIO; // 单词间隔

        for (int i = 0; i < codeString.length(); i++) {
            char c = codeString.charAt(i);
            int durationMs = 0;
            boolean isTone = false;

            switch (c) {
                case '.': // 点
                    durationMs = dot;
                    isTone = true;
                    break;
                case '-': // 划
                    durationMs = dash;
                    isTone = true;
                    break;
                case '@': // 字母间隔
                    durationMs = letterSpace;
                    break;
                case '/': // 单词间隔
                    durationMs = wordSpace;
                    break;
                default:
                    continue;
            }

            int sampleCount = (int) ((durationMs / 1000.0) * sampleRate);
            for (int sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++) {
                short sample;
                if (isTone) {
                    double angle = 2.0 * Math.PI * frequency * sampleIndex / sampleRate;
                    sample = (short) (Math.sin(angle) * Short.MAX_VALUE * volume);
                } else {
                    sample = 0;
                }

                // 根据 channelCount 写入交错声道
                for (int channelIndex = 0; channelIndex < channelCount; channelIndex++) {
                    out.write(sample & 0xff);
                    out.write((sample >> 8) & 0xff);
                }
            }

            // 点和划后面补一个 **1 单位静音**
            if (isTone) {
                int gapSamples = (int) ((unitMs / 1000.0) * sampleRate);
                for (int gapIndex = 0; gapIndex < gapSamples; gapIndex++) {
                    for (int channelIndex = 0; channelIndex < channelCount; channelIndex++) {
                        out.write(0);
                        out.write(0);
                    }
                }
            }
        }

        return out.toByteArray();
    }

    private int toUnitDurationMs(int wpm) {
        return (int) (MILLISECONDS_PER_MINUTE / wpm);
    }

    private void validateAudioConfig(int frequency, int wpm, int sampleRate, double volume, int channelCount) {
        if (frequency <= 0) {
            throw new IllegalArgumentException("frequency must be greater than 0");
        }
        if (wpm <= 0) {
            throw new IllegalArgumentException("wpm must be greater than 0");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be greater than 0");
        }
        if (volume <= 0 || volume > 1.0) {
            throw new IllegalArgumentException("volume must be in range (0, 1]");
        }
        if (channelCount <= 0) {
            throw new IllegalArgumentException("channelCount must be greater than 0");
        }
    }

}
