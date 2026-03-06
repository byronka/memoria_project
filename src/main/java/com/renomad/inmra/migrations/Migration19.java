package com.renomad.inmra.migrations;

import com.renomad.minum.logging.ILogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

/**
 * Adds a field for the poster image on a video in the database
 */
public class Migration19 {

    private final ILogger logger;
    private final Path dbDirectory;

    public Migration19(Path dbDirectory, ILogger logger) {
        this.dbDirectory = dbDirectory;
        this.logger = logger;
    }

    public void run() throws IOException {
        run(false);
    }

    public void runReverse() throws IOException {
        run(true);
    }

    private void run(boolean runReverse) throws IOException {
        Path videosDirectory = dbDirectory.resolve("videos");

        String runMessage =  "Running migration19" + (runReverse ? " in reverse" : "");
        logger.logDebug(() -> runMessage);

        logger.logDebug(() -> "get all the paths (that is, all the files) in the directory");
        List<Path> files = getPaths(videosDirectory);

        logger.logDebug(() -> "for each one, adjust and then overwrite the file with the new adjusted content");
        for (var file : files) {

            List<String> tokens = deserializeHelper(Files.readString(file));

            logger.logDebug(() -> String.format("processing %s", file));

            // replace each file with the updated schema
            logger.logDebug(() -> "Writing file for " + file);

            if (! runReverse) {
                String data = serializeHelper(
                        Long.parseLong(tokens.get(0)),
                        tokens.get(1),
                        tokens.get(2),
                        tokens.get(3),
                        ""
                );
                Files.writeString(file, data);
            } else {
                String data = serializeHelper(
                        Long.parseLong(tokens.get(0)),
                        tokens.get(1),
                        tokens.get(2),
                        tokens.get(3)
                );
                Files.writeString(file, data);
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
