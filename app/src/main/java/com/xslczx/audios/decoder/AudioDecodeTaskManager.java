package com.xslczx.audios.decoder;

import android.media.MediaFormat;

import java.util.*;
import java.util.concurrent.*;

public class AudioDecodeTaskManager {
    private static final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public static String startDecodeTask(List<String> audioPaths, AudioDecoderConcatenator.DecodeCallback callback) {
        String taskId = UUID.randomUUID().toString();
        
        Future<?> future = executor.submit(() -> {
            try {
                byte[] result = AudioDecoderConcatenator.decodeAndConcatenateInternal(audioPaths, callback, taskId);
                if (result != null && !Thread.currentThread().isInterrupted()) {
                    MediaFormat format = AudioDecoderConcatenator.getAudioFormat(audioPaths.get(0));
                    int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    callback.onSuccess(result, sampleRate, channels);
                }
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    callback.onError(e);
                }
            } finally {
                runningTasks.remove(taskId);
            }
        });
        
        runningTasks.put(taskId, future);
        return taskId;
    }

    public static boolean cancelTask(String taskId) {
        Future<?> future = runningTasks.get(taskId);
        if (future != null) {
            boolean cancelled = future.cancel(true);
            runningTasks.remove(taskId);
            return cancelled;
        }
        return false;
    }

    public static void cancelAllTasks() {
        for (Future<?> future : runningTasks.values()) {
            future.cancel(true);
        }
        runningTasks.clear();
    }

    public static boolean isTaskRunning(String taskId) {
        Future<?> future = runningTasks.get(taskId);
        return future != null && !future.isDone();
    }
}