package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.utils.IFileDeleteWrapper;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.StacktraceUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PhotoDeleter {

    private final ILogger logger;
    private final IFileDeleteWrapper fileDeleteWrapper;

    public PhotoDeleter(
            ILogger logger,
            IFileDeleteWrapper fileDeleteWrapper) {

        this.logger = logger;
        this.fileDeleteWrapper = fileDeleteWrapper;
    }

    public void deletePhoto(Path photoUrlPath, String username) {
        try {
            if (Files.exists(photoUrlPath)) {
                logger.logAudit(() -> String.format("%s is deleting the file at %s", username, photoUrlPath));
                fileDeleteWrapper.delete(photoUrlPath);
            }

        } catch (IOException ex) {
            String exception = StacktraceUtils.stackTraceToString(ex);
            logger.logAsyncError(() -> String.format("Error: while %s was deleting photo at %s: %s", username, photoUrlPath, exception));
        }
    }
}
