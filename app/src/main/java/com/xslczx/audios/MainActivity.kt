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
import com.xslczx.audios.processor.AITailPcmAppender
import com.xslczx.audios.processor.OnProcessAdapter
import com.xslczx.audios.tag.Tagger
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        DialogX.init(this)
        binding.btnGenerateMorse.setOnClickListener {
            val outputFile = getOutputFile("output_ai_morse")
            val waitDialog = WaitDialog.show("Generating...")
            val config = Config(
                null,
                outputFile.absolutePath,
                mutableMapOf<String, String>().apply {
                    put("AIGC", aigc.toString())
                }
            ).setVolume(0.5)
            val AIGCAudioProcessor =
                AIGCAudioProcessor(config, object : OnProcessAdapter() {

                    override fun onProgress(progress: Float) {
                        super.onProgress(progress)
                        waitDialog.setMessageContent("Generating... ${(progress * 100).toInt()}%")
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
                })
            waitDialog.setOnBackPressedListener {
                AIGCAudioProcessor.stop()
                true
            }
            AIGCAudioProcessor.generateAIAsync()
        }
        binding.btnGenerateMusic.setOnClickListener {
            val waitDialog = WaitDialog.show("Generating...")
            val assetsPath = "music_002.mp3"
            val inputFile = File(filesDir, assetsPath)
            val inputStream = assets.open(assetsPath)
            inputStream.copyTo(inputFile.outputStream())
            val outputFile = getOutputFile("output_music_002")
            val config = Config(
                inputFile.absolutePath,
                outputFile.absolutePath,
                mutableMapOf<String, String>().apply {
                    put("AIGC", aigc.toString())
                }
            )
            val AIGCAudioProcessor =
                AIGCAudioProcessor(config, object : OnProcessAdapter() {

                    override fun onProgress(progress: Float) {
                        super.onProgress(progress)
                        waitDialog.setMessageContent("Generating... ${(progress * 100).toInt()}%")
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
                })
            waitDialog.setOnBackPressedListener {
                AIGCAudioProcessor.stop()
                true
            }
            AIGCAudioProcessor.startAsync()
        }
        binding.btnUpdateTag.setOnClickListener {
            val outputFile = getOutputFile("output_music_002")
            if (!outputFile.exists()) {
                TipDialog.show("请先生成音频", WaitDialog.TYPE.ERROR)
                return@setOnClickListener
            }
            try {
                Tagger.updateCustomInfo(outputFile.absolutePath, hashMapOf<String, String>().apply {
                    put("AIGC", aigc.toString())
                })
                TipDialog.show("成功", WaitDialog.TYPE.SUCCESS)
            } catch (e: Exception) {
                TipDialog.show("不支持的文件格式", WaitDialog.TYPE.ERROR)
                Log.e(">>>:Tagger", "tag", e)
            }
        }
    }

    private val aigc = JSONObject().apply {
        putOpt("Label", "xxxxx")
        putOpt("Product", "aaaa")
    }

    private fun getOutputFile(name: String): File {
        val outExtension = when (binding.rgOutputType.checkedRadioButtonId) {
            R.id.rb_output_wav -> "wav"
            R.id.rb_output_mp3 -> "mp3"
            R.id.rb_output_m4a -> "m4a"
            R.id.rb_output_flac -> "flac"
            else -> "wav"
        }
        val outputFile = File(filesDir, "$name.$outExtension")
        return outputFile
    }
}