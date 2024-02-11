package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.IFileWriteStringWrapper;
import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.MyThread;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.renomad.inmra.featurelogic.photo.PhotoService.determineSuffixByContentType;
import static com.renomad.minum.testing.TestFramework.*;

public class PhotoServiceTests {

    private static TestLogger logger;
    private static Path thumbnailPhotos;
    private static Path mediumPhotos;
    private static Path originalPhotos;

    @BeforeClass
    public static void init() throws IOException {
        Context context = buildTestingContext("PhotoServiceTests");
        logger = (TestLogger)context.getLogger();
        FileUtils fileUtils = context.getFileUtils();

        thumbnailPhotos = Path.of("target/simple_db_for_photo_service_tests/photo_files_thumbnail");
        mediumPhotos = Path.of("target/simple_db_for_photo_service_tests/photo_files_medium");
        originalPhotos = Path.of("target/simple_db_for_photo_service_tests/photo_files_original");

        fileUtils.deleteDirectoryRecursivelyIfExists(thumbnailPhotos, logger);
        fileUtils.deleteDirectoryRecursivelyIfExists(mediumPhotos, logger);
        fileUtils.deleteDirectoryRecursivelyIfExists(originalPhotos, logger);

        fileUtils.makeDirectory(thumbnailPhotos);
        fileUtils.makeDirectory(mediumPhotos);
        fileUtils.makeDirectory(originalPhotos);
    }

    /**
     * If the provided mime type is not one we recognize, make that clearer
     * for future maintenance by setting its suffix to "unfamiliar_mime_type"
     */
    @Test
    public void testUnfamiliarMimeType() {
        String suffix = determineSuffixByContentType(List.of("foo"), logger);
        assertEquals(suffix, ".unfamiliar_mime_type");
    }

    /**
     * If no mime type is provided, we'll set the suffix to "unknown"
     */
    @Test
    public void testMissingMimeType() {
        String suffix = determineSuffixByContentType(List.of(), logger);
        assertEquals(suffix, ".unknown");
    }

    /**
     * If we recognize the mime type, set the suffix appropriately
     */
    @Test
    public void testKnownMimeType() {
        String suffix = determineSuffixByContentType(List.of("image/png"), logger);
        assertEquals(suffix, ".png");
        suffix = determineSuffixByContentType(List.of("image/jpeg"), logger);
        assertEquals(suffix, ".jpg");
    }

    /**
     * This tests a function that will check if files exist in a few
     * directories before moving on.
     */
    @Test
    public void testWaitUntilPhotosConverted() throws IOException {
        Path dbDir = Path.of("target/simple_db_for_photo_service_tests");
        Files.writeString(thumbnailPhotos.resolve("abc123.jpg"), "testing...");
        Files.writeString(mediumPhotos.resolve("abc123.jpg"), "testing...");
        Files.writeString(originalPhotos.resolve("abc123.jpg"), "testing...");
        // let the disk settle down
        MyThread.sleep(50);
        StopwatchUtils stopwatchUtils = new StopwatchUtils();
        stopwatchUtils.startTimer();

        PhotoService.photoWaiter("abc123.jpg", 10, 50, dbDir, logger);

        long timeTaken = stopwatchUtils.stopTimer();
        // the maximum time to wait would be 10 * 50 = 500, we should get here before that
        assertTrue(timeTaken < 100);
    }

    /**
     * Here, the method never finds the photos
     */
    @Test
    public void testWaitUntilPhotosConverted_NegativeCase() {
        Path dbDir = Path.of("target/simple_db_for_photo_service_tests");
        StopwatchUtils stopwatchUtils = new StopwatchUtils();
        stopwatchUtils.startTimer();

        // since "does_not_exist" will never show up in the directories, this will
        // bail after waiting.
        PhotoService.photoWaiter("does_not_exist", 4, 30, dbDir, logger);

        long timeTaken = stopwatchUtils.stopTimer();
        assertTrue(timeTaken > 100);
    }

    /**
     * If we fail to build the directories, should thrown an exception
     */
    @Test
    public void testBuildingNecessaryDirectories_EdgeCase_CreationFails() {
        IFileUtils fileUtils = buildMockFileUtilsThatThrows();
        Path path = Path.of("");

        PhotoService.buildNecessaryDirectories(fileUtils, path, path, path, path, path, path, path, logger);

        assertTrue(logger.findFirstMessageThatContains("Hi, the directory creation failed").length() > 0);
    }

    /**
     * A {@link com.renomad.inmra.utils.FileUtils} that always throws
     * an exception from {@link com.renomad.inmra.utils.FileUtils#makeDirectory(Path)}
     */
    private static IFileUtils buildMockFileUtilsThatThrows() {
        return new IFileUtils() {

            @Override
            public void makeDirectory(Path path) throws IOException {
                throw new IOException("Hi, the directory creation failed.");
            }

            @Override
            public String readTemplate(String path) {
                return null;
            }
        };
    }

    @Test
    public void testStoringPhotoToAudit() {
        IFileWriteStringWrapper fileWriteStringWrapper = (path, csq, options) -> {
            throw new IOException("foo foo did a foo");
        };
        PhotoService.storePhotoAudit(
                Photograph.EMPTY,
                Path.of(""),
                logger,
                fileWriteStringWrapper).run();
        assertTrue(logger.findFirstMessageThatContains("foo foo did a foo").length() > 0);
    }
}
