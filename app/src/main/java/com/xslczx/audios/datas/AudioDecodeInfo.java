package com.xslczx.audios.datas;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class AudioDecodeInfo {
    public final int sampleRate;
    public final int channelCount;
    public final int pcmEncoding;
    public final long durationUs;

    public AudioDecodeInfo(int sr, int ch, int enc, long dur) {
        this.sampleRate = sr;
        this.channelCount = ch;
        this.pcmEncoding = enc;
        this.durationUs = dur;
    }

    public String formatDuration() {
        if (durationUs <= 0) return "00:00";

        long totalSeconds = durationUs / 1_000_000; // 微秒转秒
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    @NotNull
    public String toString() {
        return String.format(Locale.getDefault(), "采样率：%d Hz\n声道数：%d\n位深：%d\n时长：%s",
                sampleRate, channelCount, pcmEncoding, formatDuration());
    }
}
