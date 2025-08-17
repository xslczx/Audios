package com.xslczx.audios.encoder;

import android.media.MediaFormat;
import android.media.MediaMuxer;

public class AacEncoder extends MediaCodecEncoder {

    public AacEncoder(String path, int profile) {
        super(path, MediaFormat.MIMETYPE_AUDIO_AAC, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, profile);
    }
}
