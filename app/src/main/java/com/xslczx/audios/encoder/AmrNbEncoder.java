package com.xslczx.audios.encoder;

import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.O)
public class AmrNbEncoder extends MediaCodecEncoder {

    private final int[] bitRates = new int[]{
            4750, 5150, 5900, 6700, 7400, 7950, 10200, 12200
    };

    public AmrNbEncoder(String path) {
        super(path, MediaFormat.MIMETYPE_AUDIO_AMR_NB, MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP);
    }

    @Override
    public void prepare(int sampleRate, int channelCount, int bitDepth, int bitRate) throws Exception {
        super.prepare(8000, 1, bitDepth, nearestValue(bitRates, bitRate));
    }
}