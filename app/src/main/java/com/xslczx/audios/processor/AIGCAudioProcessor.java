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

import java.util.concurrent.atomic.AtomicBoolean;

public class AIGCAudioProcessor {
    private static final int PCM_ENCODING_BIT_DEPTH = 16;
    private static final int DEFAULT_OUTPUT_BIT_RATE = 128_000;
    private static final int GENERATED_SAMPLE_RATE = 16000;
    private static final int GENERATED_CHANNEL_COUNT = 2;
    private static final int GENERATED_BIT_RATE = 32_000;
    private static final int GENERATED_WPM = 13;
    private static final double GENERATED_VOLUME = 1.0;
    private static final String AI_MARKER = "AI";

    private final Config config;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private final OnProcessListener onProcessListener;
    private Encoder encoder;

    public AIGCAudioProcessor(@NotNull Config config, @Nullable OnProcessListener onProcessListener) {
        this.config = config;
        this.onProcessListener = onProcessListener;
        try {
            encoder = EncoderFactory.createEncoder(config);
        } catch (Exception e) {
            callbackOnError(new AudioException("初始化失败,无法创建编码器", e));
            stop();
        }
    }

    /**
     * 异步解码 + PCM 处理
     */
    @NotNull
    public AIGCAudioProcessor startAsync() {
        if (stopped.get()) return this;
        if (config.audioPaths.isEmpty()) {
            callbackOnError(new AudioException("音频处理失败", new IllegalArgumentException("audioPaths must not be empty")));
            stop();
            return this;
        }

        AudioDecodeTaskManager.cancelAllTasks();
        AudioDecodeTaskManager.startDecodeTask(config.audioPaths, new AudioDecoderConcatenator.DecodeCallback() {
            @Override
            public void onProgress(float progress, long currentTimeUs) {
                callbackOnProgress(0f, calculateProgressSplit(), progress);
            }

            @Override
            public void onSuccess(byte[] pcmData, int sampleRate, int channels) {
                try {
                    processDecodedAudio(pcmData, sampleRate, channels);
                } catch (Exception e) {
                    callbackOnError(new AudioException("音频处理失败", e));
                } finally {
                    closeEncoderQuietly();
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
        if (stopped.get()) return this;
        new Thread(this::generateAI, "AIGeneratorThread").start();
        return this;
    }

    /**
     * 停止解码
     */
    public void stop() {
        stopped.set(true);
    }

    private float calculateProgressSplit() {
        return requiresMultiPhaseProgress() ? 0.5f : 1f;
    }

    private void processDecodedAudio(byte[] pcmData, int sampleRate, int channels) throws Exception {
        Encoder activeEncoder = requireEncoder();
        int outputBitRate = resolveOutputBitRate();

        config.effectProcessor.prepare(sampleRate, channels, PCM_ENCODING_BIT_DEPTH, outputBitRate);
        config.effectProcessor.write(pcmData);
        byte[] processedPcm = config.effectProcessor.flush();

        activeEncoder.setProgressListener((writtenBytes, totalBytes) -> {
            if (stopped.get()) return;
            callbackOnProgress(calculateProgressSplit(), 1f, toSafeProgress(writtenBytes, totalBytes));
        }, processedPcm.length);
        activeEncoder.prepare(sampleRate, channels, PCM_ENCODING_BIT_DEPTH, outputBitRate);
        activeEncoder.write(processedPcm);
        activeEncoder.flush();

        callbackOnProgress(1f, 1f, 1f);
        updateAudioMetadata();
        callbackOnInfo(Log.DEBUG, "音频处理完成==>" + config.outputPath);
        callbackOnComplete();
    }

    private void updateAudioMetadata() throws Exception {
        callbackOnInfo(Log.DEBUG, "更新音频信息==>" + config.extraInfo);
        Tagger.updateCustomInfo(config.outputPath, config.extraInfo);
    }

    private void generateAI() {
        try {
            Encoder activeEncoder = requireEncoder();
            byte[] generatedSound = new MorseAudio().morseWord2Sound(
                    AI_MARKER,
                    Config.FREQUENCY_NORMAL,
                    GENERATED_WPM,
                    GENERATED_SAMPLE_RATE,
                    GENERATED_VOLUME,
                    GENERATED_CHANNEL_COUNT
            );
            activeEncoder.prepare(
                    GENERATED_SAMPLE_RATE,
                    GENERATED_CHANNEL_COUNT,
                    PCM_ENCODING_BIT_DEPTH,
                    GENERATED_BIT_RATE
            );
            activeEncoder.write(generatedSound);
            activeEncoder.flush();
            callbackOnComplete();
        } catch (Exception e) {
            callbackOnError(new AudioException("音频处理失败", e));
        } finally {
            closeEncoderQuietly();
        }
    }

    private Encoder requireEncoder() throws AudioException {
        if (encoder == null) {
            throw new AudioException("初始化失败,无法创建编码器");
        }
        return encoder;
    }

    private void closeEncoderQuietly() {
        if (encoder != null) {
            encoder.closeQuietly();
        }
    }

    private int resolveOutputBitRate() {
        return config.bitRate > 0 ? config.bitRate : DEFAULT_OUTPUT_BIT_RATE;
    }

    private boolean requiresMultiPhaseProgress() {
        return encoder instanceof MediaCodecEncoder || encoder instanceof FlacEncoder;
    }

    private float toSafeProgress(long writtenBytes, long totalBytes) {
        if (totalBytes <= 0) {
            return 0f;
        }
        return writtenBytes * 1f / totalBytes;
    }

    private void callbackOnInfo(int level, String extra) {
        if (stopped.get()) return;
        if (onProcessListener != null) {
            onProcessListener.onInfo(level, extra);
        }
    }

    private void callbackOnProgress(float start, float end, float progress) {
        float normalizedProgress = start + progress * (end - start);
        callbackOnInfo(Log.VERBOSE, "进度==>" + normalizedProgress);
        mainThreadHandler.post(() -> {
            if (stopped.get()) return;
            if (onProcessListener != null) {
                onProcessListener.onProgress(normalizedProgress);
            }
        });
    }

    private void callbackOnComplete() {
        mainThreadHandler.post(() -> {
            if (stopped.get()) return;
            if (onProcessListener != null) {
                onProcessListener.onComplete(config.outputPath);
            }
        });
    }

    private void callbackOnError(AudioException e) {
        mainThreadHandler.post(() -> {
            if (stopped.get()) return;
            if (onProcessListener != null) {
                onProcessListener.onError(e);
            }
        });
    }

    public interface OnProcessListener {

        void onInfo(int level, @NotNull String extra);

        void onProgress(float progress);

        void onComplete(@NotNull String outputPath);

        void onError(@NotNull AudioException e);
    }
}
