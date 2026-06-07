package com.xslczx.audios.encoder;

import android.media.MediaCodecInfo;

import com.xslczx.audios.datas.Config;

import java.util.Locale;

public class EncoderFactory {
    private static final String WAV_EXTENSION = ".wav";
    private static final String MP3_EXTENSION = ".mp3";
    private static final String M4A_EXTENSION = ".m4a";
    private static final String FLAC_EXTENSION = ".flac";

    public static Encoder createEncoder(Config config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        String normalizedOutputPath = config.outputPath.trim();
        if (normalizedOutputPath.isEmpty()) {
            throw new IllegalArgumentException("输出路径不能为空");
        }

        String normalizedExtension = normalizedOutputPath.toLowerCase(Locale.ROOT);
        if (normalizedExtension.endsWith(WAV_EXTENSION)) {
            return new WavEncoder(normalizedOutputPath);
        }
        if (normalizedExtension.endsWith(MP3_EXTENSION)) {
            return new Mp3Encoder(normalizedOutputPath);
        }
        if (normalizedExtension.endsWith(M4A_EXTENSION)) {
            return new AacEncoder(normalizedOutputPath, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        }
        if (normalizedExtension.endsWith(FLAC_EXTENSION)) {
            return new FlacEncoder(normalizedOutputPath);
        }
        throw new IllegalArgumentException("不支持的输出格式: " + normalizedExtension);
    }
}
