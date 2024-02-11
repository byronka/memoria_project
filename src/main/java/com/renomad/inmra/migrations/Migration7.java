package com.renomad.inmra.migrations;

import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.featurelogic.photo.PhotoToPerson;
import com.renomad.inmra.featurelogic.photo.Photograph;
import com.renomad.minum.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Collection;

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


    public void run() throws IOException {
        run(false);

    }

    /**
     * Convert the new form of Person back to its previous form
     */
    public void runReverse() throws IOException {
        run(true);
    }

    /**
     * There's no way to run this in reverse.  It deletes invalid
     * entries in the database.  The reverse would mean putting invalid
     * entries back into the database.
     */
    private void run(boolean runReverse) throws IOException {
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


}
