package com.xslczx.audios.encoder;

import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

public class ThreeGpEncoder extends MediaCodecEncoder {
    public ThreeGpEncoder(String path) {
        super(path, MediaFormat.MIMETYPE_AUDIO_AAC,
                Build.VERSION.SDK_INT >= 26 ? MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
                        : MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }
}

