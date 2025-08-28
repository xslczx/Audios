package com.xslczx.audios.processor;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.xslczx.audios.datas.AudioException;
import com.xslczx.audios.datas.Config;
import com.xslczx.audios.decoder.AudioDecodeTaskManager;
import com.xslczx.audios.decoder.AudioDecoderConcatenator;
import com.xslczx.audios.encoder.Encoder;
import com.xslczx.audios.encoder.EncoderFactory;
import com.xslczx.audios.encoder.FlacEncoder;
import com.xslczx.audios.encoder.MediaCodecEncoder;
import com.xslczx.audios.morse.MorseAudio;
import com.xslczx.audios.tag.Tagger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
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
        AudioDecodeTaskManager.cancelAllTasks();
        AudioDecodeTaskManager.startDecodeTask(Collections.singletonList(config.inputPath), new AudioDecoderConcatenator.DecodeCallback() {
            @Override
            public void onProgress(float progress, long currentTimeUs) {
                callbackOnProgress(0, calculateFraction(), progress);
            }

            @Override
            public void onSuccess(byte[] pcmData, int sampleRate, int channels) {
                try {
                    int bitRate = config.bitRate > 0 ? config.bitRate : 128_000;
                    config.effectProcessor.prepare(
                            sampleRate,
                            channels,
                            PCM_ENCODING_BIT_DEPTH,
                            bitRate
                    );
                    config.effectProcessor.write(pcmData);

                    byte[] finalPcm = config.effectProcessor.flush();

                    encoder.setProgressListener((writtenBytes, totalBytes) -> {
                        if (isStopped.get()) return;
                        callbackOnProgress(calculateFraction(), 1f, writtenBytes * 1f / totalBytes);
                    }, finalPcm.length);
                    encoder.prepare(sampleRate,channels,16,bitRate);
                    encoder.write(finalPcm);
                    encoder.flush();

                    callbackOnProgress(1f, 1f, 1f);

                    callbackOnInfo(Log.DEBUG, "更新音频信息==>" + config.extraInfo);
                    Tagger.updateCustomInfo(config.outputPath, config.extraInfo);
                    callbackOnInfo(Log.DEBUG, "音频处理完成==>" + config.outputPath);
                    callbackOnComplete();
                } catch (Exception e) {
                    callbackOnError(new AudioException("音频处理失败", e));
                } finally {
                    encoder.closeQuietly();
                }
            }

            @Override
            public void onError(Exception exception) {
                callbackOnError(new AudioException("音频处理失败", exception));
            }
        });
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

    private float calculateFraction() {
        if (encoder instanceof MediaCodecEncoder) return 0.5f;
        if (encoder instanceof FlacEncoder) return 0.5f;
        return 1f;
    }

    private void generateAI() {
        MorseAudio morseAudio = new MorseAudio();
        byte[] sound = morseAudio.morseWord2Sound("AI", 800, 13, 16000, 1.0, 2);
        try {
            encoder.prepare(16000, 2, 16, 32_000);
            encoder.write(sound);
            encoder.flush();
            callbackOnComplete();
        } catch (Exception e) {
            callbackOnError(new AudioException("音频处理失败", e));
        } finally {
            encoder.closeQuietly();
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

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

        void onInfo(int level, @NotNull String extra);

        void onProgress(float progress);

        void onComplete(@NotNull String outputPath);

        void onError(@NotNull AudioException e);
    }
}
