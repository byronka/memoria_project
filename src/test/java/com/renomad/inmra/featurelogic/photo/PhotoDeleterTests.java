package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.utils.IFileDeleteWrapper;
import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static com.renomad.minum.testing.TestFramework.*;

/**
 * This is a test of {@link PhotoDeleter} focusing on the edge cases
 */
public class PhotoDeleterTests {

    private TestLogger logger;
    private PhotoDeleter photoDeleter;

    @Before
    public void init() {
        Context context = buildTestingContext("photo_deleter_tests");
        logger = (TestLogger) context.getLogger();
        IFileDeleteWrapper fileDeleteWrapper = path -> {
            throw new IOException("foo foo did a foo");
        };
        photoDeleter = new PhotoDeleter(logger, fileDeleteWrapper);
    }

    /**
     * If an {@link IOException} is thrown when deleting a photo,
     * we expect a log message to be written.
     */
    @Test
    public void testDeletingPhoto_EdgeCase_IOException() {
        photoDeleter.deletePhoto(Path.of("."), "bar");

        assertFalse(logger.findFirstMessageThatContains("while bar was deleting photo at").isEmpty());
    }

    /**
     * If the file does not exist, simply nothing happens
     */
    @Test
    public void testDeletingPhoto_EdgeCase_FileNotExists() {
        // trying deleting a non-existent file
        photoDeleter.deletePhoto(Path.of("foo"), "bar");
        assertTrue(true, "if we got here without an exception, all is well.");
    }
}
