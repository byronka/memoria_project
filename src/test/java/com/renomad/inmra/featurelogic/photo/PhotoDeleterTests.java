package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.auth.User;
import com.renomad.inmra.utils.Auditor;
import com.renomad.inmra.utils.IFileDeleteWrapper;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
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
    private Auditor auditor;

    @Before
    public void init() {
        Context context = buildTestingContext("photo_deleter_tests");
        logger = (TestLogger) context.getLogger();
        IFileDeleteWrapper fileDeleteWrapper = path -> {
            throw new IOException("foo foo did a foo");
        };
        auditor = new Auditor(context);
        photoDeleter = new PhotoDeleter(logger, fileDeleteWrapper, auditor);
    }

    /**
     * If an {@link IOException} is thrown when deleting a photo,
     * we expect a log message to be written.
     */
    @Test
    public void testDeletingPhoto_EdgeCase_IOException() {
        var user = new User(1L, "bar", "", "");
        photoDeleter.deletePhoto(Path.of("."), user);

        assertFalse(logger.findFirstMessageThatContains("while bar was deleting photo at").isEmpty());
    }

    /**
     * If the file does not exist, simply nothing happens
     */
    @Test
    public void testDeletingPhoto_EdgeCase_FileNotExists() {
        // trying deleting a non-existent file
        var user = new User(1L, "bar", "", "");
        photoDeleter.deletePhoto(Path.of("foo"), user);
        assertTrue(true, "if we got here without an exception, all is well.");
    }
}
