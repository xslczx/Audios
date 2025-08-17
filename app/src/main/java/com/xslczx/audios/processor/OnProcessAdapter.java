package com.xslczx.audios.processor;

import android.util.Log;

import com.xslczx.audios.datas.AudioDecodeInfo;
import com.xslczx.audios.datas.AudioException;

import org.jetbrains.annotations.NotNull;

/**
 * 回调
 */
public class OnProcessAdapter implements AIGCAudioProcessor.OnProcessListener {
    @Override
    public void onStart(@NotNull AudioDecodeInfo info) {
        onInfo(Log.DEBUG, "音频信息==>" + info);
    }

    @Override
    public void onInfo(int level, @NotNull String extra) {
        Log.println(level, ">>>:AudioProcessor", extra);
    }

    @Override
    public void onProgress(float progress) {
        onInfo(Log.VERBOSE, "进度==>" + progress);
    }

    @Override
    public void onComplete(@NotNull String outputPath) {
        onInfo(Log.DEBUG, "音频处理完成==>" + outputPath);
    }

    @Override
    public void onError(@NotNull AudioException e) {
        onInfo(Log.ERROR, e.getMessageWithCause());
    }
}