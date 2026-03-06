package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.auth.User;
import com.renomad.inmra.featurelogic.persons.IPersonLruCache;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.featurelogic.photo.PhotoService;
import com.renomad.inmra.featurelogic.photo.Photograph;
import com.renomad.inmra.utils.Auditor;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.minum.database.AbstractDb;
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
    private final AbstractDb<Person> personDb;
    private final IPersonLruCache personLruCache;
    private final PhotoService photoService;
    private final FamilyGraphBuilder familyGraphBuilder;
    private final Auditor auditor;

    public PersonTrasher(
            Path personDirectory,
            Path dbDir,
            ILogger logger,
            AbstractDb<Person> personDb,
            IPersonLruCache personLruCache,
            PhotoService photoService,
            FamilyGraphBuilder familyGraphBuilder,
            IFileUtils fileUtils,
            Auditor auditor
            ) {
        this.personDirectory = personDirectory;
        this.personTrash = dbDir.resolve("person_trash");
        this.personFileTrash = dbDir.resolve("person_file_trash");
        this.personDb = personDb;
        this.personLruCache = personLruCache;
        this.photoService = photoService;
        this.familyGraphBuilder = familyGraphBuilder;
        this.auditor = auditor;

        try {
            fileUtils.makeDirectory(personTrash);
            fileUtils.makeDirectory(personFileTrash);
        } catch (IOException ex) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
        }
    }

    /**
     * Put this person's data into the trash directory.
     * @param user the person executing this action
     * @param person the {@link Person} we are putting in the trash
     */
    public void moveToTrash(User user, Person person) throws IOException {
        auditor.audit(() -> String.format("%s is moving %s to %s", user.getUsername(), person, personTrash), user);
        // create a new prefix filename based on the old name and the date, which
        // will specify when this was deleted
        String newFileNameAfterDelete = String.format("deleted_on_%s___", LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        Files.writeString(personTrash.resolve(newFileNameAfterDelete + person.getId() + ".ddps"), person.serialize());

        // remove this person from the family graph
        familyGraphBuilder.deleteNode(person.getId());

        // finally, once we have written a trash-version of the person, send the delete command.
        personDb.delete(person);
        personLruCache.removeFromPersonFileLruCache(person.getId().toString());

        // move the person file data to the person_file_trash directory
        Path target = personFileTrash.resolve(newFileNameAfterDelete + person.getId().toString());
        auditor.audit(() -> String.format("%s is moving the data for %s to %s", user.getUsername(), person, target), user);

        // move the personfile to the trash directory
        Files.move(personDirectory.resolve(person.getId().toString()),
                target);

        // if we have deleted a person, we will delete their photos too.
        // no worries though - our algorithm puts the photos in a trash directory for final review
        List<Long> personAssociatedPhotoIds = photoService.getPhotoIdsForPerson(person);
        for (var photoId : personAssociatedPhotoIds) {
            Photograph photo = photoService.getPhotoById(photoId);
            photoService.deletePhotograph(user, photo);
        }
    }
}
