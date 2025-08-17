package com.xslczx.audios;

import android.media.MediaCodecInfo;
import android.os.Build;

import com.xslczx.audios.encoder.AacEncoder;
import com.xslczx.audios.encoder.AmrNbEncoder;
import com.xslczx.audios.encoder.AmrWbEncoder;
import com.xslczx.audios.encoder.Encoder;
import com.xslczx.audios.encoder.FlacEncoder;
import com.xslczx.audios.encoder.Mp3Encoder;
import com.xslczx.audios.encoder.OggOpusEncoder;
import com.xslczx.audios.encoder.ThreeGpEncoder;
import com.xslczx.audios.encoder.WavEncoder;

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
        } else if (lower.endsWith(".aac")) {
            return new AacEncoder(config.outputPath, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        } else if (lower.endsWith(".m4a")) {
            return new AacEncoder(config.outputPath, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        } else if (lower.endsWith(".amr")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return new AmrNbEncoder(config.outputPath);
            }
        } else if (lower.endsWith(".awb")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return new AmrWbEncoder(config.outputPath);
            }
        } else if (lower.endsWith(".3gp") || lower.endsWith(".3gpp")) {
            return new ThreeGpEncoder(config.outputPath);
        } else if (lower.endsWith(".flac")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return new FlacEncoder(config.outputPath);
            }
        } else if (lower.endsWith(".ogg")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return new OggOpusEncoder(config.outputPath);
            }
        }
        throw new IllegalArgumentException("不支持的输出格式: " + lower);
    }
}

