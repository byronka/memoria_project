package com.renomad.inmra.migrations;

import com.renomad.minum.logging.ILogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

public class Migration9 {

    private final ILogger logger;
    private final Path dbDirectory;

    /**
     * This migration adds "lastModified" - a date which
     * is the last time and date the content was updated.
     */
    public Migration9(Path dbDirectory, ILogger logger) {
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

        // get the current date and time
        Instant now = Instant.now();


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
                        tokens.get(4),
                        tokens.get(5),
                        tokens.get(6),
                        tokens.get(7),
                        tokens.get(8),
                        tokens.get(9),
                        tokens.get(10),
                        tokens.get(11),
                        tokens.get(12),
                        tokens.get(13),
                        now);
                Files.writeString(personFilePath, data);
            } else {
                String data = serializeHelper(
                        Long.parseLong(tokens.get(0)),
                        UUID.fromString(tokens.get(1)),
                        tokens.get(2),
                        tokens.get(3),
                        tokens.get(4),
                        tokens.get(5),
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
