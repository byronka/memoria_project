package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.MyThread;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static com.renomad.inmra.featurelogic.photo.PhotoResizing.writeImageFile;
import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.buildTestingContext;

public class PhotoConversionTests {

    private static PhotoResizing photoResizing;
    private static ILogger logger;
    private static IFileUtils fileUtils;

    @BeforeClass
    public static void initClass() throws IOException {
        var context = buildTestingContext("unit_tests");
        logger = context.getLogger();
        MemoriaContext memoriaContext = MemoriaContext.buildMemoriaContext(context);
        fileUtils = memoriaContext.fileUtils();
        fileUtils.makeDirectory(Path.of("target/testing_image_conversion/"));
        photoResizing = new PhotoResizing(context);
    }

    /**
     * A pure happy-path test of converting a photo.  Just useful
     * as a handle into the conversion code.
     */
    @Test
    public void testConvertPhoto() throws IOException {
        File thumbnailFile = new File("target/testing_image_conversion/dad_in_uniform_converted_thumbnail.jpg");
        thumbnailFile.delete();
        File file = new File("src/test/resources/images/dad_in_uniform.jpg");
        InputStream targetStream = new FileInputStream(file);

        writeImageFile(
                targetStream,
                150,
                thumbnailFile,
                logger);

        BufferedImage img = ImageIO.read(thumbnailFile);
        assertEquals(img.getHeight(), 149);
        assertEquals(img.getWidth(), 150);
    }

    /**
     * Similar to {@link #testConvertPhoto} but converts a .png file
     */
    @Test
    public void testConvertPhotoPng() throws IOException {
        File thumbnailFile = new File("target/testing_image_conversion/bessie_thumbnail.jpg");
        thumbnailFile.delete();
        File file = new File("src/test/resources/images/bessie.png");
        InputStream targetStream = new FileInputStream(file);

        writeImageFile(
                targetStream,
                150,
                thumbnailFile,
                logger);

        BufferedImage img = ImageIO.read(thumbnailFile);
        assertEquals(img.getHeight(), 150);
        assertEquals(img.getWidth(), 134);
    }

    /**
     * A bit more involved of a test - this resizes the image
     * inside an {@link com.renomad.minum.queue.ActionQueue}, so we
     * have to wait a bit until the file shows up.
     */
    @Test
    public void testConvertPhotoInQueue() throws IOException {
        // a path for where we expect the converted file to eventually show up
        Path mediumFile = Path.of("target/testing_image_conversion/dad_in_uniform_converted_medium.jpg");
        // clean out any existing file before our test
        try {
            Files.delete(mediumFile);
        } catch (NoSuchFileException ex) {
            // no worries.  If already gone, just keep going.
        }
        // the file we will convert
        File file = new File("src/test/resources/images/dad_in_uniform.jpg");
        InputStream targetStream = new FileInputStream(file);

        photoResizing.addConversionToQueue(targetStream, 600, mediumFile.toFile());

        for (int i = 0; i < 5; i++) {
            if (Files.isRegularFile(mediumFile)) {
                break;
            }
            MyThread.sleep(100);
        }
        BufferedImage img = ImageIO.read(mediumFile.toFile());
        assertEquals(img.getHeight(), 599);
        assertEquals(img.getWidth(), 600);
    }

    @Test
    public void testConvertPhotoInQueue_WithPng() throws IOException {
        // a path for where we expect the converted file to eventually show up
        Path mediumFile = Path.of("target/testing_image_conversion/bessie_medium.jpg");
        // clean out any existing file before our test
        try {
            Files.delete(mediumFile);
        } catch (NoSuchFileException ex) {
            // no worries.  If already gone, just keep going.
        }
        // the file we will convert
        File file = new File("src/test/resources/images/bessie.png");
        InputStream targetStream = new FileInputStream(file);

        photoResizing.addConversionToQueue(targetStream, 1200, mediumFile.toFile());

        for (int i = 0; i < 5; i++) {
            if (Files.isRegularFile(mediumFile)) {
                break;
            }
            MyThread.sleep(100);
        }
        BufferedImage img = ImageIO.read(mediumFile.toFile());
        assertEquals(img.getHeight(), 277);
        assertEquals(img.getWidth(), 249);
    }

    /**
     * If we send a file that is not an image
     */
    @Test
    public void testConvertPhotoInQueue_WithNonImage() throws IOException {
        // the file we will convert
        File file = new File("src/README.md");
        InputStream targetStream = new FileInputStream(file);

        photoResizing.addConversionToQueue(targetStream, 1200,  new File("foo"));
        MyThread.sleep(500);
    }

}
