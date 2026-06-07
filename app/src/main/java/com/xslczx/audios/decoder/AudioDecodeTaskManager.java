package com.xslczx.audios.decoder;

import android.media.MediaFormat;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AudioDecodeTaskManager {
    private static final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public static String startDecodeTask(List<String> audioPaths, AudioDecoderConcatenator.DecodeCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        if (audioPaths == null || audioPaths.isEmpty()) {
            throw new IllegalArgumentException("audioPaths must not be empty");
        }

        String taskId = UUID.randomUUID().toString();

        Future<?> future = executor.submit(() -> {
            try {
                byte[] result = AudioDecoderConcatenator.decodeAndConcatenateInternal(audioPaths, callback, taskId);
                if (result != null && !Thread.currentThread().isInterrupted()) {
                    MediaFormat audioFormat = getPrimaryAudioFormat(audioPaths);
                    int sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
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

    private static MediaFormat getPrimaryAudioFormat(List<String> audioPaths) throws Exception {
        // The decode step already validated all inputs, so reusing the first path keeps
        // the callback contract stable while centralizing the format lookup in one place.
        return AudioDecoderConcatenator.getAudioFormat(audioPaths.get(0));
    }
}
