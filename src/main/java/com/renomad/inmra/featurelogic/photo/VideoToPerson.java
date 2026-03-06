package com.renomad.inmra.featurelogic.photo;

import com.renomad.minum.database.DbData;

import java.util.Objects;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

/**
 * Represents a relationship between a photograph and
 * a person.
 */
public class VideoToPerson extends DbData<VideoToPerson> {

    public static final VideoToPerson EMPTY = new VideoToPerson(0, 0, 0, "");
    private long index;
    private final long videoIndex;
    private final long personIndex;
    private final String videoUrl;

    public VideoToPerson(long index, long videoIndex, long personIndex, String videoUrl) {
        this.index = index;
        this.videoIndex = videoIndex;
        this.personIndex = personIndex;
        this.videoUrl = videoUrl;
    }

    @Override
    public String serialize() {
        return serializeHelper(index, videoIndex, personIndex, videoUrl);
    }

    @Override
    public VideoToPerson deserialize(String serializedText) {
        final var tokens = deserializeHelper(serializedText);

        return new VideoToPerson(
                Long.parseLong(tokens.get(0)),
                Long.parseLong(tokens.get(1)),
                Long.parseLong(tokens.get(2)),
                tokens.get(3)
        );
    }

    @Override
    public long getIndex() {
        return index;
    }

    @Override
    public void setIndex(long index) {
        this.index = index;
    }

    public long getVideoIndex() {
        return videoIndex;
    }

    public long getPersonIndex() {
        return personIndex;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoToPerson that = (VideoToPerson) o;
        return index == that.index && videoIndex == that.videoIndex && personIndex == that.personIndex && Objects.equals(videoUrl, that.videoUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, videoIndex, personIndex, videoUrl);
    }

    @Override
    public String toString() {
        return "VideoToPerson{" +
                "index=" + index +
                ", videoIndex=" + videoIndex +
                ", personIndex=" + personIndex +
                ", videoUrl='" + videoUrl + '\'' +
                '}';
    }
}
