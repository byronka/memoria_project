package com.renomad.inmra.featurelogic.photo;

import com.renomad.minum.database.DbData;

import java.util.Objects;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

/**
 * Represents a relationship between a photograph and
 * a person.
 */
public class PhotoToPerson extends DbData<PhotoToPerson> {

    public static final PhotoToPerson EMPTY = new PhotoToPerson(0, 0, 0, "");
    private long index;
    private final long photoIndex;
    private final long personIndex;
    private final String photoUrl;

    public PhotoToPerson(long index, long photoIndex, long personIndex, String photoUrl) {
        this.index = index;
        this.photoIndex = photoIndex;
        this.personIndex = personIndex;
        this.photoUrl = photoUrl;
    }

    @Override
    public String serialize() {
        return serializeHelper(index, photoIndex, personIndex, photoUrl);
    }

    @Override
    public PhotoToPerson deserialize(String serializedText) {
        final var tokens = deserializeHelper(serializedText);

        return new PhotoToPerson(
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

    public long getPhotoIndex() {
        return photoIndex;
    }

    public long getPersonIndex() {
        return personIndex;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PhotoToPerson that = (PhotoToPerson) o;
        return index == that.index && photoIndex == that.photoIndex && personIndex == that.personIndex && Objects.equals(photoUrl, that.photoUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, photoIndex, personIndex, photoUrl);
    }

    @Override
    public String toString() {
        return "PhotoToPerson{" +
                "index=" + index +
                ", photoIndex=" + photoIndex +
                ", personIndex=" + personIndex +
                ", photoUrl='" + photoUrl + '\'' +
                '}';
    }
}
