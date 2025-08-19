package com.xslczx.audios.encoder;


import com.encoder.lame.LameNative;

import java.io.FileOutputStream;
import java.io.IOException;

public class Mp3Encoder implements Encoder {

    private final String outputFile;
    private FileOutputStream fos;
    private boolean isPrepared = false;
    private int channelCount;

    // LAME 常用比特率
    private static final int[] COMMON_BIT_RATES = {32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320};

    public Mp3Encoder(String outputFile) {
        this.outputFile = outputFile;
    }

    @Override
    public void prepare(int sampleRate, int channelCount, int bitDepth, int bitRate) throws Exception {
        if (isPrepared) return;

        this.channelCount = channelCount;
        fos = new FileOutputStream(outputFile);

        // 转换 bitRate（bps -> kbps）并选最近的常用值
        int kbps = bitRate / 1000;
        int nearestBitRate = nearestValue(COMMON_BIT_RATES, kbps);

        int mode = (channelCount == 1) ? 3 : 1; // 3=MONO, 1=STEREO
        int quality = 7; // 0=best, 9=fastest

        int result = LameNative.initEncoder(channelCount, sampleRate, nearestBitRate, mode, quality);
        if (result != 0) {
            throw new RuntimeException("LAME initEncoder failed with code " + result);
        }

        isPrepared = true;
    }

    @Override
    public void write(byte[] pcm) throws IOException {
        if (!isPrepared) throw new IllegalStateException("Mp3Encoder is not prepared");
        if (pcm.length % 2 != 0) throw new IllegalArgumentException("PCM byte length must be even");

        int shortCount = pcm.length / 2;
        short[] pcmShorts = new short[shortCount];

        // 小端序转换
        for (int i = 0; i < shortCount; i++) {
            int low = pcm[i * 2] & 0xFF;
            int high = pcm[i * 2 + 1] & 0xFF;
            pcmShorts[i] = (short) ((high << 8) | low);
        }

        int numSamplesPerChannel = shortCount / channelCount;
        int mp3BufferSize = (int) (1.25 * numSamplesPerChannel) + 7200;
        byte[] mp3Buffer = new byte[mp3BufferSize];

        int bytesEncoded = LameNative.encode(pcmShorts, numSamplesPerChannel, mp3Buffer);
        if (bytesEncoded > 0) {
            fos.write(mp3Buffer, 0, bytesEncoded);
        }
    }

    @Override
    public void flush() throws IOException {
        if (!isPrepared) return;
        byte[] mp3Buffer = new byte[7200];
        int bytesFlushed = LameNative.flush(mp3Buffer);
        if (bytesFlushed > 0) fos.write(mp3Buffer, 0, bytesFlushed);
        fos.flush();
    }

    @Override
    public void closeQuietly() {
        try { flush(); } catch (IOException ignored) {}
        try { LameNative.close(); } catch (Throwable ignored) {}
        try { if (fos != null) fos.close(); } catch (IOException ignored) {}
        isPrepared = false;
    }
}


