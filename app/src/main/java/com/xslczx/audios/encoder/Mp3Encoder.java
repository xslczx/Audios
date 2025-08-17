package com.xslczx.audios.encoder;


import com.github.axet.lamejni.Lame;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Mp3Encoder implements Encoder {

    private final Lame lame;
    private final String path;
    private FileOutputStream fos;
    private boolean isPrepared = false;
    private boolean isClosed = false;

    public Mp3Encoder(String path) {
        this.path = path;
        this.lame = new Lame();
    }

    @Override
    public void prepare(int sampleRate, int channelCount, int bitDepth, int bitRate) throws Exception {
        if (isPrepared) return;
        fos = new FileOutputStream(path);
        lame.open(channelCount, sampleRate, bitRate/1000, 2);
        isPrepared = true;
    }

    @Override
    public void write(byte[] pcm) throws IOException {
        if (!isPrepared || isClosed || pcm == null || pcm.length == 0) return;

        int samples = pcm.length / 2; // 16bit PCM
        short[] pcmShorts = new short[samples];
        ByteBuffer.wrap(pcm)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .get(pcmShorts);

        int offset = 0;
        final int frameSize = 1024;
        while (offset < pcmShorts.length) {
            int toWrite = Math.min(frameSize, pcmShorts.length - offset);
            byte[] encoded = lame.encode(pcmShorts, offset, toWrite);
            if (encoded != null && encoded.length > 0) {
                fos.write(encoded);
            }
            offset += toWrite;
        }
    }

    @Override
    public void flush() throws IOException {
        if (!isPrepared || isClosed) return;

        byte[] tailBytes = lame.close();
        if (tailBytes != null && tailBytes.length > 0) {
            fos.write(tailBytes);
        }
    }

    @Override
    public void closeQuietly() {
        if (isClosed) return;
        try {
            if (fos != null) {
                fos.close();
            }
        } catch (IOException ignored) {
        } finally {
            isClosed = true;
        }
    }
}


