package com.xslczx.audios.datas;

import com.xslczx.audios.processor.PcmEffectProcessor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Config {
    public final String inputPath;
    public final String outputPath;
    public final PcmEffectProcessor effectProcessor;
    public int bitRate; // 可选，单位 bps,默认128_000，输出MP3/AAC 等格式需要

    public Config(
            @Nullable String inputPath,
            @NotNull String outputPath,
            @Nullable PcmEffectProcessor effectProcessor) {
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.effectProcessor = effectProcessor;
    }

    public Config setBitRate(int bitRate) {
        this.bitRate = bitRate;
        return this;
    }
}

