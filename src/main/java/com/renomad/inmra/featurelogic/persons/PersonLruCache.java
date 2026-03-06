package com.renomad.inmra.featurelogic.persons;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.LRUCache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static com.renomad.inmra.utils.FileUtils.badFilePathPatterns;

public class PersonLruCache implements IPersonLruCache {
    private final Map<String, PersonFile> personFileLruCache;
    private final ReentrantLock personFileLruCacheLock;
    private final Path personDirectory;
    private final ILogger logger;

    public PersonLruCache(Path personDirectory, ILogger logger) {
        this.personDirectory = personDirectory;
        this.logger = logger;
        this.personFileLruCache = LRUCache.getLruCache(1_000_000);
        this.personFileLruCacheLock = new ReentrantLock();
    }


    /**
     * Obtain a mapping from a person's id (a UUID in string form)
     * to their file information
     */
    @Override
    public void putToPersonFileLruCache(String id, PersonFile personFile) {
        personFileLruCacheLock.lock();
        try {
            personFileLruCache.put(id, personFile);
        } finally {
            personFileLruCacheLock.unlock();
        }
    }

    @Override
    public void removeFromPersonFileLruCache(String id) {
        personFileLruCacheLock.lock();
        try {
            personFileLruCache.remove(id);
        } finally {
            personFileLruCacheLock.unlock();
        }
    }


    /**
     * Gets a {@link PersonFile} by UUID identifier, or else
     * return a {@link PersonFile#EMPTY}.
     * <br>
     * Will read from the cache if available, or from disk
     * if necessary.
     */
    @Override
    public PersonFile getCachedPersonFile(Person person) {
        String uuidForPerson = person.getId().toString();
        return getCachedPersonFile(uuidForPerson);
    }


    /**
     * Gets a {@link PersonFile} by UUID identifier, or else
     * return a {@link PersonFile#EMPTY}
     */
    @Override
    public PersonFile getCachedPersonFile(String uuidForPerson) {
        try {
            UUID.fromString(uuidForPerson);
        } catch (IllegalArgumentException ex) {
            logger.logDebug(() -> "Input to getCachedPersonFile was not a valid uuid.  Returning an empty person file.  Value was: " + uuidForPerson);
            return PersonFile.EMPTY;
        }
        if (badFilePathPatterns.matcher(uuidForPerson).find()) {
            logger.logDebug(() -> String.format("Bad path requested for getCachedPersonFile: %s", uuidForPerson));
            return PersonFile.EMPTY;
        }
        String personFileRaw;
        if (! personFileLruCache.containsKey(uuidForPerson)) {
            try {
                boolean personFileExists = Files.exists(personDirectory.resolve(uuidForPerson));
                if (personFileExists) {
                    personFileRaw = Files.readString(personDirectory.resolve(uuidForPerson));
                } else {
                    logger.logDebug(() -> "requested personfile of " + uuidForPerson + " did not exist.  Returning an empty person file");
                    return PersonFile.EMPTY;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            PersonFile deserializedPersonFile = PersonFile.EMPTY.deserialize(personFileRaw);
            putToPersonFileLruCache(uuidForPerson, deserializedPersonFile);
        }

        return personFileLruCache.get(uuidForPerson);
    }

}
