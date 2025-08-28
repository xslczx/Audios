package com.xslczx.audios.encoder;

import java.io.IOException;

/**
 * 音频编码器
 */
public interface Encoder {

    void setProgressListener(ProgressListener listener, long totalBytes);

    void prepare(int sampleRate, int channelCount, int bitDepth, int bitRate) throws Exception;

    void write(byte[] pcm) throws IOException;

    void flush() throws IOException;

    void closeQuietly();

    default int nearestValue(int[] values, int value) {
        int distance = Math.abs(values[0] - value);
        int idx = 0;

        for (int c = 1; c < values.length; c++) {
            int cDistance = Math.abs(values[c] - value);
            if (cDistance < distance) {
                idx = c;
                distance = cDistance;
            }
        }

        if (value != values[idx]) {
            StringBuilder availableValues = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                availableValues.append(values[i]);
                if (i != values.length - 1) {
                    availableValues.append(", ");
                }
            }
        }

        return values[idx];
    }
}
