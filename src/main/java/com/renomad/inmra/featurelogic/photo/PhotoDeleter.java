package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.auth.User;
import com.renomad.inmra.utils.Auditor;
import com.renomad.inmra.utils.IFileDeleteWrapper;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.StacktraceUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PhotoDeleter {

    private final ILogger logger;
    private final IFileDeleteWrapper fileDeleteWrapper;
    private final Auditor auditor;

    public PhotoDeleter(
            ILogger logger,
            IFileDeleteWrapper fileDeleteWrapper,
            Auditor auditor) {

        this.logger = logger;
        this.fileDeleteWrapper = fileDeleteWrapper;
        this.auditor = auditor;
    }

    public void deletePhoto(Path photoUrlPath, User user) {
        try {
            if (Files.exists(photoUrlPath)) {
                auditor.audit(() -> String.format("%s is deleting the file at %s", user.getUsername(), photoUrlPath), user);
                fileDeleteWrapper.delete(photoUrlPath);
            }

        } catch (IOException ex) {
            String exception = StacktraceUtils.stackTraceToString(ex);
            logger.logAsyncError(() -> String.format("Error: while %s was deleting photo at %s: %s", user.getUsername(), photoUrlPath, exception));
        }
    }
}
