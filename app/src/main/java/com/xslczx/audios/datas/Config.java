package com.xslczx.audios.datas;

import com.xslczx.audios.processor.AITailPcmAppender;
import com.xslczx.audios.processor.PcmEffectProcessor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置参数
 * inputPath  输入文件路径
 * outputPath 输出文件路径 生成速度快：wav/mp3，体积小、兼容性强:mp3，音质无损:wav/flac
 * frequency 600 Hz → 标准电台摩斯常用音调,700~800 Hz → 人耳听起来比较清晰, 1000 Hz → 高频一些，但时间长了会刺耳
 * wpm       新手练习：5，业余无线电（Ham）考试：12-15，业余爱好者常用：18-20，电台 QSO 常见速度：25-30，竞赛或专业通信员的速度：35-40
 * volume    0.01 ~ 1.0
 * effectProcessor 可选，用于处理 PCM 数据，如添加尾声、添加特效、降噪处理等
 * extraInfo  可选，用于添加自定义信息，如作者、版权、描述等信息
 * bitRate   可选，单位 bps,默认128_000，输出MP3等格式需要
 */
public class Config {
    private static final int DEFAULT_BIT_RATE = 128_000;
    private static final double DEFAULT_VOLUME = 0.1;


    public static final int FREQUENCY_STANDARD = 600;
    public static final int FREQUENCY_NORMAL = 800;
    public static final int FREQUENCY_HIGH = 1000;
    public static final int WPM_SLOW = 10;
    public static final int WPM_NORMAL = 20;
    public static final int WPM_FAST = 30;
    public static final int WPM_VERY_FAST = 40;

    public static final String[] SUPPORT_FILE_TYPE = new String[]{"wav", "mp3", "flac"};

    public final String outputPath;
    public final PcmEffectProcessor effectProcessor;
    public Map<String, String> extraInfo;
    public int bitRate = DEFAULT_BIT_RATE;
    public int wpm = WPM_NORMAL;
    public double volume = DEFAULT_VOLUME;
    public int frequency = FREQUENCY_STANDARD;
    public final List<String> audioPaths = new ArrayList<>();

    public Config(@Nullable String inputPath,
                  @NotNull String outputPath,
                  @NotNull Map<String, String> extraInfo) {
        this.outputPath = requireOutputPath(outputPath);
        this.extraInfo = copyExtraInfo(extraInfo);
        if (inputPath != null && !inputPath.trim().isEmpty()) {
            this.audioPaths.add(inputPath);
        }
        this.effectProcessor = createDefaultEffectProcessor();
    }

    public Config(@NotNull List<String> audioPaths,
                  @NotNull String outputPath,
                  @NotNull Map<String, String> extraInfo) {
        this.outputPath = requireOutputPath(outputPath);
        this.extraInfo = copyExtraInfo(extraInfo);
        addAudioPaths(audioPaths);
        this.effectProcessor = createDefaultEffectProcessor();
    }

    public Config setWpm(int wpm) {
        if (wpm <= 0) {
            throw new IllegalArgumentException("wpm must be greater than 0");
        }
        this.wpm = wpm;
        return this;
    }

    public Config setVolume(double volume) {
        if (volume <= 0 || volume > 1.0) {
            throw new IllegalArgumentException("volume must be in range (0, 1]");
        }
        this.volume = volume;
        return this;
    }

    public Config setFrequency(int frequency) {
        if (frequency <= 0) {
            throw new IllegalArgumentException("frequency must be greater than 0");
        }
        this.frequency = frequency;
        return this;
    }

    public Config setBitRate(int bitRate) {
        if (bitRate <= 0) {
            throw new IllegalArgumentException("bitRate must be greater than 0");
        }
        this.bitRate = bitRate;
        return this;
    }

    private void addAudioPaths(@NotNull List<String> audioPaths) {
        for (String audioPath : audioPaths) {
            if (audioPath == null || audioPath.trim().isEmpty()) {
                throw new IllegalArgumentException("audio path must not be blank");
            }
            this.audioPaths.add(audioPath);
        }
    }

    @NotNull
    private static String requireOutputPath(@NotNull String outputPath) {
        String normalizedOutputPath = outputPath.trim();
        if (normalizedOutputPath.isEmpty()) {
            throw new IllegalArgumentException("outputPath must not be blank");
        }
        return normalizedOutputPath;
    }

    @NotNull
    private static Map<String, String> copyExtraInfo(@NotNull Map<String, String> extraInfo) {
        return new LinkedHashMap<>(extraInfo);
    }

    @NotNull
    private PcmEffectProcessor createDefaultEffectProcessor() {
        return new AITailPcmAppender(this);
    }
}
