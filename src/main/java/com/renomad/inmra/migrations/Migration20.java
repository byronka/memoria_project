package com.renomad.inmra.migrations;

import com.renomad.inmra.featurelogic.persons.Date;
import com.renomad.minum.logging.ILogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

/**
 * Add a value for the count of cousins to a person's metrics
 */
public class Migration20 {

    private final ILogger logger;
    private final Path dbDirectory;

    public Migration20(Path dbDirectory, ILogger logger) {
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
        Path metricsDirectory = dbDirectory.resolve("person_metrics");

        if (!metricsDirectory.toFile().exists()) {
            logger.logDebug(() -> "No person_metrics directory - skipping migration actions");
            return;
        }

        String runMessage =  "Running migration20" + (runReverse ? " in reverse" : "");
        logger.logDebug(() -> runMessage);

        logger.logDebug(() -> "get all the paths (that is, all the files) in the directory");
        List<Path> files = getPaths(metricsDirectory);

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
                        Integer.parseInt(tokens.get(3)),
                        Integer.parseInt(tokens.get(4)),
                        Integer.parseInt(tokens.get(5)),
                        Integer.parseInt(tokens.get(6)),
                        Integer.parseInt(tokens.get(7)),
                        Integer.parseInt(tokens.get(8)),
                        Integer.parseInt(tokens.get(9)),
                        Integer.parseInt(tokens.get(10)),
                        Integer.parseInt(tokens.get(11)),
                        Integer.parseInt(tokens.get(12)),
                        Integer.parseInt(tokens.get(13)),
                        Integer.parseInt(tokens.get(14)),
                        Integer.parseInt(tokens.get(15)),
                        Integer.parseInt(tokens.get(16)),
                        Integer.parseInt(tokens.get(17)),
                        Date.fromString(tokens.get(18)),
                        Date.fromString(tokens.get(19)),
                        tokens.get(20),
                        tokens.get(21),
                        Boolean.parseBoolean(tokens.get(22)),
                        Integer.parseInt(tokens.get(23)),
                        Integer.parseInt(tokens.get(24)),
                        Integer.parseInt(tokens.get(25)),
                        0
                );
                Files.writeString(file, data);
            } else {
                String data = serializeHelper(
                        Long.parseLong(tokens.get(0)),
                        tokens.get(1),
                        tokens.get(2),
                        Integer.parseInt(tokens.get(3)),
                        Integer.parseInt(tokens.get(4)),
                        Integer.parseInt(tokens.get(5)),
                        Integer.parseInt(tokens.get(6)),
                        Integer.parseInt(tokens.get(7)),
                        Integer.parseInt(tokens.get(8)),
                        Integer.parseInt(tokens.get(9)),
                        Integer.parseInt(tokens.get(10)),
                        Integer.parseInt(tokens.get(11)),
                        Integer.parseInt(tokens.get(12)),
                        Integer.parseInt(tokens.get(13)),
                        Integer.parseInt(tokens.get(14)),
                        Integer.parseInt(tokens.get(15)),
                        Integer.parseInt(tokens.get(16)),
                        Integer.parseInt(tokens.get(17)),
                        Date.fromString(tokens.get(18)),
                        Date.fromString(tokens.get(19)),
                        tokens.get(20),
                        tokens.get(21),
                        Boolean.parseBoolean(tokens.get(22)),
                        Integer.parseInt(tokens.get(23)),
                        Integer.parseInt(tokens.get(24)),
                        Integer.parseInt(tokens.get(25))
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
