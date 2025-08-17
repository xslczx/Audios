package com.xslczx.audios.othertest;

import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.xslczx.audios.encoder.MediaCodecEncoder;

@RequiresApi(Build.VERSION_CODES.R)
public class OggOpusEncoder extends MediaCodecEncoder {

    private final int[] sampleRates = new int[]{
            8000, 12000, 16000, 24000, 48000
    };

    public OggOpusEncoder(String path) {
        super(path, MediaFormat.MIMETYPE_AUDIO_OPUS, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG);
    }

    @Override
    public void prepare(int sampleRate, int channelCount, int bitDepth, int bitRate) throws Exception {
        super.prepare(nearestValue(sampleRates, sampleRate), channelCount, bitDepth, bitRate);
    }
}
