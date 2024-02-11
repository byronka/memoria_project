package com.renomad.inmra.migrations;

import com.renomad.minum.logging.ILogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

public class Migration13 {

    private final ILogger logger;
    private final Path dbDirectory;

    /**
     * This migration runs a search and replace across all the Photos,
     * converting .png to .jpg.  We only provide output of jpeg now.
     * <br>
     * The reason for this change is because we are switching to use image
     * conversion code built into our system instead of using an external
     * converter.  This means one less dependency, simplifies things.
     * But it also means that some of our endpoints change behavior - so
     * we need the image suffix to all be JPEG.
     */

    public Migration13(Path dbDirectory, ILogger logger) {
        this.dbDirectory = dbDirectory;
        this.logger = logger;
    }

    public void run() throws IOException {
        Path photosDirectory = dbDirectory.resolve("photos");
        if (!Files.exists(photosDirectory)) return;

        logger.logDebug(() -> "get all the paths (that is, all the files) in the photos directory");
        List<Path> listPhotos = getPaths(photosDirectory);

        logger.logDebug(() -> "for each one, adjust and then overwrite the file with the new adjusted content");
        for (var photoPath : listPhotos) {

            List<String> tokens = deserializeHelper(Files.readString(photoPath));

            logger.logDebug(() -> String.format("processing %s - %s", photoPath, tokens.get(1)));

            // replace each file with the updated schema
            logger.logDebug(() -> "Writing file for " + photoPath);

            String data = serializeHelper(
                Long.parseLong(tokens.get(0)),
                tokens.get(1).replace(".png", ".jpg"),
                tokens.get(2),
                tokens.get(3));
            Files.writeString(photoPath, data);
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
