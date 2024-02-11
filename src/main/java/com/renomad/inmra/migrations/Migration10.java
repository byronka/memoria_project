package com.renomad.inmra.migrations;

import com.renomad.minum.logging.ILogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

public class Migration10 {

    private final ILogger logger;
    private final Path dbDirectory;

    /**
     * This migration modifies the SessionId type to have
     * a date type of {@link Instant} instead of {@link java.time.ZonedDateTime}.
     * This is because Instant is intrinsically UTC.
     */
    public Migration10(Path dbDirectory, ILogger logger) {
        this.dbDirectory = dbDirectory;
        this.logger = logger;
    }


    public void run() throws IOException {
        run(false);

    }

    /**
     * Convert the new form of SessionId back to its previous form
     */
    public void runReverse() throws IOException {
        run(true);
    }

    private void run(boolean runReverse) throws IOException {
        Path sessionFilesDirectory = dbDirectory.resolve("sessions");
        if (!Files.exists(sessionFilesDirectory)) return;

        logger.logDebug(() -> "get all the paths (that is, all the files) in the sessions directory");
        List<Path> listSessions = getPaths(sessionFilesDirectory);

        logger.logDebug(() -> "for each one, adjust and then overwrite the file with the new adjusted content");
        for (var session : listSessions) {

            List<String> tokens = deserializeHelper(Files.readString(session));

            logger.logDebug(() -> String.format("processing session %d", Long.parseLong(tokens.get(0))));

            // replace each file with the updated schema
            logger.logDebug(() -> "Writing file for " + session);

            if (! runReverse) {
                String data = serializeHelper(
                        Long.parseLong(tokens.get(0)),
                        tokens.get(1),
                        ZonedDateTime.parse(Objects.requireNonNull(tokens.get(2))).toInstant(),
                        ZonedDateTime.parse(Objects.requireNonNull(tokens.get(3))).toInstant(),
                        Long.parseLong(tokens.get(4)));
                Files.writeString(session, data);
            } else {
                String data = serializeHelper(
                        Long.parseLong(tokens.get(0)),
                        tokens.get(1),
                        ZonedDateTime.ofInstant(Instant.parse(Objects.requireNonNull(tokens.get(2))), ZoneId.of("UTC")),
                        ZonedDateTime.ofInstant(Instant.parse(Objects.requireNonNull(tokens.get(3))), ZoneId.of("UTC")),
                        Long.parseLong(tokens.get(4)));
                Files.writeString(session, data);
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
