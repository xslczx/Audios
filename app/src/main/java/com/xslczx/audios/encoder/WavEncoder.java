package com.xslczx.audios.encoder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class WavEncoder implements Encoder {

    private final String path;
    private FileOutputStream fos;
    private boolean isPrepared = false;
    private boolean isClosed = false;
    private int sampleRate;
    private int channelCount;
    private int bitDepth;
    private long dataSize = 0;

    public WavEncoder(String path) {
        this.path = path;
    }

    @Override
    public void setProgressListener(ProgressListener listener, long totalBytes) {

    }

    @Override
    public void prepare(int sampleRate, int channelCount, int bitDepth, int bitRate) throws Exception {
        if (isPrepared) return;

        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        this.bitDepth = bitDepth;

        fos = new FileOutputStream(path);
        writeWavHeaderPlaceholder();

        isPrepared = true;
        isClosed = false;
        dataSize = 0;
    }

    @Override
    public void write(byte[] pcm) throws IOException {
        if (!isPrepared || isClosed || pcm == null || pcm.length == 0) return;
        fos.write(pcm);
        dataSize += pcm.length;
    }

    @Override
    public void flush() throws IOException {
        if (!isPrepared || isClosed) return;
        fos.flush();
        updateWavHeader();
    }

    @Override
    public void closeQuietly() {
        if (isClosed) return;
        try {
            if (fos != null) {
                try {
                    fos.flush();
                } catch (IOException ignored) {}
                try {
                    updateWavHeader();
                } catch (IOException ignored) {}
                try {
                    fos.close();
                } catch (IOException ignored) {}
            }
        } finally {
            isClosed = true;
        }
    }

    private void writeWavHeaderPlaceholder() throws IOException {
        byte[] header = new byte[44]; // WAV 头 44 字节占位
        fos.write(header);
    }

    private void updateWavHeader() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path, "rw")) {
            raf.seek(0);
            // ChunkID "RIFF"
            raf.writeBytes("RIFF");
            // ChunkSize: 36 + dataSize
            raf.write(intToLittleEndian((int) (36 + dataSize)), 0, 4);
            // Format "WAVE"
            raf.writeBytes("WAVE");
            // Subchunk1ID "fmt "
            raf.writeBytes("fmt ");
            // Subchunk1Size 16
            raf.write(intToLittleEndian(16), 0, 4);
            // AudioFormat PCM = 1
            raf.write(shortToLittleEndian((short) 1), 0, 2);
            // NumChannels
            raf.write(shortToLittleEndian((short) channelCount), 0, 2);
            // SampleRate
            raf.write(intToLittleEndian(sampleRate), 0, 4);
            // ByteRate = SampleRate * NumChannels * BitsPerSample/8
            int byteRate = sampleRate * channelCount * bitDepth / 8;
            raf.write(intToLittleEndian(byteRate), 0, 4);
            // BlockAlign = NumChannels * BitsPerSample/8
            short blockAlign = (short) (channelCount * bitDepth / 8);
            raf.write(shortToLittleEndian(blockAlign), 0, 2);
            // BitsPerSample
            raf.write(shortToLittleEndian((short) bitDepth), 0, 2);
            // Subchunk2ID "data"
            raf.writeBytes("data");
            // Subchunk2Size = dataSize
            raf.write(intToLittleEndian((int) dataSize), 0, 4);
        }
    }

    private byte[] intToLittleEndian(int value) {
        return new byte[]{
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 24) & 0xff)
        };
    }

    private byte[] shortToLittleEndian(short value) {
        return new byte[]{
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff)
        };
    }
}

