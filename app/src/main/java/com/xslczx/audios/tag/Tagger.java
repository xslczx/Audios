package com.xslczx.audios.tag;

import android.util.Log;

import com.xslczx.audios.AudioException;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp3.MP3File;
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
import java.util.Iterator;

public class Tagger {

    private static final String TAG = ">>>:Tagger";

    public static void tag(String path, String key, String value) throws Exception {
        AudioFile audioFile;
        try {
            audioFile = AudioFileIO.read(new File(path));
        } catch (Exception e) {
            throw new AudioException("音频标识处理失败", e);
        }

        ID3v22Tag id3v22Tag = new ID3v22Tag();
        ID3v22Frame id3v22frame = new ID3v22Frame(ID3v22Frames.FRAME_ID_V2_USER_DEFINED_INFO);
        ((FrameBodyTXXX) id3v22frame.getBody()).setText(value);
        ((FrameBodyTXXX) id3v22frame.getBody()).setDescription(key);
        id3v22Tag.setFrame(id3v22frame);

        Tag tag = audioFile.getTagOrCreateDefault();
        Log.d(TAG, "tagOrCreateDefault: " + tag.getClass().getName());
        String ext = audioFile.getExt();
        if (ext.endsWith("mp3") || audioFile instanceof MP3File) {
            MP3File mp3File = ((MP3File) audioFile);
            mp3File.setID3v2TagOnly(id3v22Tag);
            mp3File.commit();
        } else if (ext.endsWith("wav") || tag instanceof WavTag) {
            WavTag wavTag = (WavTag) tag;
            WavInfoTag infoTag = new WavInfoTag();
            wavTag.setID3Tag(id3v22Tag);
            wavTag.setInfoTag(infoTag);
            audioFile.setTag(wavTag);
            audioFile.commit();
        } else if (ext.endsWith("m4a") || tag instanceof Mp4Tag) {
            Mp4Tag mp4Tag = (Mp4Tag) tag;
            Mp4TagTextField field = new Mp4TagTextField(key, value);
            if (mp4Tag.hasField(field.getId())) {
                mp4Tag.setField(field);
            } else {
                mp4Tag.addField(field);
            }
            audioFile.setTag(mp4Tag);
            audioFile.commit();
        } else if (ext.endsWith("flac") || tag instanceof FlacTag) {
            FlacTag flacTag = (FlacTag) tag;
            TagField flacTagField = flacTag.createField(key, value);
            if (flacTag.hasField(flacTagField.getId())) {
                flacTag.setField(flacTagField);
            } else {
                flacTag.addField(flacTagField);
            }
            audioFile.setTag(flacTag);
            audioFile.commit();
        }

        AudioFile f = AudioFileIO.read(new File(path));
        Tag createAndSetDefault = f.getTag();
        Iterator<TagField> fields = createAndSetDefault.getFields();
        while (fields.hasNext()) {
            TagField next = fields.next();
            Log.d(TAG, next.getId() + "==>" + next);
        }
    }
}
