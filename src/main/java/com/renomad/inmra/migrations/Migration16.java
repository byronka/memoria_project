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

public class Migration16 {

    private final ILogger logger;
    private final Path dbDirectory;

    /**
     * This migration adds a few more stats to the metrics database
     */
    public Migration16(Path dbDirectory, ILogger logger) {
        this.dbDirectory = dbDirectory;
        this.logger = logger;
    }

    public void run() throws IOException {
        run(false);
    }

    /**
     * Convert the new form of metrics back to its previous form
     */
    public void runReverse() throws IOException {
        run(true);
    }

    private void run(boolean runReverse) throws IOException {
        Path metricsDirectory = dbDirectory.resolve("person_metrics");
        if (!Files.exists(metricsDirectory)) {
            // if there are no metrics to convert, then we're all good, the first metrics
            // will get created with a proper form.
            logger.logDebug(() -> "person_metrics directory does not exist.  Exiting from migration");
            return;
        }

        String runMessage =  "Running migration16" + (runReverse ? " in reverse" : "");
        logger.logDebug(() -> runMessage);

        logger.logDebug(() -> "get all the paths (that is, all the files) in the person_metrics directory");
        List<Path> listMetricsFiles = getPaths(metricsDirectory);

        logger.logDebug(() -> "for each one, adjust and then overwrite the file with the new adjusted content");
        for (var metricsFilePath : listMetricsFiles) {

            List<String> tokens = deserializeHelper(Files.readString(metricsFilePath));

            logger.logDebug(() -> String.format("processing %s", metricsFilePath));

            // replace each file with the updated schema
            logger.logDebug(() -> "Writing file for " + metricsFilePath);

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
                        false, // new default value for whether has a headshot
                        0,     // new default value for family tree size
                        0      // new default value for notes size in chars
                );
                Files.writeString(metricsFilePath, data);
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
                        tokens.get(21)
                );
                Files.writeString(metricsFilePath, data);
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
