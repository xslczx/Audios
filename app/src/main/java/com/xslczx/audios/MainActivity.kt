package com.xslczx.audios

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.kongzue.dialogx.DialogX
import com.kongzue.dialogx.dialogs.TipDialog
import com.kongzue.dialogx.dialogs.WaitDialog
import com.xslczx.audios.databinding.ActivityMainBinding
import com.xslczx.audios.datas.AudioException
import com.xslczx.audios.datas.Config
import com.xslczx.audios.processor.AIGCAudioProcessor
import com.xslczx.audios.processor.OnProcessAdapter
import com.xslczx.audios.tag.Tagger
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {
    private companion object {
        const val OUTPUT_AI_MORSE = "output_ai_morse"
        const val OUTPUT_MUSIC = "output_music_002"
        const val DEMO_ASSET_NAME = "music_002.mp3"
        const val AIGC_FIELD_KEY = "AIGC"
        const val GENERATING_MESSAGE = "Generating..."
    }

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val aigcPayload = JSONObject().apply {
        putOpt("Label", "xxxxx")
        putOpt("Product", "aaaa")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        DialogX.init(this)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnGenerateMorse.setOnClickListener { generateMarkerAudio() }
        binding.btnGenerateMusic.setOnClickListener { generateTaggedMusic() }
        binding.btnUpdateTag.setOnClickListener { updateAudioTag() }
    }

    private fun generateMarkerAudio() {
        runAudioJob(
            config = Config(
                null,
                getOutputFile(OUTPUT_AI_MORSE).absolutePath,
                createAigcMetadata()
            ).setVolume(0.5),
            start = { generateAIAsync() }
        )
    }

    private fun generateTaggedMusic() {
        try {
            val inputFile = copyDemoAssetToInternalStorage(DEMO_ASSET_NAME)
            runAudioJob(
                config = Config(
                    inputFile.absolutePath,
                    getOutputFile(OUTPUT_MUSIC).absolutePath,
                    createAigcMetadata()
                ),
                start = { startAsync() }
            )
        } catch (exception: Exception) {
            showError("生成失败", exception)
        }
    }

    private fun updateAudioTag() {
        val outputFile = getOutputFile(OUTPUT_MUSIC)
        if (!outputFile.exists()) {
            TipDialog.show("请先生成音频", WaitDialog.TYPE.ERROR)
            return
        }

        try {
            Tagger.updateCustomInfo(outputFile.absolutePath, createAigcMetadata())
            TipDialog.show("成功", WaitDialog.TYPE.SUCCESS)
        } catch (exception: Exception) {
            showError("不支持的文件格式", exception)
        }
    }

    private fun runAudioJob(
        config: Config,
        start: AIGCAudioProcessor.() -> Unit
    ) {
        val waitDialog = WaitDialog.show(GENERATING_MESSAGE)
        val audioProcessor = AIGCAudioProcessor(config, createProcessListener(waitDialog))
        waitDialog.setOnBackPressedListener {
            audioProcessor.stop()
            true
        }
        audioProcessor.start()
    }

    private fun createProcessListener(waitDialog: WaitDialog): OnProcessAdapter {
        return object : OnProcessAdapter() {
            override fun onProgress(progress: Float) {
                super.onProgress(progress)
                waitDialog.setMessageContent("$GENERATING_MESSAGE ${(progress * 100).toInt()}%")
            }

            override fun onComplete(outputPath: String) {
                super.onComplete(outputPath)
                waitDialog.doDismiss()
                TipDialog.show("成功", WaitDialog.TYPE.SUCCESS)
            }

            override fun onError(e: AudioException) {
                super.onError(e)
                waitDialog.doDismiss()
                TipDialog.show(e.messageWithCause, WaitDialog.TYPE.ERROR)
            }
        }
    }

    private fun copyDemoAssetToInternalStorage(assetName: String): File {
        val targetFile = File(filesDir, assetName)
        assets.open(assetName).use { inputStream ->
            targetFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return targetFile
    }

    private fun createAigcMetadata(): MutableMap<String, String> {
        return mutableMapOf(AIGC_FIELD_KEY to aigcPayload.toString())
    }

    private fun showError(message: String, exception: Exception) {
        TipDialog.show(message, WaitDialog.TYPE.ERROR)
        Log.e(">>>:MainActivity", message, exception)
    }

    private fun getOutputFile(baseName: String): File {
        val outputExtension = when (binding.rgOutputType.checkedRadioButtonId) {
            R.id.rb_output_wav -> "wav"
            R.id.rb_output_mp3 -> "mp3"
            R.id.rb_output_m4a -> "m4a"
            R.id.rb_output_flac -> "flac"
            else -> "wav"
        }
        return File(filesDir, "$baseName.$outputExtension")
    }
}
