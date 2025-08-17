package com.xslczx.audios.encoder;

public interface ProgressListener {
    /**
     * @param writtenBytes 已经写入的 PCM 字节数
     * @param totalBytes   总 PCM 字节数，如果未知可以传 -1
     */
    void onProgress(long writtenBytes, long totalBytes);
}