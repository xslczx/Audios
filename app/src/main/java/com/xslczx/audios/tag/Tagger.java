package com.xslczx.audios.tag;

import android.util.Log;

import com.xslczx.audios.datas.AudioException;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.ID3v22Frame;
import org.jaudiotagger.tag.id3.ID3v22Frames;
import org.jaudiotagger.tag.id3.ID3v22Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.mp4.field.Mp4TagTextField;
import org.jaudiotagger.tag.wav.WavInfoTag;
import org.jaudiotagger.tag.wav.WavTag;

import java.io.File;
import java.util.Locale;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Tagger {

    private static final String TAG = ">>>:Tagger";
    public static final Map<String, FieldKey> FIELD_KEY_MAP = new HashMap<>();

    static {
        FIELD_KEY_MAP.put("title", FieldKey.TITLE);          // 标题
        FIELD_KEY_MAP.put("artist", FieldKey.ARTIST);        // 艺术家
        FIELD_KEY_MAP.put("album", FieldKey.ALBUM);          // 专辑
        FIELD_KEY_MAP.put("albumArtist", FieldKey.ALBUM_ARTIST); // 专辑艺术家
        FIELD_KEY_MAP.put("composer", FieldKey.COMPOSER);    // 作曲
        FIELD_KEY_MAP.put("genre", FieldKey.GENRE);          // 流派
        FIELD_KEY_MAP.put("year", FieldKey.YEAR);            // 年份
        FIELD_KEY_MAP.put("track", FieldKey.TRACK);          // 音轨号
        FIELD_KEY_MAP.put("trackTotal", FieldKey.TRACK_TOTAL); // 音轨总数
        FIELD_KEY_MAP.put("disc", FieldKey.DISC_NO);         // 光盘号
        FIELD_KEY_MAP.put("discTotal", FieldKey.DISC_TOTAL); // 总碟数
        FIELD_KEY_MAP.put("comment", FieldKey.COMMENT);      // 评论
        FIELD_KEY_MAP.put("lyrics", FieldKey.LYRICS);        // 歌词
        FIELD_KEY_MAP.put("rating", FieldKey.RATING);        // 评分
        FIELD_KEY_MAP.put("cover", FieldKey.COVER_ART);      // 封面
    }

    /**
     * 更新自定义信息
     */
    public static void updateCustomInfo(String filePath, Map<String, String> extraInfo) throws Exception {
        validateUpdateArguments(filePath, extraInfo);

        AudioFile audioFile;
        try {
            audioFile = AudioFileIO.read(new File(filePath));
        } catch (Exception e) {
            throw new AudioException("不支持的音频格式", e);
        }

        Tag tag = audioFile.getTagOrCreateDefault();
        for (Map.Entry<String, String> entry : extraInfo.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null) continue;

            FieldKey fieldKey = FIELD_KEY_MAP.get(key);
            if (fieldKey != null) {
                tag.setField(fieldKey, value);
            } else {
                applyCustomField(audioFile, tag, key, value);
            }
        }
        audioFile.commit();
    }

    public static void printTag(String path) throws Exception {
        AudioFile f = AudioFileIO.read(new File(path));
        Tag createAndSetDefault = f.getTag();
        Iterator<TagField> fields = createAndSetDefault.getFields();
        while (fields.hasNext()) {
            TagField next = fields.next();
            Log.d(TAG, next.getId() + "==>" + next);
        }
    }

    private static void applyCustomField(AudioFile audioFile, Tag tag, String key, String value) throws Exception {
        String extension = audioFile.getExt().toLowerCase(Locale.ROOT);
        if (extension.endsWith("mp3") || audioFile instanceof MP3File) {
            MP3File mp3File = (MP3File) audioFile;
            mp3File.setID3v2TagOnly(createId3CustomTag(key, value));
            return;
        }

        if (extension.endsWith("wav") || tag instanceof WavTag) {
            WavTag wavTag = (WavTag) tag;
            wavTag.setID3Tag(createId3CustomTag(key, value));
            wavTag.setInfoTag(new WavInfoTag());
            audioFile.setTag(wavTag);
            return;
        }

        if (extension.endsWith("m4a") || tag instanceof Mp4Tag) {
            applyMp4CustomField(audioFile, (Mp4Tag) tag, key, value);
            return;
        }

        if (extension.endsWith("flac") || tag instanceof FlacTag) {
            applyFlacCustomField(audioFile, (FlacTag) tag, key, value);
            return;
        }

        throw new AudioException("不支持的音频格式");
    }

    private static ID3v22Tag createId3CustomTag(String key, String value) throws Exception {
        ID3v22Tag customTag = new ID3v22Tag();
        ID3v22Frame customFrame = new ID3v22Frame(ID3v22Frames.FRAME_ID_V2_USER_DEFINED_INFO);
        FrameBodyTXXX frameBody = (FrameBodyTXXX) customFrame.getBody();
        frameBody.setText(value);
        frameBody.setDescription(key);
        customTag.setFrame(customFrame);
        return customTag;
    }

    private static void applyMp4CustomField(AudioFile audioFile, Mp4Tag mp4Tag, String key, String value) throws Exception {
        Mp4TagTextField customField = new Mp4TagTextField(key, value);
        if (mp4Tag.hasField(customField.getId())) {
            mp4Tag.setField(customField);
        } else {
            mp4Tag.addField(customField);
        }
        audioFile.setTag(mp4Tag);
    }

    private static void applyFlacCustomField(AudioFile audioFile, FlacTag flacTag, String key, String value) throws Exception {
        TagField customField = flacTag.createField(key, value);
        if (flacTag.hasField(customField.getId())) {
            flacTag.setField(customField);
        } else {
            flacTag.addField(customField);
        }
        audioFile.setTag(flacTag);
    }

    private static void validateUpdateArguments(String filePath, Map<String, String> extraInfo) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        if (extraInfo == null || extraInfo.isEmpty()) {
            throw new IllegalArgumentException("extraInfo must not be empty");
        }
    }
}
