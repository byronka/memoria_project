package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.auth.AuthResult;
import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.featurelogic.misc.Message;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.utils.*;
import com.renomad.minum.queue.ActionQueue;
import com.renomad.minum.state.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.*;
import com.renomad.minum.web.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.renomad.minum.utils.SearchUtils.findExactlyOne;
import static com.renomad.minum.utils.TimeUtils.getTimestampIsoInstant;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

public class PhotoService {

    private final Db<Photograph> photographDb;
    private final ILogger logger;
    private final Path dbDir;
    private final Map<String, byte[]> photoLruCache;
    private final Db<PhotoToPerson> photoToPersonDb;
    private final Db<Person> personDb;
    private final ActionQueue actionQueue;
    private final IAuthUtils auth;

    /**
     * A directory for photo trash to land in
     */
    private final Path photoTrash;
    private final Path photoFileTrash;
    private final Path photoAuditDirectory;
    private final PhotoDeleter photoDeleter;
    private final FileWriteStringWrapper fileWriteStringWrapper;
    private final PhotoResizing photoResizing;
    private final Path photoArchiveDirectory;
    private final Path photoThumbnailDirectory;
    private final Path photoMediumDirectory;
    private final Path photoOriginalDirectory;

    public PhotoService(Context context,
                        MemoriaContext memoriaContext,
                        Db<Photograph> photographDb,
                        Map<String, byte[]> photoLruCache,
                        Db<PhotoToPerson> photoToPersonDb,
                        Db<Person> personDb,
                        IAuthUtils auth) {
        this.photographDb = photographDb;
        this.logger = context.getLogger();
        this.dbDir = Path.of(context.getConstants().dbDirectory);
        this.photoLruCache = photoLruCache;
        this.photoToPersonDb = photoToPersonDb;
        this.personDb = personDb;
        this.actionQueue = new ActionQueue("photoServiceActionQueue", context).initialize();
        this.auth = auth;
        this.photoDeleter = new PhotoDeleter(logger, new FileDeleteWrapper());
        this.photoResizing = new PhotoResizing(context);

        // we will store the deleted photo metadata in this directory
        this.photoTrash = dbDir.resolve("photo_trash");
        // the actual image binary will get put here when trashed.
        this.photoFileTrash = dbDir.resolve("photo_file_trash");

        this.photoArchiveDirectory = dbDir.resolve("photo_archive");
        this.photoThumbnailDirectory = dbDir.resolve("photo_files_thumbnail");
        this.photoMediumDirectory = dbDir.resolve("photo_files_medium");
        this.photoOriginalDirectory = dbDir.resolve("photo_files_original");

        // we will store old data for photos in the audit directory.  Yes, each time an
        // edit takes place, we'll add the whole data as an audit entry.
        this.photoAuditDirectory = Path.of(context.getConstants().dbDirectory).resolve("photo_audit_logs");

        IFileUtils fileUtils = memoriaContext.fileUtils();
        fileWriteStringWrapper = new FileWriteStringWrapper();

        buildNecessaryDirectories(
                fileUtils,
                photoAuditDirectory,
                photoTrash,
                photoFileTrash,
                photoArchiveDirectory,
                photoThumbnailDirectory,
                photoMediumDirectory,
                photoOriginalDirectory,
                logger);
    }

    /**
     * create a directory for photos to go if we delete them
     * in the webapp.  To delete them really and forever, it is
     * necessary to delete the files inside the photo_trash and photo_file_trash directory
     */
    static void buildNecessaryDirectories(
            IFileUtils fileUtils,
            Path photoAuditDirectory,
            Path photoTrash,
            Path photoFileTrash,
            Path photoArchiveDirectory,
            Path photoThumbnailDirectory,
            Path photoMediumDirectory,
            Path photoOriginalDirectory,
            ILogger logger) {
        try {
            fileUtils.makeDirectory(photoAuditDirectory);
            fileUtils.makeDirectory(photoTrash);
            fileUtils.makeDirectory(photoFileTrash);
            fileUtils.makeDirectory(photoArchiveDirectory);
            fileUtils.makeDirectory(photoThumbnailDirectory);
            fileUtils.makeDirectory(photoMediumDirectory);
            fileUtils.makeDirectory(photoOriginalDirectory);
        } catch (IOException e) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(e));
        }
    }


    /**
     * This deletes a {@link Photograph}
     * @param username the user executing this action. This is used for logging / auditing.
     * @param photo the photograph to delete.
     */
    public void deletePhotograph(String username, Photograph photo) throws IOException {
        logger.logAudit(() -> String.format("%s is deleting photo: %s", username, photo));

        logger.logAudit(() -> String.format("%s is moving %s to %s", username, photo, photoTrash));
        // create a new prefix filename based on the old name and the date, which
        // will specify when this was deleted
        String newFileNameAfterDelete = String.format("deleted_on_%s___", LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        Files.writeString(photoTrash.resolve(newFileNameAfterDelete + photo.getIndex() + ".ddps"), photo.serialize());

        // delete the database entry (the metadata)
        photographDb.delete(photo);

        // remove the entry for this photo in photo_to_person database
        PhotoToPerson entryToDelete = getPersonByPhoto(photo);
        photoToPersonDb.delete(entryToDelete);

        // only if no one else is pointing to this URL, will we delete the actual photograph.
        if (photographDb.values().stream().noneMatch(x -> x.getPhotoUrl().equals(photo.getPhotoUrl()))) {

            // clear it from the caches
            var regularPhotoPath = dbDir.resolve("photo_files_original").resolve(photo.getPhotoUrl());
            var mediumPhotoPath = dbDir.resolve("photo_files_medium").resolve(photo.getPhotoUrl());
            var smallPhotoPath = dbDir.resolve("photo_files_thumbnail").resolve(photo.getPhotoUrl());
            var archivePhotoPath = dbDir.resolve("photo_archive").resolve(photo.getPhotoUrl());

            photoLruCache.remove(regularPhotoPath.toString());
            photoLruCache.remove(mediumPhotoPath.toString());
            photoLruCache.remove(smallPhotoPath.toString());

            photoDeleter.deletePhoto(regularPhotoPath, username);
            photoDeleter.deletePhoto(mediumPhotoPath, username);
            photoDeleter.deletePhoto(smallPhotoPath, username);

            // don't delete the archive photo, just move it to trash
            movePhotoToTrash(archivePhotoPath, username);
        }
    }

    /**
     * A helper method to get a {@link Photograph} from our database
     * by its id
     */
    public Photograph getPhotoById(Long id) {
        return findExactlyOne(
                photographDb.values().stream(),
                x -> x.getIndex() == id);
    }

    /**
     * Given the path to a photograph on the disk, move it
     * to the trash directory in the database directory.
     * This way there's a bit more safety, and it's clear
     * what to remove if we *really* *truly* want to get
     * rid of a photograph
     */
    private void movePhotoToTrash(Path photoPath, String username) {
        try {
            logger.logAudit(() -> String.format("%s is moving %s to %s", username, photoPath, photoFileTrash));
            Files.move(photoPath, photoFileTrash.resolve(photoPath.getFileName()));
        } catch (NoSuchFileException ex) {
            logger.logDebug(() -> String.format("attempted to move a photo %s to trash, but it does not exist.  Therefore, no action taken", photoPath));
        } catch (IOException e) {
            String exception = StacktraceUtils.stackTraceToString(e);
            logger.logAsyncError(() -> String.format("Error: Failed to move photo %s to trash: %s", photoPath, exception));
        }
    }


    /**
     * Returns a list of identifiers for photos that are associated
     * with a person
     * @param person the {@link Person} we are inspecting for photos
     */
    public List<Long> getPhotoIdsForPerson(Person person) {
        return photoToPersonDb.values().stream()
                .filter(x -> x.getPersonIndex() == person.getIndex())
                .map(PhotoToPerson::getPhotoIndex)
                .toList();
    }

    /**
     * Returns the identifier of a person for a particular photo
     * @param photograph the {@link Photograph} we are inspecting
     */
    public PhotoToPerson getPersonByPhoto(Photograph photograph) {
        return SearchUtils.findExactlyOne(
                photoToPersonDb.values().stream(),
                x -> x.getPhotoIndex() == photograph.getIndex());
    }

    /**
     * This method stores the metadata of a photo into an audit file - it is similar to our
     * database, except it is for cold storage (at least for now) of changes.
     */
    private void enqueuePhotoAudit(
            Photograph photo) {
        actionQueue.enqueue(
                "store a copy of the old photo content, timestamped, in an audit file",
                storePhotoAudit(photo, photoAuditDirectory, logger, fileWriteStringWrapper));
    }

    static ThrowingRunnable storePhotoAudit(
            Photograph photo,
            Path photoAuditDirectory,
            ILogger logger,
            IFileWriteStringWrapper fileWriteStringWrapper) {
        return () -> {
            try {
                fileWriteStringWrapper.writeString(
                        photoAuditDirectory.resolve(photo.getPhotoUrl() + ".audit"),
                        String.format("%s\t%s%n", getTimestampIsoInstant(), photo.serialize()),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                logger.logAsyncError(() -> String.format("exception thrown while writing to photo audit for %s: %s %s",
                        photo.getPhotoUrl(),
                        exception.getMessage(),
                        StacktraceUtils.stackTraceToString(exception)));
            }
        };
    }


    /**
     * make sure they sent a photo
     */
    byte[] checkPhotoWasSent(Body body) {
        var photoBytes = body.asBytes("image_uploads");
        if (photoBytes == null || photoBytes.length == 0) {
            throw new InvalidPhotoException("Error: a photograph is required");
        }
        return photoBytes;
    }

    /**
     * make sure they sent a short description
     */
    String checkShortDescription(Body body) {
        var shortDescription = body.asString("short_description");
        if (shortDescription == null || shortDescription.isBlank()) {
            throw new InvalidPhotoException("Error: caption (short description) is required");
        }
        return shortDescription;
    }

    /**
     * make sure they sent a photo identifier (when copying)
     */
    String checkPhotoId(Body body) {
        var photoId = body.asString("photo_id");
        if (photoId == null || photoId.isBlank()) {
            throw new InvalidPhotoException("Error: photo id is required");
        }
        return photoId;
    }

    /**
     * they must have a person id to associate with this photo
     */
    Person checkPersonId(Body body) {
        var personIdString = body.asString("person_id");
        if (personIdString == null || personIdString.isBlank()) {
            throw new InvalidPhotoException("Error: a person_id is required and must be valid");
        }
        Person foundPerson = SearchUtils
                .findExactlyOne(
                        personDb.values().stream(),
                        x -> x.getId().toString().equals(personIdString));
        if (foundPerson == null) {
            throw new InvalidPhotoException("Error: a valid person_id is required");
        }
        return foundPerson;
    }

    /**
     * See {@link #photoWaiter(String, int, int, Path, ILogger)}
     */
    void waitUntilPhotosConverted(String newFilename, int countOfChecks, int waitTimePerCheck) {
        photoWaiter(newFilename, countOfChecks, waitTimePerCheck, dbDir, logger);
    }

    /**
     * This code will look into all the directories where we expect this photo
     * to be stored after processing finishes. If the photos do not land in
     * time, it will just move on (the original file will be in the proper place)
     * and a debug log will be added.
     * @param countOfChecks number of times we will check whether the files have arrived in proper place.
     * @param waitTimePerCheck how long we will wait after checking each time
     */
    static void photoWaiter(String newFilename, int countOfChecks, int waitTimePerCheck, Path dbDir, ILogger logger) {
        // we will check up to countOfChecks times and then move on.
        var thumbnailConvertedPhoto = dbDir.resolve("photo_files_thumbnail").resolve(newFilename);
        var mediumConvertedPhoto = dbDir.resolve("photo_files_medium").resolve(newFilename);
        var originalConvertedPhoto = dbDir.resolve("photo_files_original").resolve(newFilename);
        for (int i = 0; i < countOfChecks; i++) {
            if (
                    Files.isRegularFile(thumbnailConvertedPhoto) &&
                            Files.isRegularFile(mediumConvertedPhoto) &&
                            Files.isRegularFile(originalConvertedPhoto)
            ) {
                return;
            }
            MyThread.sleep(waitTimePerCheck);
        }
        // if we get here, we never found the files in all their expected locations
        logger.logDebug(() -> "Did not find " + newFilename + " stored in its expected locations when time ran out");
    }

    /**
     * Given proper data, writes the new photograph to disk and database.
     * <br>
     * This will also add the new photograph to a queue for resizing and
     * storing in several destination directories. See {@link #photoWaiter(String, int, int, Path, ILogger)}
     * @param suffix the image file suffix, e.g. ".jpg" or ".png"
     * @param shortDescription a short description of the image, goes in alt text
     * @param description longer description of the photo - unlimited in length
     * @param photoBytes the bytes of the image
     * @param person the person associated to this photo
     * @return the new filename of the image, e.g. "foo.jpg"
     */
    String writePhotoData(
            String suffix,
            String shortDescription,
            String description,
            byte[] photoBytes,
            Person person) throws IOException {
        var newFilename = UUID.randomUUID() + suffix;
        final var newPhotograph = new Photograph(0L, newFilename, shortDescription, description);
        Files.write(photoArchiveDirectory.resolve(newFilename), photoBytes);

        Photograph writtenPhotograph = photographDb.write(newPhotograph);
        final var photoToPerson = new PhotoToPerson(0L, writtenPhotograph.getIndex(), person.getIndex());
        photoToPersonDb.write(photoToPerson);

        // add to queue for resizing
        photoResizing.addConversionToQueue(new ByteArrayInputStream(photoBytes), 150, photoThumbnailDirectory.resolve(newFilename).toFile());
        photoResizing.addConversionToQueue(new ByteArrayInputStream(photoBytes), 1200, photoMediumDirectory.resolve(newFilename).toFile());
        photoResizing.addConversionToQueue(new ByteArrayInputStream(photoBytes), 3000, photoOriginalDirectory.resolve(newFilename).toFile());
        return newFilename;
    }

    /**
     * Create a copy of the photo metadata for another person, so they can share access
     * to the photo bytes but have the photo listed in their page with its own
     * description.  Imagine a photo with several family members.  You might want
     * to make that photo available to each family member, so they could each
     * write a description, but you would only need one copy of the photo itself.
     * @param filename the filename of the photograph itself
     * @param person the person to whom we are attaching this already-existing photo.
     */
    void copyPhoto(String filename, String shortDescription, String description, Person person) {
        final var newPhotograph = new Photograph(0L, filename, shortDescription, description);
        Photograph writtenPhotograph = photographDb.write(newPhotograph);
        final var photoToPerson = new PhotoToPerson(0L, writtenPhotograph.getIndex(), person.getIndex());
        photoToPersonDb.write(photoToPerson);
    }

    /**
     * Look at the mime of the photo file we were sent and determine its
     * proper suffix.
     */
    String extractSuffix(Body body) {
        Headers partitionHeaders = body.partitionHeaders("image_uploads");
        List<String> contentTypeList = partitionHeaders != null ? partitionHeaders.valueByKey("content-type") : List.of();
        return determineSuffixByContentType(contentTypeList, logger);
    }

    static String determineSuffixByContentType(List<String> contentTypeList, ILogger logger) {
        String suffix;
        if (contentTypeList == null || contentTypeList.size() != 1) {
            logger.logDebug(() -> String.format("setting image file suffix to unknown.  Content type was: %s", contentTypeList));
            suffix = ".unknown";
        } else {
            String contentType = contentTypeList.get(0);
            switch (contentType) {
                case "image/png" -> suffix = ".png";
                case "image/jpeg" -> suffix = ".jpg";
                default -> {
                    logger.logDebug(() -> String.format("setting image file suffix to unfamiliar_mime_type.  Content type was: %s", contentType));
                    suffix = ".unfamiliar_mime_type";
                }
            }
        }
        return suffix;
    }


    /**
     * Deletes a photograph and its related metadata
     * @param isPost if true, we received this as a post, and therefore need to
     *               review the body params and return a redirect.  Otherwise, it
     *               came from a Javascript request, and we'll just return 204
     *               if successful.
     */
    public Response photoDelete(Request request, boolean isPost) {
        AuthResult authResult = auth.processAuth(request);

        if (!authResult.isAuthenticated()) {
            return Response.redirectTo("/");
        }

        // get the id from different spots, depending on if this is a post.
        long id;
        try {
            id = Long.parseLong(isPost ? request.body().asString("photoid") :
                    request.requestLine().queryString().get("id"));
        } catch (NumberFormatException ex) {
            logger.logDebug(() -> "User failed to include valid id of photograph to delete. " + ex);
            return new Response(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }

        Photograph photo = SearchUtils.findExactlyOne(
                photographDb.values().stream(),
                x1 -> x1.getIndex() == id);

        if (photo == null) {
            logger.logDebug(() -> "User provided an invalid file to delete: " + id);
            return new Response(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }

        // get the person for this photo, so we can redirect to them.
        long personByPhoto = getPersonByPhoto(photo).getPersonIndex();

        try {
            String username = authResult.user().getUsername();
            deletePhotograph(username, photo);
        } catch (IOException e) {
            logger.logDebug(() -> "Failed to delete photograph fully: " + e);
            return new Response(CODE_500_INTERNAL_SERVER_ERROR);
        }

        if (isPost) {
            Person person = findExactlyOne(personDb.values().stream(), x -> x.getIndex() == personByPhoto);
            return Message.redirect("The photo has been deleted", "/photos?personid=" + person.getId().toString());
        } else {
            return new Response(StatusLine.StatusCode.CODE_204_NO_CONTENT);
        }
    }


    /**
     * Update the long description on a photograph
     * @param isPost if true, this request arrived as a POST request, and needs
     *               to be handled accordingly.  Otherwise, a JavaScript request.
     */
    public Response photoLongDescriptionUpdate(Request request, boolean isPost) {
        AuthResult authResult = auth.processAuth(request);
        if (!authResult.isAuthenticated()) {
            return Response.redirectTo("/");
        }
        String updatedDescription = isPost ? request.body().asString("long_description") : request.body().asString();
        long photoId;
        try {
            photoId = Long.parseLong(isPost ? request.body().asString("photoid") : request.requestLine().queryString().get("id"));
        } catch (NumberFormatException ex) {
            logger.logDebug(() -> "User failed to include valid id of photograph to delete. " + ex);
            return new Response(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }

        Photograph originalPhoto = SearchUtils.findExactlyOne(photographDb.values().stream(), x -> x.getIndex() == photoId);
        if (originalPhoto != null && originalPhoto != Photograph.EMPTY) {
            // store the old data in the audit log
            enqueuePhotoAudit(originalPhoto);
            // update the photo data
            Photograph dataUpdate = new Photograph(
                    originalPhoto.getIndex(),
                    originalPhoto.getPhotoUrl(),
                    originalPhoto.getShortDescription(),
                    updatedDescription);
            photographDb.write(dataUpdate);

            if (isPost) {
                // get the person for this photo, so we can redirect to them.
                long personByPhoto = getPersonByPhoto(originalPhoto).getPersonIndex();
                Person person = findExactlyOne(personDb.values().stream(), x -> x.getIndex() == personByPhoto);
                return Message.redirect("The photo's long description has been modified", "/photos?personid=" + person.getId().toString());
            } else {
                return new Response(CODE_204_NO_CONTENT);
            }
        } else {
            return new Response(CODE_400_BAD_REQUEST);
        }

    }


    /**
     * Receives a new caption of a photograph as a PATCH request,
     * sent by Javascript.
     * @param isPost if true, this is being sent as a POST request, and we'll
     *               handle accordingly.  Otherwise, a request sent by Javascript.
     */
    public Response photoShortDescriptionUpdate(Request request, boolean isPost) {
        AuthResult authResult = auth.processAuth(request);
        if (!authResult.isAuthenticated()) {
            return Response.redirectTo("/");
        }
        String updatedShortDescription = isPost ? request.body().asString("caption") : request.body().asString();
        long photoId;
        try {
            photoId = Long.parseLong(isPost ? request.body().asString("photoid") : request.requestLine().queryString().get("id"));
        } catch (NumberFormatException ex) {
            logger.logDebug(() -> "User failed to include valid id of photograph to delete. " + ex);
            return new Response(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }

        Photograph originalPhoto = SearchUtils.findExactlyOne(photographDb.values().stream(), x -> x.getIndex() == photoId);
        if (originalPhoto != null && originalPhoto != Photograph.EMPTY) {
            // store the old data in the audit log
            enqueuePhotoAudit(originalPhoto);

            // update the photo data
            Photograph dataUpdate = new Photograph(
                    originalPhoto.getIndex(),
                    originalPhoto.getPhotoUrl(),
                    updatedShortDescription,
                    originalPhoto.getDescription());
            photographDb.write(dataUpdate);

            if (isPost) {
                // get the person for this photo, so we can redirect to them.
                long personByPhoto = getPersonByPhoto(originalPhoto).getPersonIndex();
                Person person = findExactlyOne(personDb.values().stream(), x -> x.getIndex() == personByPhoto);

                return Message.redirect("The photo's caption has been modified", "/photos?personid=" + person.getId().toString());
            } else {
                return new Response(CODE_204_NO_CONTENT);
            }
        } else {
            return new Response(CODE_400_BAD_REQUEST);
        }

    }
}
