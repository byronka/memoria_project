package com.renomad.inmra.migrations;

import com.renomad.inmra.featurelogic.persons.Date;
import com.renomad.inmra.featurelogic.persons.Month;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.minum.logging.ILogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class Migration2 {

    /**
     * This migration converts the dates in the persons database
     * to proper values.  It turned out that the code for converting
     * HTML date strings to {@link com.renomad.inmra.featurelogic.persons.Date}
     * objects was off-by-one.
     * <p>
     *     Note that the date value stored in the person_files database is actually
     *     fine.
     * </p>
     */
    private final ILogger logger;
    private final Path dbDirectory;

    public Migration2(Path dbDirectory, ILogger logger) {
        this.dbDirectory = dbDirectory;
        this.logger = logger;
    }

    public void run() throws IOException {
        run(false);
    }

    private void run(boolean runReverse) throws IOException {
        Path personsDirectory = dbDirectory.resolve("persons");
        if (!Files.exists(personsDirectory)) return;

        String runMessage =  "Running migration2" + (runReverse ? " in reverse" : "");
        logger.logDebug(() -> runMessage);

        logger.logDebug(() -> "get all the paths (that is, all the files) in the persons directory");
        List<Path> listPersonFiles = getPaths(personsDirectory);

        logger.logDebug(() -> "for each one, adjust and then overwrite the file with the new adjusted content");
        for (var personPath : listPersonFiles) {
            var oldPerson = Person.EMPTY.deserialize(Files.readString(personPath));

            logger.logDebug(() -> String.format("processing %s - %s", personPath, oldPerson.getName()));

            // fix the values, but only if the value doesn't represent an empty value.
            // if empty, it will look like 0.JANUARY.0

            Date fixedBirthDate = fixDate(oldPerson.getBirthday(), runReverse);
            Date fixedDeathDate = fixDate(oldPerson.getDeathday(), runReverse);
            var fixedPerson = new Person(
                    oldPerson.getIndex(),
                    oldPerson.getId(),
                    oldPerson.getName(),
                    fixedBirthDate,
                    fixedDeathDate
            );

            // if we changed either the birthdate or deathdate, rewrite the file.
            if (fixedBirthDate != Date.EMPTY || fixedDeathDate != Date.EMPTY) {
                logger.logDebug(() -> "Writing file for " + personPath);
                Files.writeString(personPath, fixedPerson.serialize());
            }
        }
    }

    private static List<Path> getPaths(Path personsDirectory) throws IOException {
        try (Stream<Path> files = Files.walk(personsDirectory)) {
            return files.filter(Files::isRegularFile)
                    .filter(x -> !x.getFileName().toString().equalsIgnoreCase("index.ddps"))
                    .toList();
        }
    }

    /**
     * Fix a date.
     * <p>
     *     In this case, fixing a date means adjusting it to the previous month.
     *     We had been operating with a broken algorithm and storing the wrong
     *     month enum when converting between the HTML input value and enum.
     * </p>
     * <p>
     *     This migration fixes that.
     * </p>
     */
    private Date fixDate(Date date, boolean runReverse) {
        Date fixedDate = Date.EMPTY;
        if (! date.equals(Date.EMPTY)) {
            // get the previous month (e.g. if December, get November. If January, get December)
            int oldMonthOrdinal = date.month().getMonthOrdinal();
            int revisedOrdinal;
            if (runReverse) {
                revisedOrdinal = oldMonthOrdinal;
            } else {
                revisedOrdinal = (oldMonthOrdinal - 2) % 12;
            }

            Month correctMonth = Month.values()[revisedOrdinal];
            fixedDate = new Date(date.year(), correctMonth, date.day());
            String fixedDateString = fixedDate.toString();
            logger.logDebug(() -> String.format("date with invalid month: %s fixed date: %s", date, fixedDateString));
        }
        return fixedDate;
    }

    /**
     * Convert the new form of Person back to its previous form
     */
    public void runReverse() throws IOException {
        run(true);
    }

}
