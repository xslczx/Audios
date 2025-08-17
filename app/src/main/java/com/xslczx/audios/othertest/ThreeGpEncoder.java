package com.xslczx.audios.othertest;

import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import com.xslczx.audios.encoder.MediaCodecEncoder;

public class ThreeGpEncoder extends MediaCodecEncoder {
    public ThreeGpEncoder(String path) {
        super(path, MediaFormat.MIMETYPE_AUDIO_AAC,
                Build.VERSION.SDK_INT >= 26 ? MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
                        : MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }
}

