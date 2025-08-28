# Audios

## 项目概述

本项目旨在为音频文件添加AIGC标识 **摩斯码 AI 标识(显式)**，并支持修改音频文件的 **ID3 元数据(隐式)**，同时提供多种输出格式。通过摩斯码生成短音频序列，将其叠加到原始音频中，实现 AI 标识的可听化和可追踪化。  

MP3 编码使用的是 **自编译 LAME** 库，保证对 Android 和跨平台的可控性。

---

## 功能特点

1. **摩斯码 AI 标识**  
   - 将 "AI" 或自定义文本转换为摩斯码音频。
   - 可配置频率、速度（WPM）、音量和声道数。
   - 支持单声道和立体声生成。
   - PCM 数据可直接用于编码或混入原始音频。

2. **音频格式支持**  
   - **WAV**：16-bit PCM，标准无损格式。  
   - **MP3**：使用自编译 LAME 实现，支持动态比特率和质量设置。  
   - **FLAC**：无损压缩格式，支持 PCM 转 FLAC。  

3. **ID3 元数据修改**  
   - 可修改 MP3 文件的标题、艺术家、专辑、用户自定义标签等信息。  
   - 支持 v1/v2 标签版本。  

---

## 技术实现

1. **摩斯码生成**
   - 将文本编码为摩斯码，支持字母间 `@` 和单词间 `/` 分隔。
   - 将摩斯码转为 PCM 音频，支持自定义：
     - 频率（Hz）
     - 速度（WPM）
     - 采样率
     - 音量
     - 声道数（可动态设置）
   - PCM 输出为 16-bit 小端序，交错声道支持立体声。

2. **MP3 编码（自编译 LAME）**
   - JNI 封装 LAME，提供 `initEncoder`、`encode`、`flush`、`close` 接口。
   - 编码完成后可与原音频合并或单独保存。

3. **WAV/FLAC 输出**
   - WAV：直接写 PCM 数据并生成标准 WAV 头。  
   - FLAC：系统 MediaCodec 编码，支持无损压缩。

4. **ID3 元数据修改**
   - 使用 `jaudiotagger` 或自定义实现修改 MP3 元信息。
   - 可写入用户自定义字段用于标识 AI 生成音频。

---

## 使用示例（Java/Kotlin）

```java
//摩斯码音频生成
MorseAudio morseAudio = new MorseAudio();
byte[] sound = morseAudio.morseWord2Sound("AI", 800, 13, 16000, 1.0, 2);

//修改元数据
Tagger.updateCustomInfo(outputFile.getAbsolutePath(), map);

//编码
Encoder encoder = EncoderFactory.createEncoder(config);
encoder.write(finalPcm);
encoder.flush();
encoder.closeQuietly();
