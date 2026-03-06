package com.renomad.inmra.migrations;

import com.renomad.inmra.featurelogic.photo.Photograph;
import com.renomad.inmra.featurelogic.photo.Video;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.SearchUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

public class Migration15 {

    private final AbstractDb<Photograph> photographDb;
    private final AbstractDb<Video> videoDb;
    private final ILogger logger;
    private final Path dbDirectory;

    /**
     * This migration adds the UUID of a photo to the PhotoToPerson database, and
     * likewise the UUID of a video to the VideoToPerson database, so
     * we can more efficiently / easily track who all has access to a particular file.
     */
    public Migration15(Path dbDirectory, ILogger logger, Context context) {
        this.dbDirectory = dbDirectory;
        this.photographDb = new Db<>(dbDirectory.resolve("photos"), context, Photograph.EMPTY);
        this.videoDb = new Db<>(dbDirectory.resolve("videos"), context, Video.EMPTY);
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
        convertPhoto(runReverse);
        convertVideo(runReverse);
    }

    private void convertPhoto(boolean runReverse) throws IOException {
        Path photoToPersonDirectory = dbDirectory.resolve("photo_to_person");
        if (!Files.exists(photoToPersonDirectory)) return;

        logger.logDebug(() -> "get all the paths (that is, all the files) in the photo_to_person directory");
        List<Path> listOfFiles = getPaths(photoToPersonDirectory);

        logger.logDebug(() -> "for each one, adjust and then overwrite the file with the new adjusted content");
        for (Path filePath : listOfFiles) {

            List<String> tokens = deserializeHelper(Files.readString(filePath));

            logger.logDebug(() -> String.format("processing %s", filePath));

            long photoIndex = Long.parseLong(tokens.get(1));

            Photograph photograph = SearchUtils.findExactlyOne(photographDb.values().stream(), x -> x.getIndex() == photoIndex);
            String photoUrl = "";
            if (photograph != null) {
                photoUrl = photograph.getPhotoUrl();
            }

            if (!runReverse) {
                String data = serializeHelper(
                        Long.parseLong(tokens.get(0)),
                        Long.parseLong(tokens.get(1)),
                        Long.parseLong(tokens.get(2)),
                        photoUrl);
                Files.writeString(filePath, data);
            } else {
                String data = serializeHelper(
                        Long.parseLong(tokens.get(0)),
                        Long.parseLong(tokens.get(1)),
                        Long.parseLong(tokens.get(2)));
                Files.writeString(filePath, data);
            }
        }
    }

    private void convertVideo(boolean runReverse) throws IOException {
        Path videoToPersonDirectory = dbDirectory.resolve("video_to_person");
        if (!Files.exists(videoToPersonDirectory)) return;

        logger.logDebug(() -> "get all the paths (that is, all the files) in the video_to_person directory");
        List<Path> listOfFiles = getPaths(videoToPersonDirectory);

        logger.logDebug(() -> "for each one, adjust and then overwrite the file with the new adjusted content");
        for (Path filePath : listOfFiles) {

            List<String> tokens = deserializeHelper(Files.readString(filePath));

            logger.logDebug(() -> String.format("processing %s", filePath));

            long videoIndex = Long.parseLong(tokens.get(1));

            Video video = SearchUtils.findExactlyOne(videoDb.values().stream(), x -> x.getIndex() == videoIndex);
            String videoUrl = "";
            if (video != null) {
                videoUrl = video.getVideoUrl();
            }

            if (!runReverse) {
                String data = serializeHelper(
                        Long.parseLong(tokens.get(0)),
                        Long.parseLong(tokens.get(1)),
                        Long.parseLong(tokens.get(2)),
                        videoUrl);
                Files.writeString(filePath, data);
            } else {
                String data = serializeHelper(
                        Long.parseLong(tokens.get(0)),
                        Long.parseLong(tokens.get(1)),
                        Long.parseLong(tokens.get(2)));
                Files.writeString(filePath, data);
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
