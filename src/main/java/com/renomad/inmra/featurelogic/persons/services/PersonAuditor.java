package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.utils.IFileWriteStringWrapper;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.queue.ActionQueue;
import com.renomad.minum.utils.StacktraceUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import static com.renomad.minum.utils.TimeUtils.getTimestampIsoInstant;

public class PersonAuditor {

    private final ILogger logger;
    private final ActionQueue actionQueue;
    private final IFileWriteStringWrapper fileWriteStringWrapper;

    public PersonAuditor(Context context, IFileWriteStringWrapper fileWriteStringWrapper) {
        this.logger = context.getLogger();
        this.actionQueue = new ActionQueue("personAuditorActionQueue", context).initialize();
        this.fileWriteStringWrapper = fileWriteStringWrapper;
    }

    /**
     * This method stores the data of a person into an audit file - it is similar to our
     * database, except it is for cold storage (at least for now) of changes.
     * @param id the UUID identifier of a person
     * @param serializedContent the String-serialized content
     * @param auditDirectory the directory where we'll store this data
     * @param name the name of this person
     */
    public void storePersonToAudit(UUID id, String serializedContent, Path auditDirectory, String name) {
        actionQueue.enqueue(
                "store a copy of the old person content, timestamped, in an audit file",
                () -> {
                    try {
                        fileWriteStringWrapper.writeString(
                                auditDirectory.resolve(id + ".audit"),
                                String.format("%s\t%s%n", getTimestampIsoInstant(), serializedContent),
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException exception) {
                        logger.logAsyncError(() -> String.format("exception thrown while writing audit for %s - %s: %s %s",
                                name,
                                id,
                                exception.getMessage(),
                                StacktraceUtils.stackTraceToString(exception)));
                    }
                });
    }

}
