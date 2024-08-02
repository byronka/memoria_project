package com.renomad.inmra.migrations;

import com.renomad.minum.logging.ILogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

public class Migration12 {

    private final ILogger logger;
    private final Path dbDirectory;

    /**
     * This migration runs a search and replace across all the PersonFiles,
     * converting .png to .jpg.  We only provide output of jpeg now.
     * <br>
     * The reason for this change is because we are switching to use image
     * conversion code built into our system instead of using an external
     * converter.  This means one less dependency, simplifies things.
     * But it also means that some of our endpoints change behavior - so
     * we need the image suffix to all be JPEG.
     */

    public Migration12(Path dbDirectory, ILogger logger) {
        this.dbDirectory = dbDirectory;
        this.logger = logger;
    }

    public void run() throws IOException {
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

            String data = serializeHelper(
                Long.parseLong(tokens.get(0)),
                UUID.fromString(tokens.get(1)),
                tokens.get(2).replace(".png", ".jpg"),
                tokens.get(3),
                tokens.get(4),
                tokens.get(5),
                tokens.get(6).replace(".png", ".jpg"),
                tokens.get(7).replace(".png", ".jpg"),
                tokens.get(8).replace(".png", ".jpg"),
                tokens.get(9).replace(".png", ".jpg"),
                tokens.get(10).replace(".png", ".jpg"),
                tokens.get(11),
                tokens.get(12),
                tokens.get(13),
                tokens.get(14),
                tokens.get(15));
            Files.writeString(personFilePath, data);
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
