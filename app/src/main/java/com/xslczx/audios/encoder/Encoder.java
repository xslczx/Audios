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
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }

        int nearestDistance = Math.abs(values[0] - value);
        int nearestIndex = 0;

        for (int index = 1; index < values.length; index++) {
            int candidateDistance = Math.abs(values[index] - value);
            if (candidateDistance < nearestDistance) {
                nearestIndex = index;
                nearestDistance = candidateDistance;
            }
        }

        return values[nearestIndex];
    }
}
