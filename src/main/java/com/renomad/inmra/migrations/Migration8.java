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

public class Migration8 {

    private final ILogger logger;
    private final Path dbDirectory;

    /**
     * This migration updates the date fields (birth and death) in a
     * PersonFile to actually have proper values for persistence.  Instead
     * of sloppiness like having empty strings, we'll specify the toString()
     * of Date.EMPTY, and such.
     */
    public Migration8(Path dbDirectory, ILogger logger) {
        this.dbDirectory = dbDirectory;
        this.logger = logger;
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

    private void run(boolean runReverse) throws IOException {
        Path personFilesDirectory = dbDirectory.resolve("person_files");
        if (!Files.exists(personFilesDirectory)) return;

        logger.logDebug(() -> "get all the paths (that is, all the files) in the person_files directory");
        List<Path> listPersonFiles = getPaths(personFilesDirectory);

        logger.logDebug(() -> "for each one, adjust and then overwrite the file with the new adjusted content");
        for (var personFilePath : listPersonFiles) {

            List<String> tokens = deserializeHelper(Files.readString(personFilePath));

            logger.logDebug(() -> String.format("processing %s - %s", personFilePath, tokens.get(3)));

            // replace each file with the updated schema
            logger.logDebug(() -> "Writing file for " + personFilePath);

            if (! runReverse) {
                String data = serializeHelper(
                        Long.parseLong(tokens.get(0)),
                        UUID.fromString(tokens.get(1)),
                        tokens.get(2),
                        tokens.get(3),
                        Date.extractDate(tokens.get(4)).toString(),
                        Date.extractDate(tokens.get(5)).toString(),
                        tokens.get(6),
                        tokens.get(7),
                        tokens.get(8),
                        tokens.get(9),
                        tokens.get(10),
                        tokens.get(11),
                        tokens.get(12),
                        tokens.get(13));
                Files.writeString(personFilePath, data);
            } else {
                String data = serializeHelper(
                        Long.parseLong(tokens.get(0)),
                        UUID.fromString(tokens.get(1)),
                        tokens.get(2),
                        tokens.get(3),
                        Date.fromString(tokens.get(4)).toHtmlString(),
                        Date.fromString(tokens.get(5)).toHtmlString(),
                        tokens.get(6),
                        tokens.get(7),
                        tokens.get(8),
                        tokens.get(9),
                        tokens.get(10),
                        tokens.get(11),
                        tokens.get(12),
                        tokens.get(13));
                Files.writeString(personFilePath, data);
            }
        }
    }

    private static List<Path> getPaths(Path myPath) throws IOException {
        try (Stream<Path> files = Files.walk(myPath)) {
            return files.filter(Files::isRegularFile)
                    .filter(x -> !x.getFileName().toString().equalsIgnoreCase("index.ddps"))
                    .toList();
        }
    }

}
