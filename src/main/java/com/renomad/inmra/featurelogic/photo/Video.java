package com.renomad.inmra.featurelogic.photo;


import com.renomad.minum.database.DbData;

import java.util.Objects;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

public final class Video extends DbData<Video> {

    public static final Video EMPTY = new Video(0L, "", "", "", "");
    private Long index;
    private final String videoUrl;
    private final String shortDescription;
    private final String description;
    private final String poster;

    public Video(Long index, String videoUrl, String shortDescription, String description, String poster) {
        this.index = index;
        this.videoUrl = videoUrl;
        this.shortDescription = shortDescription;
        this.description = description;
        this.poster = poster;
    }

    @Override
    public long getIndex() {
        return index;
    }

    @Override
    public void setIndex(long index) {
        this.index = index;
    }

    @Override
    public String serialize() {
        return serializeHelper(index, videoUrl, shortDescription, description, poster);
    }

    @Override
    public Video deserialize(String serializedText) {
        final var tokens = deserializeHelper(serializedText);

        return new Video(
                Long.parseLong(tokens.get(0)),
                tokens.get(1),
                tokens.get(2),
                tokens.get(3),
                tokens.get(4)
        );
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getDescription() {
        return description;
    }

    public String getPoster() {
        return poster;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Video video = (Video) o;
        return Objects.equals(index, video.index) && Objects.equals(videoUrl, video.videoUrl) && Objects.equals(shortDescription, video.shortDescription) && Objects.equals(description, video.description) && Objects.equals(poster, video.poster);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, videoUrl, shortDescription, description, poster);
    }

    @Override
    public String toString() {
        return "Video{" +
                "index=" + index +
                ", videoUrl='" + videoUrl + '\'' +
                ", shortDescription='" + shortDescription + '\'' +
                ", description='" + description + '\'' +
                ", poster='" + poster + '\'' +
                '}';
    }
}
