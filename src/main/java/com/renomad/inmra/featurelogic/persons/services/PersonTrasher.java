package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.IPersonLruCache;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.featurelogic.persons.PersonFile;
import com.renomad.inmra.featurelogic.photo.PhotoService;
import com.renomad.inmra.featurelogic.photo.Photograph;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.StacktraceUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A class responsible for the tasks when putting a person in the trash.
 * <br>
 * This is actually a pretty involved process.  When we delete a person, we
 * soft-delete them - we move all their data to a different directory, named
 * by their UUID and timestamp.
 * <br>
 * We also kill off that person's photographs, using a similarly convoluted
 * process - again, soft-delete all the way.
 */
public class PersonTrasher {

    private final Path personDirectory;
    /**
     * This is where we will store old deleted persons, as a
     * last ditch before deletion.
     */
    private final Path personTrash;
    private final Path personFileTrash;
    private final ILogger logger;
    private final Db<Person> personDb;
    private final IPersonLruCache personLruCache;
    private final PhotoService photoService;
    private final FamilyGraphBuilder familyGraphBuilder;

    public PersonTrasher(
            Path personDirectory,
            Path dbDir,
            ILogger logger,
            Db<Person> personDb,
            IPersonLruCache personLruCache,
            PhotoService photoService,
            FamilyGraphBuilder familyGraphBuilder,
            IFileUtils fileUtils
            ) {
        this.personDirectory = personDirectory;
        this.personTrash = dbDir.resolve("person_trash");
        this.personFileTrash = dbDir.resolve("person_file_trash");
        this.logger = logger;
        this.personDb = personDb;
        this.personLruCache = personLruCache;
        this.photoService = photoService;
        this.familyGraphBuilder = familyGraphBuilder;

        try {
            fileUtils.makeDirectory(personTrash);
            fileUtils.makeDirectory(personFileTrash);
        } catch (IOException ex) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
        }
    }

    /**
     * Put this person's data into the trash directory.
     * @param username the person executing this action
     * @param person the {@link Person} we are putting in the trash
     */
    public void moveToTrash(String username, Person person) throws IOException {
        logger.logAudit(() -> String.format("%s is moving %s to %s", username, person, personTrash));
        // create a new prefix filename based on the old name and the date, which
        // will specify when this was deleted
        String newFileNameAfterDelete = String.format("deleted_on_%s___", LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        Files.writeString(personTrash.resolve(newFileNameAfterDelete + person.getId() + ".ddps"), person.serialize());

        // finally, once we have written a trash-version of the person, send the delete command.
        personDb.delete(person);
        PersonFile cachedPersonFile = personLruCache.getCachedPersonFile(person);
        personLruCache.removeFromPersonFileLruCache(person.getId().toString());
        familyGraphBuilder.rebuildFamilyTree(cachedPersonFile);

        // move the person file data to the person_file_trash directory
        Path target = personFileTrash.resolve(newFileNameAfterDelete + person.getId().toString());
        logger.logAudit(() -> String.format("%s is moving the data for %s to %s", username, person, target));

        Files.move(personDirectory.resolve(person.getId().toString()),
                target);

        // if we have deleted a person, we will delete their photos too.
        // no worries though - our algorithm puts the photos in a trash directory for final review
        List<Long> personAssociatedPhotoIds = photoService.getPhotoIdsForPerson(person);
        for (var photoId : personAssociatedPhotoIds) {
            Photograph photo = photoService.getPhotoById(photoId);
            photoService.deletePhotograph(username, photo);
        }
    }
}
