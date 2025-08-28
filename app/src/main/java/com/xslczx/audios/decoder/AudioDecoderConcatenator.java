package com.xslczx.audios.decoder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

public class AudioDecoderConcatenator {
    private static final int PCM_DEQUEUE_TIMEOUT_US = 10000;
    private static final int MIN_PROGRESS_INTERVAL_MS = 100;
    private static final int PCM_ENCODING_BIT_DEPTH = 16;

    private static final Map<String, Boolean> cancellationMap = new ConcurrentHashMap<>();

    public interface DecodeCallback {
        void onProgress(float progress, long currentTimeUs);
        void onSuccess(byte[] pcmData, int sampleRate, int channels);
        void onError(Exception exception);
        default void onCancelled() {
            // 可选的取消回调
        }
    }

    public static String decodeAndConcatenate(List<String> audioPaths, DecodeCallback callback) {
        return AudioDecodeTaskManager.startDecodeTask(audioPaths, callback);
    }

    static byte[] decodeAndConcatenateInternal(List<String> audioPaths, DecodeCallback callback, String taskId) {
        cancellationMap.put(taskId, false);
        ByteArrayOutputStream concatenatedOutputStream = new ByteArrayOutputStream();

        try {
            int totalFiles = audioPaths.size();
            long totalDurationUs = 0;
            List<Long> fileDurations = new ArrayList<>();
            List<MediaFormat> fileFormats = new ArrayList<>();

            // 首先验证所有文件的格式是否一致
            int sampleRate = -1;
            int channels = -1;
            
            for (String path : audioPaths) {
                checkCancellation(taskId);
                MediaFormat format = getAudioFormat(path);
                fileFormats.add(format);
                
                long durationUs = format.containsKey(MediaFormat.KEY_DURATION) ? 
                        format.getLong(MediaFormat.KEY_DURATION) : 0;
                fileDurations.add(durationUs);
                totalDurationUs += durationUs;
                
                // 检查采样率和通道数是否一致
                int currentSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                int currentChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                
                if (sampleRate == -1) {
                    sampleRate = currentSampleRate;
                    channels = currentChannels;
                } else if (sampleRate != currentSampleRate || channels != currentChannels) {
                    throw new Exception("音频文件格式不一致: " + path + 
                            " (期望: " + sampleRate + "Hz, " + channels + "声道, " +
                            "实际: " + currentSampleRate + "Hz, " + currentChannels + "声道)");
                }
            }

            long processedDurationUs = 0;

            for (int i = 0; i < totalFiles; i++) {
                checkCancellation(taskId);
                
                String path = audioPaths.get(i);
                long fileDurationUs = fileDurations.get(i);
                MediaFormat format = fileFormats.get(i);

                callback.onProgress(processedDurationUs * 1f / totalDurationUs, processedDurationUs);

                long finalProcessedDurationUs = processedDurationUs;
                long finalTotalDurationUs = totalDurationUs;
                byte[] pcmData = decodeSingleFile(path, format, new DecodeCallback() {
                    @Override
                    public void onProgress(float progress, long currentTimeUs) {
                        checkCancellation(taskId);
                        long totalCurrentTimeUs = finalProcessedDurationUs + currentTimeUs;
                        float overallProgress = finalTotalDurationUs > 0 ?
                                totalCurrentTimeUs * 1f / finalTotalDurationUs : 0f;
                        callback.onProgress(overallProgress, totalCurrentTimeUs);
                    }

                    @Override
                    public void onSuccess(byte[] pcmData, int sampleRate, int channels) {
                        // 单个文件成功
                    }

                    @Override
                    public void onError(Exception exception) {
                        callback.onError(new Exception("文件解码失败: " + path, exception));
                    }

                    @Override
                    public void onCancelled() {
                        // 处理取消
                    }
                }, taskId);

                if (pcmData != null && pcmData.length > 0) {
                    concatenatedOutputStream.write(pcmData, 0, pcmData.length);
                    processedDurationUs += fileDurationUs;
                }
            }

            return concatenatedOutputStream.toByteArray();

        } catch (CancellationException e) {
            callback.onCancelled();
            throw e;
        } catch (Exception e) {
            callback.onError(e);
            return null;
        } finally {
            cancellationMap.remove(taskId);
            closeQuietly(concatenatedOutputStream);
        }
    }

    private static byte[] decodeSingleFile(String path, MediaFormat expectedFormat, DecodeCallback callback, String taskId) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        ByteArrayOutputStream pcmOutputStream = null;

        try {
            checkCancellation(taskId);
            extractor.setDataSource(path);
            
            int trackIndex = selectAudioTrack(extractor);
            if (trackIndex < 0) {
                throw new Exception("找不到音轨: " + path);
            }
            
            extractor.selectTrack(trackIndex);
            MediaFormat actualFormat = extractor.getTrackFormat(trackIndex);
            
            // 验证格式是否匹配
            int expectedSampleRate = expectedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int expectedChannels = expectedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int actualSampleRate = actualFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int actualChannels = actualFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            
            if (expectedSampleRate != actualSampleRate || expectedChannels != actualChannels) {
                throw new Exception("音频格式不匹配: " + path + 
                        " (期望: " + expectedSampleRate + "Hz, " + expectedChannels + "声道, " +
                        "实际: " + actualSampleRate + "Hz, " + actualChannels + "声道)");
            }
            
            String mime = actualFormat.getString(MediaFormat.KEY_MIME);
            
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(actualFormat, null, null, 0);
            codec.start();

            long durationUs = actualFormat.containsKey(MediaFormat.KEY_DURATION) ? 
                    actualFormat.getLong(MediaFormat.KEY_DURATION) : -1;

            pcmOutputStream = new ByteArrayOutputStream();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean isEOS = false;
            float lastProgress = 0f;
            long lastProgressTime = 0;

            while (!isEOS) {
                checkCancellation(taskId);

                // 输入处理
                int inIndex = codec.dequeueInputBuffer(PCM_DEQUEUE_TIMEOUT_US);
                if (inIndex >= 0) {
                    ByteBuffer inputBuffer = getCodecBuffer(codec, inIndex, true);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, 
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        long pts = extractor.getSampleTime();
                        codec.queueInputBuffer(inIndex, 0, sampleSize, pts, 0);
                        extractor.advance();
                    }
                }

                // 输出处理
                int outIndex = codec.dequeueOutputBuffer(bufferInfo, PCM_DEQUEUE_TIMEOUT_US);
                if (outIndex >= 0) {
                    try {
                        ByteBuffer outputBuffer = getCodecBuffer(codec, outIndex, false);
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            // 直接写入PCM数据，不处理时间戳
                            byte[] chunk = new byte[bufferInfo.size];
                            outputBuffer.get(chunk);
                            pcmOutputStream.write(chunk, 0, chunk.length);
                        }

                        // 进度回调
                        if (durationUs > 0 && bufferInfo.presentationTimeUs >= 0) {
                            float progress = Math.min(bufferInfo.presentationTimeUs * 1f / durationUs, 1f);
                            long now = System.currentTimeMillis();
                            
                            if ((progress - lastProgress >= 0.005f || progress == 1f) &&
                                (now - lastProgressTime >= MIN_PROGRESS_INTERVAL_MS)) {
                                lastProgress = progress;
                                lastProgressTime = now;
                                callback.onProgress(progress, bufferInfo.presentationTimeUs);
                            }
                        }

                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isEOS = true;
                        }
                    } finally {
                        codec.releaseOutputBuffer(outIndex, false);
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 处理格式变化
                    MediaFormat newFormat = codec.getOutputFormat();
                    // 可以在这里验证输出格式
                }
                
                checkCancellation(taskId);
            }

            return pcmOutputStream.toByteArray();

        } catch (CancellationException e) {
            callback.onCancelled();
            throw e;
        } catch (Exception e) {
            callback.onError(e);
            return null;
        } finally {
            closeQuietly(codec);
            closeQuietly(extractor);
            closeQuietly(pcmOutputStream);
        }
    }

    // 添加格式验证方法
    private static boolean isFormatCompatible(MediaFormat format1, MediaFormat format2) {
        if (format1 == null || format2 == null) return false;
        
        int sampleRate1 = format1.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channels1 = format1.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int sampleRate2 = format2.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channels2 = format2.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        
        return sampleRate1 == sampleRate2 && channels1 == channels2;
    }

    // 检查取消状态
    private static void checkCancellation(String taskId) {
        if (Thread.currentThread().isInterrupted() || 
            Boolean.TRUE.equals(cancellationMap.get(taskId))) {
            throw new CancellationException("任务已被取消");
        }
    }

    static MediaFormat getAudioFormat(String path) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(path);
            int trackIndex = selectAudioTrack(extractor);
            if (trackIndex < 0) {
                throw new IOException("找不到音轨: " + path);
            }
            return extractor.getTrackFormat(trackIndex);
        } finally {
            extractor.release();
        }
    }

    private static int selectAudioTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private static ByteBuffer getCodecBuffer(MediaCodec codec, int index, boolean isInput) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return isInput ? codec.getInputBuffer(index) : codec.getOutputBuffer(index);
        } else {
            ByteBuffer[] buffers = isInput ? codec.getInputBuffers() : codec.getOutputBuffers();
            return buffers[index];
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignore) {
            }
        }
    }

    private static void closeQuietly(MediaCodec codec) {
        if (codec != null) {
            try {
                codec.stop();
            } catch (Exception ignore) {
            }
            try {
                codec.release();
            } catch (Exception ignore) {
            }
        }
    }

    private static void closeQuietly(MediaExtractor extractor) {
        if (extractor != null) {
            try {
                extractor.release();
            } catch (Exception ignore) {
            }
        }
    }
}