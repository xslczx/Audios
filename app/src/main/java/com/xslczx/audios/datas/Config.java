package com.xslczx.audios.datas;

import com.xslczx.audios.processor.AITailPcmAppender;
import com.xslczx.audios.processor.PcmEffectProcessor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 配置参数
 * inputPath  输入文件路径
 * outputPath 输出文件路径 生成速度快：wav/mp3，体积小、兼容性强:m4a/mp3，音质无损:wav/flac
 * frequency 600 Hz → 标准电台摩斯常用音调,700~800 Hz → 人耳听起来比较清晰, 1000 Hz → 高频一些，但时间长了会刺耳
 * wpm       新手练习：5，业余无线电（Ham）考试：12-15，业余爱好者常用：18-20，电台 QSO 常见速度：25-30，竞赛或专业通信员的速度：35-40
 * volume    0.1 ~ 1.0
 * effectProcessor 可选，用于处理 PCM 数据，如添加尾声、添加特效、降噪处理等
 * extraInfo  可选，用于添加自定义信息，如作者、版权、描述等信息
 * bitRate   可选，单位 bps,默认128_000，输出MP3等格式需要
 */
public class Config {

    public static final int FREQUENCY_STANDARD = 600;
    public static final int FREQUENCY_NORMAL = 800;
    public static final int FREQUENCY_HIGH = 1000;
    public static final int WPM_SLOW = 10;
    public static final int WPM_NORMAL = 20;
    public static final int WPM_FAST = 30;
    public static final int WPM_VERY_FAST = 40;

    public static final String[] SUPPORT_FILE_TYPE = new String[]{"wav,mp3,m4a,flac"};

    public final String inputPath;
    public final String outputPath;
    public final PcmEffectProcessor effectProcessor;
    public Map<String, String> extraInfo;
    public int bitRate;
    public int wpm = WPM_NORMAL;
    public double volume = 0.1;
    public int frequency = FREQUENCY_STANDARD;

    public Config(@Nullable String inputPath,
                  @NotNull String outputPath,
                  @NotNull Map<String, String> extraInfo) {
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.extraInfo = extraInfo;
        this.effectProcessor = new AITailPcmAppender(this);
    }

    public Config setWpm(int wpm) {
        this.wpm = wpm;
        return this;
    }

    public Config setVolume(double volume) {
        this.volume = volume;
        return this;
    }

    public Config setFrequency(int frequency) {
        this.frequency = frequency;
        return this;
    }

    public Config setBitRate(int bitRate) {
        this.bitRate = bitRate;
        return this;
    }
}

