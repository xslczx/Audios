# Audios

一个 Android 音频处理 Demo，用来给音频追加可听的 `AI` 摩斯码尾标，并把额外的 AIGC 信息写入输出文件元数据。

当前代码的主流程是：

1. 解码输入音频为 PCM
2. 在尾部追加 `AI` 摩斯码 PCM
3. 按输出文件扩展名重新编码
4. 把 `extraInfo` 写入输出文件标签

## 当前实际能力

- 生成纯摩斯码音频，不依赖输入文件
- 给单个或多个输入音频追加 `AI` 摩斯码尾标
- 输出格式按文件后缀自动选择：
  - `.wav`
  - `.mp3`
  - `.m4a`
  - `.flac`
- 通过 `Tagger.updateCustomInfo(...)` 写入标准字段或自定义字段
- MP3 编码使用 JNI + 预编译 `libmp3lame.so`

## 当前限制

- App 界面当前只暴露 `wav/mp3/m4a/flac`，其他编码器类在项目里属于实验代码，未接入主流程
- 摩斯码尾标固定为 `AI`，主处理链路没有开放自定义文本入口
- 多文件拼接要求输入音频采样率和声道数一致，否则会直接报错
- MP3 原生库当前只随仓库提供了 `armeabi-v7a` 和 `arm64-v8a`

## 项目结构

- `app/src/main/java/com/xslczx/audios/MainActivity.kt`
  - Demo 入口，提供“生成摩斯码音频”和“生成 AIGC 音频”两个按钮
- `app/src/main/java/com/xslczx/audios/processor/AIGCAudioProcessor.java`
  - 主处理流程：解码、追加尾标、编码、写标签
- `app/src/main/java/com/xslczx/audios/morse/`
  - 摩斯码编码和 PCM 生成
- `app/src/main/java/com/xslczx/audios/encoder/`
  - `wav/mp3/m4a/flac` 编码器
- `app/src/main/java/com/xslczx/audios/tag/Tagger.java`
  - 音频元数据写入
- `app/src/main/cpp/` + `app/src/main/jniLibs/`
  - LAME JNI 封装和预编译 so

## 构建与运行

环境以仓库当前配置为准：

- Android Studio
- Android SDK `compileSdk 36`
- `minSdk 21`
- JDK 11
- NDK `21.4.7075529`

直接运行：

```bash
./gradlew :app:assembleDebug
```

项目已经包含 MP3 所需的 so 文件；如果你要重新编译 LAME，可参考根目录的 [build-lame.sh](/Users/lijianglong/AndroidStudioProjects/Audios/build-lame.sh)。

## Demo 用法

运行 App 后可以直接测试两条路径：

- `生成摩斯码音频`
  - 直接生成一个只有 `AI` 摩斯码的音频文件
- `生成AIGC音乐`
  - 把 `app/src/main/assets/music_002.mp3` 复制到内部存储后，追加摩斯码尾标并导出

输出文件保存在应用内部目录 `filesDir`，文件扩展名由界面中的单选框决定。

## 代码用法

### 1. 生成纯 `AI` 摩斯码音频

```java
Map<String, String> extraInfo = new HashMap<>();
extraInfo.put("AIGC", "{\"Label\":\"xxxxx\",\"Product\":\"aaaa\"}");

Config config = new Config(
        null,
        outputPath,   // 例如 /data/user/0/.../output_ai_morse.wav
        extraInfo
).setVolume(0.5);

new AIGCAudioProcessor(config, null).generateAIAsync();
```

### 2. 给输入音频追加 `AI` 尾标并重新编码

```java
Map<String, String> extraInfo = new HashMap<>();
extraInfo.put("AIGC", "{\"Label\":\"xxxxx\",\"Product\":\"aaaa\"}");

Config config = new Config(
        inputPath,
        outputPath,   // 后缀决定输出格式：.wav/.mp3/.m4a/.flac
        extraInfo
).setFrequency(Config.FREQUENCY_NORMAL)
 .setWpm(Config.WPM_NORMAL)
 .setVolume(0.1)
 .setBitRate(128_000);

new AIGCAudioProcessor(config, null).startAsync();
```

### 3. 多文件拼接后统一追加尾标

```java
List<String> audioPaths = Arrays.asList(path1, path2, path3);

Config config = new Config(audioPaths, outputPath, extraInfo);
new AIGCAudioProcessor(config, null).startAsync();
```

### 4. 单独更新音频元数据

```java
Map<String, String> extraInfo = new HashMap<>();
extraInfo.put("title", "demo");
extraInfo.put("artist", "audios");
extraInfo.put("AIGC", "{\"Label\":\"xxxxx\",\"Product\":\"aaaa\"}");

Tagger.updateCustomInfo(outputPath, extraInfo);
```

## 元数据说明

`Tagger` 目前支持两类字段：

- 标准字段：`title`、`artist`、`album`、`albumArtist`、`composer`、`genre`、`year`、`track`、`trackTotal`、`disc`、`discTotal`、`comment`、`lyrics`、`rating`、`cover`
- 自定义字段：不在上述映射中的 key 会按文件类型写入自定义标签

不同容器的自定义字段写入方式不同，因此实际兼容性取决于播放器和目标格式。

## 实现细节

- 摩斯码音频由 `MorseAudio` 直接生成 16-bit little-endian PCM
- 默认尾标处理器是 `AITailPcmAppender`
- `wav` 由项目内 `WavEncoder` 直接写文件
- `mp3` 由 `Mp3Encoder` 调用 LAME JNI
- `m4a` 和 `flac` 使用 Android `MediaCodec`

## 注意

- README 以当前仓库代码为准，不再把隐藏按钮、未接主流程的实验类、或未暴露的可配置项当作正式能力说明
- 如果后续把自定义摩斯文本、更多输出格式或公开 SDK API 接入主流程，README 需要同步更新
