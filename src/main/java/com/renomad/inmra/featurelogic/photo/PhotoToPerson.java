package com.renomad.inmra.featurelogic.photo;

import com.renomad.minum.database.DbData;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

/**
 * Represents a relationship between a photograph and
 * a person.
 */
public class PhotoToPerson extends DbData<PhotoToPerson> {

    public static final PhotoToPerson EMPTY = new PhotoToPerson(0, 0, 0);
    private long index;
    private final long photoIndex;
    private final long personIndex;

    public PhotoToPerson(long index, long photoIndex, long personIndex) {
        this.index = index;
        this.photoIndex = photoIndex;
        this.personIndex = personIndex;
    }

    @Override
    protected String serialize() {
        return serializeHelper(index, photoIndex, personIndex);
    }

    @Override
    protected PhotoToPerson deserialize(String serializedText) {
        final var tokens = deserializeHelper(serializedText);

        return new PhotoToPerson(
                Long.parseLong(tokens.get(0)),
                Long.parseLong(tokens.get(1)),
                Long.parseLong(tokens.get(2)));
    }

    @Override
    protected long getIndex() {
        return index;
    }

    @Override
    protected void setIndex(long index) {
        this.index = index;
    }

    public long getPhotoIndex() {
        return photoIndex;
    }

    public long getPersonIndex() {
        return personIndex;
    }
}
