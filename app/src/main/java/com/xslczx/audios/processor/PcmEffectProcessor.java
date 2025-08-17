package com.xslczx.audios.processor;

import java.io.IOException;

/**
 * PCM 处理接口
 */
public interface PcmEffectProcessor {

    /**
     * 准备处理
     *
     * @param sampleRate   采样率
     * @param channelCount 声道数
     * @param bitDepth     位深
     * @param bitrate      码率
     */
    void prepare(int sampleRate, int channelCount, int bitDepth, int bitrate);

    /**
     * 处理解码过程中输出的 PCM 数据
     *
     * @param pcmData 当前 PCM 数据片段
     */
    void write(byte[] pcmData) throws IOException;

    /**
     * 当整个解码流程完成时调用
     * 用于最终处理或合并 PCM 数据
     *
     * @return 返回最终 PCM 数据
     */
    byte[] flush() throws IOException;
}
