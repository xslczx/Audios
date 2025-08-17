package com.xslczx.audios.processor;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.xslczx.audios.datas.AudioDecodeInfo;
import com.xslczx.audios.datas.AudioException;
import com.xslczx.audios.datas.Config;
import com.xslczx.audios.encoder.Encoder;
import com.xslczx.audios.encoder.EncoderFactory;
import com.xslczx.audios.encoder.FlacEncoder;
import com.xslczx.audios.encoder.MediaCodecEncoder;
import com.xslczx.audios.morse.MorseAudio;
import com.xslczx.audios.tag.Tagger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class AIGCAudioProcessor {

    private final Config config;
    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    private Encoder encoder;
    private static final int PCM_ENCODING_BIT_DEPTH = 16;
    private static final long PCM_DEQUEUE_TIMEOUT_US = 10000;
    private static final long MIN_PROGRESS_INTERVAL_MS = 100;
    private float lastProgress = 0f;
    private long lastProgressTime = 0;
    private final OnProcessListener onProcessListener;

    public AIGCAudioProcessor(@NotNull Config config, @Nullable OnProcessListener onProcessListener) {
        this.config = config;
        this.onProcessListener = onProcessListener;
        try {
            encoder = EncoderFactory.createEncoder(config);
        } catch (Exception e) {
            AudioException audioException = new AudioException("初始化失败,无法创建编码器", e);
            callbackOnError(audioException);
            stop();
        }
    }

    /**
     * 异步解码 + PCM 处理
     */
    @NotNull
    public AIGCAudioProcessor startAsync() {
        if (isStopped.get()) return this;
        new Thread(this::decodeInternal, "AudioDecoderThread").start();
        return this;
    }

    @NotNull
    public AIGCAudioProcessor generateAIAsync() {
        if (isStopped.get()) return this;
        new Thread(this::generateAI, "AIGeneratorThread").start();
        return this;
    }

    /**
     * 停止解码
     */
    public void stop() {
        isStopped.set(true);
    }

    /**
     * 核心逻辑
     */
    private void decodeInternal() {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;

        try {
            callbackOnInfo(Log.DEBUG, "开始初始化");
            extractor.setDataSource(config.inputPath);
            callbackOnInfo(Log.DEBUG, "开始解码==>" + config.inputPath);
            int trackIndex = selectAudioTrack(extractor);
            if (trackIndex < 0) throw new AudioException("找不到音轨");
            callbackOnInfo(Log.DEBUG, "找到音轨==>" + trackIndex);
            extractor.selectTrack(trackIndex);

            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            try {
                codec = MediaCodec.createDecoderByType(Objects.requireNonNull(mime));
                codec.configure(format, null, null, 0);
                codec.start();
                callbackOnInfo(Log.DEBUG, "解码器已创建==>" + mime);
            } catch (Exception e) {
                throw new AudioException("解码器创建失败", e);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                    ? format.getLong(MediaFormat.KEY_DURATION) : -1;
            AudioDecodeInfo decodeInfo = new AudioDecodeInfo(
                    sampleRate,
                    channels,
                    PCM_ENCODING_BIT_DEPTH,
                    durationUs
            );
            callbackOnStart(decodeInfo);

            int bitRate = config.bitRate > 0 ? config.bitRate : 128_000;
            try {
                encoder.prepare(
                        sampleRate,
                        channels,
                        PCM_ENCODING_BIT_DEPTH,
                        bitRate
                );
            } catch (Exception e) {
                throw new AudioException("编码器准备失败", e);
            }
            if (config.effectProcessor != null) {
                config.effectProcessor.prepare(
                        sampleRate,
                        channels,
                        PCM_ENCODING_BIT_DEPTH,
                        bitRate
                );
            }

            callbackOnInfo(Log.DEBUG, "开始缓冲");
            boolean isEOS = false;

            while (!isStopped.get()) {

                int inIndex = codec.dequeueInputBuffer(PCM_DEQUEUE_TIMEOUT_US);
                if (inIndex >= 0 && !isEOS) {
                    ByteBuffer inputBuffer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? codec.getInputBuffer(inIndex)
                            : codec.getInputBuffers()[inIndex];
                    if (inputBuffer == null) throw new AudioException("无法获取输入缓冲");
                    inputBuffer.clear();
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    long pts = extractor.getSampleTime();

                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, pts, 0);
                        extractor.advance();
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(bufferInfo, PCM_DEQUEUE_TIMEOUT_US);
                if (outIndex >= 0) {
                    ByteBuffer outputBuffer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? codec.getOutputBuffer(outIndex)
                            : codec.getOutputBuffers()[outIndex];

                    byte[] pcm = new byte[bufferInfo.size];
                    if (outputBuffer != null) {
                        outputBuffer.get(pcm);
                        outputBuffer.clear();
                    }

                    if (config.effectProcessor != null) {
                        try {
                            config.effectProcessor.write(pcm);
                        } catch (IOException e) {
                            throw (new AudioException("数据处理失败", e));
                        }
                    }

                    if (durationUs > 0) {
                        float progress = bufferInfo.presentationTimeUs * 1f / durationUs;
                        progress = Math.min(progress, 1f);

                        long now = System.currentTimeMillis();
                        if ((progress - lastProgress >= 0.005f || progress == 1f)
                                && (now - lastProgressTime >= MIN_PROGRESS_INTERVAL_MS)) {
                            lastProgress = progress;
                            lastProgressTime = now;
                            callbackOnProgress(0f, calculateFraction(), progress);
                        }
                    }

                    codec.releaseOutputBuffer(outIndex, false);

                    if (durationUs > 0) {
                        float progress = bufferInfo.presentationTimeUs * 1f / durationUs;
                        callbackOnProgress(0f, calculateFraction(), Math.min(progress, 1f));
                    }

                    // EOS
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        callbackOnProgress(0f, calculateFraction(), 1f);
                        callbackOnInfo(Log.DEBUG, "EOS");
                        break;
                    }
                }
            }

            if (config.effectProcessor != null) {
                try {
                    callbackOnInfo(Log.DEBUG, "effectProcessor flush");
                    byte[] finalPcm = config.effectProcessor.flush();
                    callbackOnInfo(Log.DEBUG, "encoder write");
                    if (encoder instanceof MediaCodecEncoder) {
                        ((MediaCodecEncoder) encoder).setProgressListener((writtenBytes, totalBytes) -> {
                            if (isStopped.get()) return;
                            callbackOnProgress(calculateFraction(), 1f, writtenBytes * 1f / totalBytes);
                        }, finalPcm.length);
                    } else if (encoder instanceof FlacEncoder) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            ((FlacEncoder) encoder).setProgressListener((writtenBytes, totalBytes) -> {
                                if (isStopped.get()) return;
                                callbackOnProgress(calculateFraction(), 1f, writtenBytes * 1f / totalBytes);
                            }, finalPcm.length);
                        }
                    }
                    encoder.write(finalPcm);
                    callbackOnInfo(Log.DEBUG, "encoder flush");
                    encoder.flush();
                } catch (Exception e) {
                    throw new AudioException("结束数据处理失败", e);
                }
            }

            callbackOnInfo(Log.DEBUG, "更新音频信息==>" + config.extraInfo);
            Tagger.updateCustomInfo(config.outputPath, config.extraInfo);
            callbackOnInfo(Log.DEBUG, "音频处理完成==>" + config.outputPath);
            callbackOnComplete();
        } catch (AudioException e) {
            callbackOnError(e);
        } catch (Exception e) {
            callbackOnError(new AudioException("音频处理失败", e));
        } finally {
            try {
                if (codec != null) {
                    try {
                        codec.stop();
                    } catch (Exception ignore) {
                    }
                    try {
                        codec.release();
                    } catch (Exception ignore) {
                    }
                }
                extractor.release();
            } catch (Exception ignore) {
            }
            encoder.closeQuietly();
        }
    }

    private float calculateFraction() {
        if (encoder instanceof MediaCodecEncoder) return 0.5f;
        if (encoder instanceof FlacEncoder) return 0.5f;
        return 1f;
    }

    private void generateAI() {
        MorseAudio morseAudio = new MorseAudio();
        byte[] sound = morseAudio.morseWord2Sound("AI", 800, 13, 16000, 1.0);
        try {
            encoder.prepare(16000, 1, 16, 32_000);
            encoder.write(sound);
            encoder.flush();
            callbackOnComplete();
        } catch (Exception e) {
            callbackOnError(new AudioException("音频处理失败", e));
        } finally {
            encoder.closeQuietly();
        }
    }

    private int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) return i;
        }
        return -1;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    private void callbackOnStart(AudioDecodeInfo info) {
        handler.post(() -> {
            if (isStopped.get()) return;
            if (onProcessListener != null) onProcessListener.onStart(info);
        });
    }

    private void callbackOnInfo(int level, String extra) {
        if (isStopped.get()) return;
        if (onProcessListener != null) {
            onProcessListener.onInfo(level, extra);
        }
    }

    private void callbackOnProgress(float start, float end, float progress) {
        float v = start + progress * (end - start);
        callbackOnInfo(Log.VERBOSE, "进度==>" + v);
        handler.post(() -> {
            if (isStopped.get()) return;
            if (onProcessListener != null) onProcessListener.onProgress(v);
        });
    }

    private void callbackOnComplete() {
        handler.post(() -> {
            if (isStopped.get()) return;
            if (onProcessListener != null) onProcessListener.onComplete(config.outputPath);
        });
    }

    private void callbackOnError(AudioException e) {
        handler.post(() -> {
            if (isStopped.get()) return;
            if (onProcessListener != null) onProcessListener.onError(e);
        });
    }

    public interface OnProcessListener {
        void onStart(@NotNull AudioDecodeInfo info);

        void onInfo(int level, @NotNull String extra);

        void onProgress(float progress);

        void onComplete(@NotNull String outputPath);

        void onError(@NotNull AudioException e);
    }
}
