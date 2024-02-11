package com.renomad.inmra.migrations;

import com.renomad.minum.logging.ILogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

public class Migration6 {

    private final ILogger logger;
    private final Path dbDirectory;

    /**
     * This migration updates SessionsId to have a {@link java.time.ZonedDateTime}
     * when its session will be deleted.
     */
    public Migration6(Path dbDirectory, ILogger logger) {
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
        Path sessionsDirectory = dbDirectory.resolve("sessions");
        if (!Files.exists(sessionsDirectory)) return;

        String runMessage =  "Running migration6" + (runReverse ? " in reverse" : "");
        logger.logDebug(() -> runMessage);

        logger.logDebug(() -> "get all the paths (that is, all the files) in the sessions directory");
        List<Path> listSessions = getPaths(sessionsDirectory);

        logger.logDebug(() -> "for each one, adjust and then overwrite the file with the new adjusted content");
        for (var sessionPath : listSessions) {

            List<String> tokens = deserializeHelper(Files.readString(sessionPath));

            logger.logDebug(() -> String.format("processing %s - index: %s", sessionPath, tokens.get(0)));

            // replace each file with the updated schema
            logger.logDebug(() -> "Writing file for " + sessionPath);

            if (! runReverse) {
                String data = serializeHelper(
                    Long.parseLong(tokens.get(0)),
                    tokens.get(1),
                    ZonedDateTime.parse(tokens.get(2)),
                    ZonedDateTime.parse(tokens.get(2)),
                    Long.parseLong(tokens.get(3)));
                Files.writeString(sessionPath, data);
            } else {
                String data = serializeHelper(
                        Long.parseLong(tokens.get(0)),
                        tokens.get(1),
                        ZonedDateTime.parse(tokens.get(2)),
                        Long.parseLong(tokens.get(4)));
                Files.writeString(sessionPath, data);
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
