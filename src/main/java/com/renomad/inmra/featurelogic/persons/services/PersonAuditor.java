package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.PersonFile;
import com.renomad.inmra.featurelogic.version.PersonFileVersionEntry;
import com.renomad.inmra.featurelogic.version.VersionUtils;
import com.renomad.inmra.utils.IFileWriteStringWrapper;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.queue.AbstractActionQueue;
import com.renomad.minum.queue.ActionQueue;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.utils.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.util.UUID;

import static com.renomad.minum.utils.TimeUtils.getTimestampIsoInstant;

public class PersonAuditor {

    private final ILogger logger;
    private final AbstractActionQueue actionQueue;
    private final IFileWriteStringWrapper fileWriteStringWrapper;
    private final VersionUtils versionUtils;

    public PersonAuditor(Context context, IFileWriteStringWrapper fileWriteStringWrapper) {
        this.logger = context.getLogger();
        this.actionQueue = new ActionQueue("personAuditorActionQueue", context).initialize();
        this.fileWriteStringWrapper = fileWriteStringWrapper;
        this.versionUtils = new VersionUtils();
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

    /**
     * Create a patch between the current and older versions of a {@link PersonFile}
     * and store the serialized form into a file.
     */
    public void storePersonFileToAudit(PersonFile currentPersonFile, PersonFile olderPersonFile, Path auditDirectory, String username, long userid) {
        var timestamp = ZonedDateTime.parse(getTimestampIsoInstant());
        PersonFileVersionEntry personFilePatch = versionUtils.createPersonFilePatch(olderPersonFile, currentPersonFile, username, userid, timestamp);
        actionQueue.enqueue(
                "store a copy of the old person content, timestamped, in an audit file",
                () -> {
                    PersonFile personFile = personFilePatch.personFile();
                    try {
                        fileWriteStringWrapper.writeString(
                                auditDirectory.resolve(currentPersonFile.getId() + ".audit"),
                                String.format("%s\t%s\t%s\t%s\n", personFilePatch.dateTimeStamp(), StringUtils.encode(personFilePatch.userName()), personFilePatch.userId(), personFile.serialize()),
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException exception) {
                        logger.logAsyncError(() -> String.format("exception thrown while writing audit for %s - %s: %s %s",
                                personFile.getName(),
                                personFile.getId(),
                                exception.getMessage(),
                                StacktraceUtils.stackTraceToString(exception)));
                    }
                });
    }

}
