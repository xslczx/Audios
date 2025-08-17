package com.xslczx.audios.morse;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class MorseAudio {

    /**
     * 点
     */
    private static final int dotRatio = 1;
    /**
     * 划
     */
    private static final int rowRatio = 3;
    /**
     * 大间隔
     */
    private static final int blankRatio = 7;

    /**
     * 单词 -> PCM 音频
     */
    public byte[] morseWord2Sound(String word, int frequency, int wpm, int sampleRate, double volume) {
        String morse = encodeWord(word);
        int unitDurationMs = 1200 / wpm;
        return codeConvert2Sound(morse, frequency, unitDurationMs, sampleRate, volume);
    }

    /**
     * 词组 -> PCM 音频
     */
    public byte[] morsePhrase2Sound(List<String> words, int frequency, int wpm, int sampleRate, double volume) {
        String morse = encodePhrase(words);
        int unitDurationMs = 1200 / wpm;
        return codeConvert2Sound(morse, frequency, unitDurationMs, sampleRate, volume);
    }


    /**
     * 将单个单词编码成摩斯码（带 @ 间隔）
     */
    public String encodeWord(String word) {
        StringBuilder result = new StringBuilder();
        for (int j = 0; j < word.length(); j++) {
            String letter = String.valueOf(word.charAt(j)).toUpperCase();
            String morse = MorseCoder.codeMap.get(letter);
            if (morse == null) {
                throw new IllegalArgumentException(letter + " 无法编码为摩尔斯电码");
            }
            result.append(morse);
            if (j < word.length() - 1) {
                result.append('@'); // 字母间隔
            }
        }
        return result.toString();
    }

    /**
     * 将词组编码成摩斯码（带 @ 和 / 间隔）
     */
    public String encodePhrase(List<String> words) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            result.append(encodeWord(words.get(i)));
            if (i < words.size() - 1) {
                result.append('/'); // 单词间隔
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
    public byte[] codeConvert2Sound(String codeString, int frequency, int unitMs, int sampleRate, double volume) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int dot = unitMs * dotRatio;   // 1 单位
        int dash = unitMs * rowRatio;  // 3 单位
        int letterSpace = unitMs * 3;  // 字母间隔
        int wordSpace = unitMs * blankRatio; // 单词间隔

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

            // 生成音/静音
            int sampleCount = (int) ((durationMs / 1000.0) * sampleRate);
            for (int k = 0; k < sampleCount; k++) {
                short sample;
                if (isTone) {
                    double angle = 2.0 * Math.PI * frequency * k / sampleRate;
                    sample = (short) (Math.sin(angle) * Short.MAX_VALUE * volume);
                } else {
                    sample = 0;
                }
                out.write(sample & 0xff);
                out.write((sample >> 8) & 0xff);
            }

            // 点和划后面补一个 **1 单位静音**
            if (isTone) {
                int gapSamples = (int) ((unitMs / 1000.0) * sampleRate);
                for (int k = 0; k < gapSamples; k++) {
                    out.write(0);
                    out.write(0);
                }
            }
        }
        return out.toByteArray();
    }
}

