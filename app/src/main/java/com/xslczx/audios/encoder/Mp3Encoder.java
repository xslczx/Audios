package com.xslczx.audios.encoder;

import android.util.Log;

import com.encoder.lame.LameNative;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class Mp3Encoder implements Encoder {

    private static final String TAG = ">>>:Mp3Encoder";

    private OutputStream out;
    private int channelCount;
    private final String outputPath;
    private boolean prepared = false;
    private boolean closed = false;
    private long totalPcmBytes;

    private ProgressListener progressListener;

    public Mp3Encoder(String outputPath) {
        this.outputPath = outputPath;
    }

    @Override
    public void setProgressListener(ProgressListener callback,long totalPcmBytes) {
        this.progressListener = callback;
        this.totalPcmBytes = totalPcmBytes;
    }

    @Override
    public void prepare(int sampleRate, int channelCount, int bitDepth, int bitRate) throws Exception {
        if (prepared) return;
        this.channelCount = channelCount;
        LameNative.init(sampleRate, channelCount, 441000, bitRate/1000, 7);
        prepared = true;
        closed = false;
        this.out = new FileOutputStream(outputPath);
        Log.d(TAG, "prepare() done");
    }

    @Override
    public void write(byte[] pcm) throws IOException {
        ensurePrepared();
        ensureNotClosed();
        if (pcm == null || pcm.length == 0) throw new IOException("pcm is null or empty.");

        int frameSamples = 1152 * channelCount; // LAME 一帧 = 1152 * 声道
        int bytesPerFrame = frameSamples * 2;   // short → byte

        int offset = 0;
        while (offset < pcm.length) {
            int len = Math.min(bytesPerFrame, pcm.length - offset);
            encodeChunk(pcm, offset, len);
            if (progressListener!=null) {
                progressListener.onProgress(offset, totalPcmBytes);
            }
            offset += len;
        }
    }

    /**
     * 把一个 PCM chunk 编码成 MP3
     */
    private void encodeChunk(byte[] pcm, int offset, int len) throws IOException {
        // byte[] → short[]
        ShortBuffer sb = ByteBuffer.wrap(pcm, offset, len)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer();
        short[] pcmShorts = new short[sb.remaining()];
        sb.get(pcmShorts);

        int samples = pcmShorts.length; // short 数量
        int samplesPerChannel = (channelCount == 1) ? samples : samples / 2;

        // mp3 缓冲区大小安全计算
        int mp3bufSize = (int) (1.25 * samplesPerChannel + 7200);
        byte[] mp3buf = new byte[mp3bufSize];

        int encoded;
        if (channelCount == 1) {
            encoded = LameNative.encodeMono(pcmShorts, samplesPerChannel, mp3buf);
        } else {
            encoded = LameNative.encodeInterleaved(pcmShorts, samplesPerChannel, mp3buf);
        }

        if (encoded < 0) {
            throw new IOException("encode() failed with code: " + encoded);
        }
        if (encoded > 0) {
            out.write(mp3buf, 0, encoded);
        }
    }

    @Override
    public void flush() throws IOException {
        ensurePrepared();
        ensureNotClosed();

        byte[] mp3buf = new byte[7200]; // lame flush 需要 7200
        int encoded = LameNative.flush(mp3buf);
        if (encoded > 0) {
            out.write(mp3buf, 0, encoded);
        }
        out.flush();
    }

    @Override
    public void closeQuietly() {
        if (closed) return;
        try {
            out.close();
        } catch (IOException ignore) {
        }
        LameNative.close();
        closed = true;
        prepared = false;
    }

    private void ensurePrepared() throws IOException {
        if (!prepared) throw new IOException("Encoder not prepared");
    }

    private void ensureNotClosed() throws IOException {
        if (closed) throw new IOException("Encoder already closed");
    }
}
