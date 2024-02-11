package com.renomad.inmra.migrations;

import com.renomad.inmra.featurelogic.persons.Date;
import com.renomad.minum.logging.ILogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

public class Migration1 {

    private final ILogger logger;
    private final Path dbDirectory;

    /**
     * This migration grabs the born and died dates from the person_files data
     * and puts it into the persons database
     */
    public Migration1(Path dbDirectory, ILogger logger) {
        this.dbDirectory = dbDirectory;
        this.logger = logger;
    }

    /**
     * Convert the Person to a new form
     */
    public void run() throws IOException {
        Path personsDirectory = dbDirectory.resolve("persons");
        if (!Files.exists(personsDirectory)) return;

        logger.logDebug(() -> "Running migration1");

        logger.logDebug(() -> "get all the paths (that is, all the files) in the persons directory");
        List<Path> listPersonFiles;
        try (Stream<Path> files = Files.walk(personsDirectory)) {
            listPersonFiles = files.filter(Files::isRegularFile)
                    .filter(x -> !x.getFileName().toString().equalsIgnoreCase("index.ddps"))
                    .toList();
        }

        logger.logDebug(() -> "for each one, upgrade and then overwrite the file with the new upgraded content");
        for (var p : listPersonFiles) {
            logger.logDebug(() -> "processing " + p);
            final var personTokens = deserializeHelper(Files.readString(p));
            // the "personFile" is the fat document data for a person, as opposed to the
            // lean metadata in "person"
            Path personFileData = dbDirectory.resolve("person_files").resolve(personTokens.get(1));
            final var personFileTokens = deserializeHelper(Files.readString(personFileData));
            logger.logDebug(() -> "Writing file for " + p);
            Files.writeString(p,
                    serializeHelper(
                            personTokens.get(0), // index
                            personTokens.get(1),  // UUID identifier
                            personFileTokens.get(3), // person's name
                            convertDate(personFileTokens.get(4)), // birthdate
                            convertDate(personFileTokens.get(5)))); // deathdate
        }
    }

    private Date convertDate(String dateValue) {
        Date date;
        if (dateValue == null || dateValue.isBlank()) {
            date = Date.EMPTY;
        } else {
            date = Date.extractDate(dateValue);
        }
        return date;
    }

    /**
     * Convert the new form of Person back to its previous form
     */
    public void runReverse() throws IOException {
        // get all the paths (that is, all the files) in the persons directory
        List<Path> listPersonFiles = Files.walk(dbDirectory.resolve("persons"))
                .filter(Files::isRegularFile)
                .filter(x -> ! x.getFileName().toString().equalsIgnoreCase("index.ddps"))
                .toList();
        // for each one, downgrade and then overwrite the file with the new downgraded content
        for (var p : listPersonFiles) {
            final var tokens = deserializeHelper(Files.readString(p));
            Files.writeString(p, serializeHelper(Long.parseLong(tokens.get(0)), UUID.fromString(tokens.get(1))));
        }
    }

}
