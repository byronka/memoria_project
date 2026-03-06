package com.renomad.inmra.migrations;

import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.featurelogic.photo.Photograph;

import com.renomad.minum.database.Db;
import com.renomad.minum.database.DbData;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Context;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Collection;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

/**
 * Fix photo-to-person entries that don't point properly
 */
public class Migration7 {

    private final ILogger logger;
    private final Context context;
    private final Path dbDirectory;

    /**
     * This migration updates SessionsId to have a {@link ZonedDateTime}
     * when its session will be deleted.
     */
    public Migration7(Path dbDirectory, ILogger logger, Context context) {
        this.dbDirectory = dbDirectory;
        this.logger = logger;
        this.context = context;
    }

    /**
     * There's no way to run this in reverse.  It deletes invalid
     * entries in the database.  The reverse would mean putting invalid
     * entries back into the database.
     */
    public void run() {
        var photoDb = new Db<>(dbDirectory.resolve("photos"), context, Photograph.EMPTY);
        var personsDb = new Db<>(dbDirectory.resolve("persons"), context, Person.EMPTY);
        var photoToPersonDb = new Db<>(dbDirectory.resolve("photo_to_person"), context, PhotoToPerson.EMPTY);

        Collection<Person> personValues = personsDb.values();
        Collection<Photograph> photoValues = photoDb.values();
        Collection<PhotoToPerson> ptpValues = photoToPersonDb.values().stream().toList();
        for (var ptp : ptpValues) {
            boolean personNoneMatch = personValues.stream().noneMatch(x -> x.getIndex() == ptp.getPersonIndex());
            boolean photosNoneMatch = photoValues.stream().noneMatch((x -> x.getIndex() == ptp.getPhotoIndex()));
            if (personNoneMatch ||
                    photosNoneMatch
            ) {
                logger.logDebug(() -> String.format(
                        "migration7: ptp %s will be deleted - personNoneMatch: %s photosNoneMatch: %s",
                        ptp,
                        personNoneMatch,
                        photosNoneMatch));
                photoToPersonDb.delete(ptp);
            }
        }
    }

    /**
     * Represents a relationship between a photograph and
     * a person.
     */
    static class PhotoToPerson extends DbData<Migration7.PhotoToPerson> {

        public static final Migration7.PhotoToPerson EMPTY = new Migration7.PhotoToPerson(0, 0, 0);
        private long index;
        private final long photoIndex;
        private final long personIndex;

        public PhotoToPerson(long index, long photoIndex, long personIndex) {
            this.index = index;
            this.photoIndex = photoIndex;
            this.personIndex = personIndex;
        }

        @Override
        public String serialize() {
            return serializeHelper(index, photoIndex, personIndex);
        }

        @Override
        public Migration7.PhotoToPerson deserialize(String serializedText) {
            final var tokens = deserializeHelper(serializedText);

            return new Migration7.PhotoToPerson(
                    Long.parseLong(tokens.get(0)),
                    Long.parseLong(tokens.get(1)),
                    Long.parseLong(tokens.get(2)));
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
    }


}
