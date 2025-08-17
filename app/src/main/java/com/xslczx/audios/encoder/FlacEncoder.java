package com.xslczx.audios.encoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class FlacEncoder implements Encoder {

    private final String path;
    private FileOutputStream fos;
    private MediaCodec codec;
    private MediaCodec.BufferInfo bufferInfo;
    private boolean isPrepared = false;
    private boolean isClosed = false;
    private final int[] sampleRates = new int[]{
            8000, 12000, 16000, 24000, 48000
    };
    private int channelCount;
    private long totalPcmBytes;
    private long writtenPcmBytes;

    private ProgressListener progressCallback;

    public FlacEncoder(String path) {
        this.path = path;
    }

    public void setProgressListener(ProgressListener callback,long totalPcmBytes) {
        this.progressCallback = callback;
        this.totalPcmBytes = totalPcmBytes;
    }

    public void prepare(int sampleRate, int channelCount, int bitDepth, int bitRate) throws Exception {
        if (isPrepared) return;

        int sampleRateReal = nearestValue(sampleRates, sampleRate);
        this.channelCount = channelCount;
        this.writtenPcmBytes = 0;

        fos = new FileOutputStream(path);
        bufferInfo = new MediaCodec.BufferInfo();

        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC, sampleRateReal, channelCount);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();

        isPrepared = true;
    }

    public void write(byte[] pcm) throws IOException {
        if (!isPrepared || isClosed || pcm == null || pcm.length == 0) return;

        int offset = 0;
        final int frameSize = 1024 * channelCount * 2; // PCM16 per frame

        while (offset < pcm.length) {
            int inputIndex = codec.dequeueInputBuffer(10000);
            if (inputIndex >= 0) {
                ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
                inputBuffer.clear();

                int toWrite = Math.min(frameSize, pcm.length - offset);
                inputBuffer.put(pcm, offset, toWrite);

                long pts = System.nanoTime() / 1000;
                codec.queueInputBuffer(inputIndex, 0, toWrite, pts, 0);

                offset += toWrite;
                writtenPcmBytes += toWrite;
                if (progressCallback != null) progressCallback.onProgress(writtenPcmBytes, totalPcmBytes);
            }

            drainOutput();
        }

        totalPcmBytes += pcm.length;
    }

    public void flush() throws IOException {
        if (!isPrepared || isClosed) return;

        int inputIndex = codec.dequeueInputBuffer(10000);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
            inputBuffer.clear();
            codec.queueInputBuffer(inputIndex, 0, 0, System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }

        boolean eos = false;
        while (!eos) {
            eos = drainOutput();
        }
    }

    public void closeQuietly() {
        if (isClosed) return;
        try {
            try {
                if (codec != null) {
                    codec.stop();
                    codec.release();
                }
            } catch (Exception ignored) {}
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException ignored) {}
        } finally {
            isClosed = true;
        }
    }

    private boolean drainOutput() throws IOException {
        int outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000);

        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            return false;
        } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            // MediaCodec FLAC encoder outputs codec-specific data here, write to file if needed
            return true;
        } else if (outputIndex >= 0) {
            ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
            if (bufferInfo.size > 0) {
                byte[] out = new byte[bufferInfo.size];
                outputBuffer.get(out);
                fos.write(out);
            }
            codec.releaseOutputBuffer(outputIndex, false);
            return (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        }
        return false;
    }
}