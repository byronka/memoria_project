package com.renomad.inmra.migrations;

import com.renomad.inmra.featurelogic.photo.PhotoResizing;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.FileUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class Migration14 {

    private final ILogger logger;
    private final Path dbDirectory;
    private final PhotoResizing photoResizing;
    private final FileUtils fileUtils;

    /**
     * This migration converts images from the photo archive to
     * JPEG files in their proper locations.
     * <br>
     * This is a one-way operation - it deletes all the content from three
     * directories - photo_files_thumbnail, photo_files_medium, photo_files_original.
     * <br>
     * It is not possible to run this migration in reverse, simply because we
     * have no Java way to write webp files (currently, and because running this
     * migration in reverse is not sufficiently important to warrant bringing in
     * an extra dependency for something that is unlikely to happen and can be
     * done using ImageMagick externally)
     *
     */
    public Migration14(Path dbDirectory, ILogger logger, Context context) {
        this.dbDirectory = dbDirectory;
        this.logger = logger;
        this.photoResizing = new PhotoResizing(context);
        this.fileUtils = new FileUtils(logger, context.getConstants());
    }

    public void run() throws IOException {
        Path photoArchiveDirectory = dbDirectory.resolve("photo_archive");
        Path photoThumbnailDirectory = dbDirectory.resolve("photo_files_thumbnail");
        Path photoMediumDirectory = dbDirectory.resolve("photo_files_medium");
        Path photoOriginalDirectory = dbDirectory.resolve("photo_files_original");

        if (!Files.exists(photoArchiveDirectory)) return;

        // delete the existing photo directories - thumbnail, medium, and original
        fileUtils.deleteDirectoryRecursivelyIfExists(photoThumbnailDirectory);
        fileUtils.deleteDirectoryRecursivelyIfExists(photoMediumDirectory);
        fileUtils.deleteDirectoryRecursivelyIfExists(photoOriginalDirectory);

        // build new directories
        fileUtils.makeDirectory(photoThumbnailDirectory);
        fileUtils.makeDirectory(photoMediumDirectory);
        fileUtils.makeDirectory(photoOriginalDirectory);

        logger.logDebug(() -> "get all the paths (that is, all the photos) in the photos archive directory");
        List<Path> listPhotos = getPaths(photoArchiveDirectory);

        logger.logDebug(() -> "for each one, convert and write to disk appropriately");
        for (var photoPath : listPhotos) {
            String fileName = photoPath.getFileName().toString();
            // if this file is not convertable, move to the next file
            boolean isConvertable = fileName.contains(".png") || fileName.contains(".jpg") || fileName.contains(".jpeg");
            if (!isConvertable) continue;

            String convertedName = convertFilenameToJpeg(fileName);

            photoResizing.addConversionToQueue(new FileInputStream(photoPath.toFile()), 150, photoThumbnailDirectory.resolve(convertedName).toFile());
            photoResizing.addConversionToQueue(new FileInputStream(photoPath.toFile()), 1200, photoMediumDirectory.resolve(convertedName).toFile());
            photoResizing.addConversionToQueue(new FileInputStream(photoPath.toFile()), 3000, photoOriginalDirectory.resolve(convertedName).toFile());
        }
    }

    static String convertFilenameToJpeg(String fileName) {
        return fileName.replaceFirst("\\..+$", ".jpg");
    }

    private static List<Path> getPaths(Path myPath) throws IOException {
        try (Stream<Path> files = Files.walk(myPath)) {
            return files.filter(Files::isRegularFile)
                    .toList();
        }
    }

}
