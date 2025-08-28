package com.xslczx.audios.encoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class MediaCodecEncoder implements Encoder {
    private static final long WAIT_TIMEOUT_US = 10000;

    protected final String path;
    protected final String mimeType;
    protected final int muxerOutputFormat;

    protected MediaCodec codec;
    protected MediaMuxer muxer;
    protected MediaCodec.BufferInfo bufferInfo;
    protected int trackIndex = -1;
    protected boolean muxerStarted = false;
    protected boolean isPrepared = false;
    protected boolean isClosed = false;

    protected int sampleRate;
    protected int channelCount;
    protected int bitRate;
    protected Integer profile;

    protected ProgressListener progressListener;
    protected long totalWritten = 0;
    protected long totalBytes = -1;

    public MediaCodecEncoder(String path, String mimeType, int muxerOutputFormat) {
        this.path = path;
        this.mimeType = mimeType;
        this.muxerOutputFormat = muxerOutputFormat;
    }

    public MediaCodecEncoder(String path, String mimeType, int muxerOutputFormat, int profile) {
        this.path = path;
        this.mimeType = mimeType;
        this.muxerOutputFormat = muxerOutputFormat;
        this.profile = profile;
    }

    @Override
    public void setProgressListener(ProgressListener listener, long totalBytes) {
        this.progressListener = listener;
        this.totalBytes = totalBytes;
    }

    @Override
    public void prepare(int sampleRate, int channelCount, int bitDepth, int bitRate) throws Exception {
        if (isPrepared) return;
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        this.bitRate = bitRate;

        bufferInfo = new MediaCodec.BufferInfo();

        MediaFormat format = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount);

        if (profile != null) {
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, profile);
        }
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        if (mimeType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
            format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 8);
        }

        codec = MediaCodec.createEncoderByType(mimeType);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();

        muxer = new MediaMuxer(path, muxerOutputFormat);

        isPrepared = true;
    }

    @Override
    public void write(byte[] pcm) throws IOException {
        if (!isPrepared || isClosed || pcm == null || pcm.length == 0) return;

        int offset = 0;
        final int frameSize = 1024 * channelCount * 2; // PCM 16bit 每帧大小

        while (offset < pcm.length) {
            int inputIndex = codec.dequeueInputBuffer(WAIT_TIMEOUT_US);
            if (inputIndex >= 0) {
                ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
                inputBuffer.clear();

                int toWrite = Math.min(frameSize, pcm.length - offset);
                inputBuffer.put(pcm, offset, toWrite);

                long pts = System.nanoTime() / 1000;
                codec.queueInputBuffer(inputIndex, 0, toWrite, pts, 0);

                offset += toWrite;
                totalWritten += toWrite;

                if (progressListener != null) {
                    progressListener.onProgress(totalWritten, totalBytes);
                }
            }

            drainOutputOnce(); // 每次写入尝试处理输出，避免死循环
        }
    }


    @Override
    public void flush() throws IOException {
        if (!isPrepared || isClosed) return;

        int inputIndex = codec.dequeueInputBuffer(WAIT_TIMEOUT_US);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
            inputBuffer.clear();
            codec.queueInputBuffer(inputIndex, 0, 0, System.nanoTime() / 1000,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }

        // 循环直到遇到 EOS 或没有可处理输出
        boolean eos = false;
        while (!eos) {
            eos = drainOutputOnce();
            if (!eos) break;
        }
    }

    @Override
    public void closeQuietly() {
        if (isClosed) return;
        try {
            try {
                if (codec != null) {
                    codec.stop();
                    codec.release();
                }
            } catch (Exception ignored) {
            }
            try {
                if (muxer != null && muxerStarted) {
                    muxer.stop();
                    muxer.release();
                }
            } catch (Exception ignored) {
            }
        } finally {
            isClosed = true;
        }
    }

    /**
     * 每次处理一次输出
     *
     * @return true: 遇到 EOS
     */
    private boolean drainOutputOnce() throws IOException {
        int outputIndex = codec.dequeueOutputBuffer(bufferInfo, WAIT_TIMEOUT_US);

        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            return false;
        } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (muxerStarted) throw new IllegalStateException("Format changed twice");
            MediaFormat newFormat = codec.getOutputFormat();
            trackIndex = muxer.addTrack(newFormat);
            muxer.start();
            muxerStarted = true;
            return true;
        } else if (outputIndex >= 0) {
            ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);

            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                bufferInfo.size = 0;
            }

            if (bufferInfo.size > 0 && muxerStarted) {
                outputBuffer.position(bufferInfo.offset);
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
            }

            codec.releaseOutputBuffer(outputIndex, false);

            return (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        }
        return false;
    }
}


