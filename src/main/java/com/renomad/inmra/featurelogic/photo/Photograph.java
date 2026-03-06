package com.renomad.inmra.featurelogic.photo;


import com.renomad.minum.database.DbData;

import java.util.Objects;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

public final class Photograph extends DbData<Photograph> {

    public static final Photograph EMPTY = new Photograph(0L, "", "", "");
    private final String photoUrl;
    private final String shortDescription;
    private final String description;
    private long index;

    public Photograph(Long index, String photoUrl, String shortDescription, String description) {
        this.index = index;
        this.photoUrl = photoUrl;
        this.shortDescription = shortDescription;
        this.description = description;
    }

    @Override
    public String serialize() {
        return serializeHelper(index, photoUrl, shortDescription, description);
    }

    @Override
    public Photograph deserialize(String serializedText) {
        final var tokens = deserializeHelper(serializedText);

        return new Photograph(
                Long.parseLong(tokens.get(0)),
                tokens.get(1),
                tokens.get(2),
                tokens.get(3));
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
    public String toString() {
        return "Photograph{" +
                "index=" + index +
                ", photoUrl='" + photoUrl + '\'' +
                ", shortDescription='" + shortDescription + '\'' +
                ", description='" + description + '\'' +
                '}';
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Photograph that = (Photograph) o;
        return Objects.equals(index, that.index) && Objects.equals(photoUrl, that.photoUrl) && Objects.equals(shortDescription, that.shortDescription) && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, photoUrl, shortDescription, description);
    }
}
