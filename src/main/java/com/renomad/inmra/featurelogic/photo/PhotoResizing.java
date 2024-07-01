package com.renomad.inmra.featurelogic.photo;

import com.renomad.minum.state.Context;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.queue.ActionQueue;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * This class is responsible for resizing photos.
 * <br>
 * Our typical use case is to convert new incoming photos
 * to thumbnail-size and medium-size (1200) jpg images.
 */
public class PhotoResizing {

    private final ActionQueue photoResizingQueue;
    private final ILogger logger;

    public PhotoResizing(Context context) {
        photoResizingQueue = new ActionQueue("photo_resizing", context).initialize();
        logger = context.getLogger();
    }

    /**
     * The typical way this class is used is by adding an inputStream of
     * an image here.  It will be processed later, once its turn comes up
     * in the {@link ActionQueue}
     */
    public void addConversionToQueue(InputStream inputStream, int size, File file) {
        this.photoResizingQueue.enqueue(
                "enqueue an image for resizing and save to " + file.getName(),
                () -> {
                    logger.logDebug(() -> String.format("%s added to queue for photo resizing", file.getName()));
                    StopwatchUtils stopwatchUtils = new StopwatchUtils();
                    try {
                        var timer = stopwatchUtils.startTimer();
                        writeImageFile(inputStream, size, file, logger);
                        logger.logDebug(() ->
                                String.format("Resized to max of %d and wrote %s to %s in %d milliseconds",
                                        size,
                                        file.getName(),
                                        file,
                                        timer.stopTimer()
                                ));
                        inputStream.close();
                    } catch (InvalidPhotoException ex) {
                        logger.logDebug(ex::getMessage);
                    } catch (IOException e) {
                        logger.logAsyncError(() -> "Error while converting image: " + file + ". Error: " + e.getMessage());
                    }
                });
    }

    public static void writeImageFile(
            InputStream inputStream,
            int size,
            File file,
            ILogger logger) throws IOException {
        BufferedImage img = ImageIO.read(inputStream);
        // if img is null, that is because ImageIO was not able to read the incoming bytes as an image
        // type.  Since ImageIO is pretty good at reading images, we'll just assume that any time we
        // get back null we will just ignore what was sent and log a mild complaint.
        if (img == null) {
            throw new InvalidPhotoException(String.format("Unable to read incoming data as an image for %s", file));
        }
        logger.logDebug(() -> String.format("resizing %s to maximum of %s pixels on either dimension", file.getName(), size));
        BufferedImage bufferedImage = progressiveScaling(img, size);
        logger.logDebug(() -> String.format("writing %s to disk at %s", file.getName(), file));
        writeJpegImageFile(bufferedImage, file);
    }

    /**
     * This method encapsulates the code for writing an image to a JPEG
     * file.
     */
    static void writeJpegImageFile(BufferedImage img, File file) throws IOException {
        ImageWriter jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam();
        jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpgWriteParam.setCompressionQuality(0.35f);

        try (ImageOutputStream outputStream = new FileImageOutputStream(file)) {
            jpgWriter.setOutput(outputStream);
            BufferedImage imgWithAlphaRemoved = removeAlphaChannel(img);
            IIOImage outputImage = new IIOImage(imgWithAlphaRemoved, null, null);
            jpgWriter.write(null, outputImage, jpgWriteParam);
            jpgWriter.dispose();
        }
    }

    /**
     * If the image has an alpha channel, remove it.  Cannot save a JPEG
     * with an alpha channel.
     */
    private static BufferedImage removeAlphaChannel(BufferedImage img) {
        if (!img.getColorModel().hasAlpha()) {
            return img;
        }

        BufferedImage target = new BufferedImage(
                img.getWidth(), img.getHeight(),  BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(img, 0, 0, null);
        g.dispose();

        return target;
    }

    /**
     * Handles looping the {@link #scale(BufferedImage, Double)} method appropriately
     * for the target size of an image.
     * <br>
     * Only resizes smaller
     * @param longestSideLength if our goal is a box with sides of this length, the
     *                          image will fit inside this box after resizing.
     */
    static BufferedImage progressiveScaling(BufferedImage img, Integer longestSideLength) {
        if (img == null) {
            return null;
        }

        int w = img.getWidth();
        int h = img.getHeight();

        // if the image is smaller than "longestSideLength", no need
        // to scale it down (and we don't want to scale it up - just
        // leave it as-is)
        if (w < longestSideLength && h < longestSideLength) {
            return img;
        }

        double ratio = h > w ?
                longestSideLength.doubleValue() / h :
                longestSideLength.doubleValue() / w;

        //Multi Step Rescale operation
        //This technique is described in Chris Campbell’s blog The Perils of
        // Image.getScaledInstance(). As Chris mentions, when downscaling to something
        // less than factor 0.5, you get the best result by doing multiple downscaling with a
        // minimum factor of 0.5 (in other words: each scaling operation should scale to
        // maximum half the size).
        while (ratio < 0.5) {
            img = scale(img, 0.5);
            w = img.getWidth();
            h = img.getHeight();
            ratio = h > w ?
                    longestSideLength.doubleValue() / h :
                    longestSideLength.doubleValue() / w;
        }
        return scale(img, ratio);
    }

    /**
     * This method wraps the {@link Graphics2D} code that scales
     * an image in one shot.  Note that scaling an image down
     * by more than half will result in graphics artifacts.
     */
    private static BufferedImage scale(BufferedImage img, Double ratio) {
        int dWidth = ((Double) (img.getWidth() * ratio)).intValue();
        int dHeight = ((Double) (img.getHeight() * ratio)).intValue();
        BufferedImage scaledImage = new BufferedImage(dWidth, dHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = scaledImage.createGraphics();
        graphics2D.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics2D.drawImage(img, 0, 0, dWidth, dHeight, null);
        graphics2D.dispose();
        return scaledImage;
    }
}
