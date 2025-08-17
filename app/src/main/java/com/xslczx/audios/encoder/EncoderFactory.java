package com.xslczx.audios.encoder;

import android.media.MediaCodecInfo;

import com.xslczx.audios.datas.Config;

public class EncoderFactory {

    public static Encoder createEncoder(Config config) {
        if (config.outputPath == null || config.outputPath.isEmpty()) {
            throw new IllegalArgumentException("输出路径不能为空");
        }

        String lower = config.outputPath.toLowerCase();

        if (lower.endsWith(".wav")) {
            return new WavEncoder(config.outputPath);
        } else if (lower.endsWith(".mp3")) {
            return new Mp3Encoder(config.outputPath);
        } else if (lower.endsWith(".m4a")) {
            return new AacEncoder(config.outputPath, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        } else if (lower.endsWith(".flac")) {
            return new FlacEncoder(config.outputPath);
        }
        throw new IllegalArgumentException("不支持的输出格式: " + lower);
    }
}

