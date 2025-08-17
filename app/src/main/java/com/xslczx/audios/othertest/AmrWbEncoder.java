package com.xslczx.audios.othertest;

import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.xslczx.audios.encoder.MediaCodecEncoder;

@RequiresApi(api = Build.VERSION_CODES.O)
public class AmrWbEncoder extends MediaCodecEncoder {

    private final int[] bitRates = new int[]{
            6600, 8850, 12650, 14250, 15850, 18250, 19850, 23050, 23850
    };

    public AmrWbEncoder(String path) {
        super(path, MediaFormat.MIMETYPE_AUDIO_AMR_NB, MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP);
    }

    @Override
    public void prepare(int sampleRate, int channelCount, int bitDepth, int bitRate) throws Exception {
        super.prepare(16000, 1, bitDepth, nearestValue(bitRates, bitRate));
    }
}