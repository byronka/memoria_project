package com.renomad.inmra.utils;

import com.renomad.inmra.auth.User;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.logging.ThrowingSupplier;
import com.renomad.minum.queue.ActionQueue;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.utils.UtilsException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static com.renomad.inmra.utils.FileUtils.badFilePathPatterns;
import static com.renomad.minum.utils.TimeUtils.getTimestampIsoInstant;

/**
 * This class contains methods for keeping a copy of auditable actions
 * close at hand for an indeterminate time.
 * <br>
 * The audit records sent to standard out will be kept for about a month,
 * but there aren't many audit records - a drop in the bucket in comparison.
 * <br>
 * So each time there would be an audit log, we'll also append to a file named
 * after the user carrying out the action.  Even if users carry out tens of thousands
 * of actions, it should be extremely compressible, when we get to that point of
 * sophistication.
 */
public class Auditor {

    private final ILogger logger;
    private final Path auditsDirectory;
    private final ActionQueue auditorActionQueue;

    public Auditor(Context context) {
        this.logger = context.getLogger();

        auditsDirectory = Path.of(context.getConstants().dbDirectory).resolve("audits");
        boolean directoryExists = Files.exists(auditsDirectory);
        logger.logDebug(() -> "Directory: " + auditsDirectory + ". Already exists: " + auditsDirectory);
        if (!directoryExists) {
            logger.logDebug(() -> "Creating directory, since it does not already exist: " + auditsDirectory);
            try {
                Files.createDirectories(auditsDirectory);
            } catch (Exception e) {
                throw new UtilsException(e);
            }
            logger.logDebug(() -> "Directory: " + auditsDirectory + " created");
        }
        auditorActionQueue = new ActionQueue("auditor", context);
        auditorActionQueue.initialize();
    }

    public void audit(ThrowingSupplier<String, Exception> msg, User user) {
        if (badFilePathPatterns.matcher(user.getUsername()).find()) {
            logger.logDebug(() -> String.format("Bad path requested for user.getUsername() in the audit: %s", user.getUsername()));
            return;
        }
        // send the audit to standard logging
        logger.logAudit(msg);
        // send the audit for recording to a separate file
        auditorActionQueue.enqueue("writing an audited action for " + user.getUsername(), () -> {
            Path userAuditFile = auditsDirectory.resolve(user.getUsername());
            try {
                Files.writeString(userAuditFile, getTimestampIsoInstant() + " " + msg.get() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception e) {
                logger.logDebug(() -> "Failed to write audit log to " + userAuditFile + ". Stacktrace: " + StacktraceUtils.stackTraceToString(e));
            }
        });

    }
}
