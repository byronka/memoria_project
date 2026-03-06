package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.auth.AuthResult;
import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.auth.User;
import com.renomad.inmra.featurelogic.misc.Message;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.utils.*;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.queue.AbstractActionQueue;
import com.renomad.minum.queue.ActionQueue;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.*;
import com.renomad.minum.web.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.renomad.minum.utils.SearchUtils.findExactlyOne;
import static com.renomad.minum.utils.TimeUtils.getTimestampIsoInstant;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

public class PhotoService {

    private final AbstractDb<Photograph> photographDb;
    private final AbstractDb<Video> videoDb;
    private final ILogger logger;
    private final Path dbDir;
    private final Map<String, byte[]> photoLruCache;
    private final AbstractDb<PhotoToPerson> photoToPersonDb;
    private final AbstractDb<VideoToPerson> videoToPersonDb;
    private final AbstractDb<Person> personDb;
    private final AbstractActionQueue actionQueue;
    private final IAuthUtils auth;

    /**
     * A directory for photo trash to land in
     */
    private final Path photoTrash;
    private final Path videoTrash;
    private final Path photoFileTrash;
    private final Path videoFileTrash;
    private final Path photoAuditDirectory;
    private final Path videoAuditDirectory;
    private final PhotoDeleter photoDeleter;
    private final FileWriteStringWrapper fileWriteStringWrapper;
    private final PhotoResizing photoResizing;
    private final Path videoDirectory;
    private final Path photoArchiveDirectory;
    private final Path photoIconDirectory;
    private final Path photoThumbnailDirectory;
    private final Path photoMediumDirectory;
    private final Path photoOriginalDirectory;
    private final Auditor auditor;

    public PhotoService(Context context,
                        MemoriaContext memoriaContext,
                        AbstractDb<Photograph> photographDb,
                        AbstractDb<Video> videoDb,
                        Map<String, byte[]> photoLruCache,
                        AbstractDb<PhotoToPerson> photoToPersonDb,
                        AbstractDb<VideoToPerson> videoToPersonDb,
                        AbstractDb<Person> personDb,
                        IAuthUtils auth) {
        IFileUtils fileUtils = memoriaContext.getFileUtils();
        this.auditor = memoriaContext.getAuditor();

        this.photographDb = photographDb;
        this.videoDb = videoDb;
        this.logger = context.getLogger();
        this.dbDir = Path.of(context.getConstants().dbDirectory);
        this.photoLruCache = photoLruCache;
        this.photoToPersonDb = photoToPersonDb;
        this.videoToPersonDb = videoToPersonDb;
        this.personDb = personDb;
        this.actionQueue = new ActionQueue("photoServiceActionQueue", context).initialize();
        this.auth = auth;
        this.photoDeleter = new PhotoDeleter(logger, new FileDeleteWrapper(), auditor);
        this.photoResizing = new PhotoResizing(context);

        // we will store the deleted photo metadata in this directory
        this.photoTrash = dbDir.resolve("photo_trash");
        this.videoTrash = dbDir.resolve("video_trash");
        // the actual image binary will get put here when trashed.
        this.photoFileTrash = dbDir.resolve("photo_file_trash");
        this.videoFileTrash = dbDir.resolve("video_file_trash");

        this.videoDirectory = dbDir.resolve("video_files");
        this.photoArchiveDirectory = dbDir.resolve("photo_archive");
        this.photoIconDirectory = dbDir.resolve("photo_files_icon");
        this.photoThumbnailDirectory = dbDir.resolve("photo_files_thumbnail");
        this.photoMediumDirectory = dbDir.resolve("photo_files_medium");
        this.photoOriginalDirectory = dbDir.resolve("photo_files_original");

        // we will store old data for photos in the audit directory.  Yes, each time an
        // edit takes place, we'll add the whole data as an audit entry.
        this.photoAuditDirectory = Path.of(context.getConstants().dbDirectory).resolve("photo_audit_logs");
        this.videoAuditDirectory = Path.of(context.getConstants().dbDirectory).resolve("video_audit_logs");

        fileWriteStringWrapper = new FileWriteStringWrapper();

        buildNecessaryDirectories(
                fileUtils,
                photoAuditDirectory,
                videoAuditDirectory,
                photoTrash,
                videoTrash,
                photoFileTrash,
                videoFileTrash,
                photoArchiveDirectory,
                photoIconDirectory,
                photoThumbnailDirectory,
                photoMediumDirectory,
                photoOriginalDirectory,
                videoDirectory,
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
            Path videoAuditDirectory,
            Path photoTrash,
            Path videoTrash,
            Path photoFileTrash,
            Path videoFileTrash,
            Path photoArchiveDirectory,
            Path photoIconDirectory,
            Path photoThumbnailDirectory,
            Path photoMediumDirectory,
            Path photoOriginalDirectory,
            Path videoDirectory,
            ILogger logger) {
        try {
            fileUtils.makeDirectory(photoAuditDirectory);
            fileUtils.makeDirectory(videoAuditDirectory);
            fileUtils.makeDirectory(photoTrash);
            fileUtils.makeDirectory(videoTrash);
            fileUtils.makeDirectory(photoFileTrash);
            fileUtils.makeDirectory(videoFileTrash);
            fileUtils.makeDirectory(photoArchiveDirectory);
            fileUtils.makeDirectory(photoIconDirectory);
            fileUtils.makeDirectory(photoThumbnailDirectory);
            fileUtils.makeDirectory(photoMediumDirectory);
            fileUtils.makeDirectory(photoOriginalDirectory);
            fileUtils.makeDirectory(videoDirectory);
        } catch (IOException e) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(e));
        }
    }


    /**
     * This deletes a {@link Photograph}
     * @param user the user executing this action. This is used for logging / auditing.
     * @param photo the photograph to delete.
     */
    public void deletePhotograph(User user, Photograph photo) throws IOException {
        auditor.audit(() -> String.format("%s is deleting photo: %s", user.getUsername(), photo), user);

        auditor.audit(() -> String.format("%s is moving %s to %s", user.getUsername(), photo, photoTrash), user);
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
            var iconPhotoPath = dbDir.resolve("photo_files_icon").resolve(photo.getPhotoUrl());
            var archivePhotoPath = dbDir.resolve("photo_archive").resolve(photo.getPhotoUrl());

            photoLruCache.remove(regularPhotoPath.toString());
            photoLruCache.remove(mediumPhotoPath.toString());
            photoLruCache.remove(smallPhotoPath.toString());
            photoLruCache.remove(iconPhotoPath.toString());

            photoDeleter.deletePhoto(regularPhotoPath, user);
            photoDeleter.deletePhoto(mediumPhotoPath, user);
            photoDeleter.deletePhoto(smallPhotoPath, user);
            photoDeleter.deletePhoto(iconPhotoPath, user);

            // don't delete the archive photo, just move it to trash
            movePhotoToTrash(archivePhotoPath, user);
        }
    }

    /**
     * This deletes a {@link Video}
     * @param user the user executing this action. This is used for logging / auditing.
     * @param video the video to delete.
     */
    public void deleteVideo(User user, Video video) throws IOException {
        auditor.audit(() -> String.format("%s is deleting video: %s", user.getUsername(), video), user);

        auditor.audit(() -> String.format("%s is moving %s to %s", user.getUsername(), video, videoTrash), user);
        // create a new prefix filename based on the old name and the date, which
        // will specify when this was deleted
        String newFileNameAfterDelete = String.format("deleted_on_%s___", LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        Files.writeString(videoTrash.resolve(newFileNameAfterDelete + video.getIndex() + ".ddps"), video.serialize());

        // delete the database entry (the metadata)
        videoDb.delete(video);

        // remove the entry for this photo in photo_to_person database
        VideoToPerson entryToDelete = getPersonByVideo(video);
        videoToPersonDb.delete(entryToDelete);

        // only if no one else is pointing to this URL, will we delete the actual video.
        if (videoDb.values().stream().noneMatch(x -> x.getVideoUrl().equals(video.getVideoUrl()))) {

            var videoPath = videoDirectory.resolve(video.getVideoUrl());

            // don't delete the video, just move it to trash
            moveVideoToTrash(videoPath, user);
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
     * A helper method to get a {@link Video} from our database
     * by its id
     */
    public Video getVideoById(Long id) {
        return findExactlyOne(
                videoDb.values().stream(),
                x -> x.getIndex() == id);
    }

    /**
     * Given the path to a photograph on the disk, move it
     * to the trash directory in the database directory.
     * This way there's a bit more safety, and it's clear
     * what to remove if we *really* *truly* want to get
     * rid of a photograph
     */
    private void movePhotoToTrash(Path photoPath, User user) {
        try {
            auditor.audit(() -> String.format("%s is moving %s to %s", user.getUsername(), photoPath, photoFileTrash), user);
            Files.move(photoPath, photoFileTrash.resolve(photoPath.getFileName()));
        } catch (NoSuchFileException ex) {
            logger.logDebug(() -> String.format("attempted to move a photo %s to trash, but it does not exist.  Therefore, no action taken", photoPath));
        } catch (IOException e) {
            String exception = StacktraceUtils.stackTraceToString(e);
            logger.logAsyncError(() -> String.format("Error: Failed to move photo %s to trash: %s", photoPath, exception));
        }
    }


    /**
     * Given the path to a video on the disk, move it
     * to the trash directory in the database directory.
     * This way there's a bit more safety, and it's clear
     * what to remove if we *really* *truly* want to get
     * rid of a video
     */
    private void moveVideoToTrash(Path videoPath, User user) {
        try {
            auditor.audit(() -> String.format("%s is moving %s to %s", user.getUsername(), videoPath, videoFileTrash), user);
            Files.move(videoPath, videoFileTrash.resolve(videoPath.getFileName()));
        } catch (NoSuchFileException ex) {
            logger.logDebug(() -> String.format("attempted to move a video %s to trash, but it does not exist.  Therefore, no action taken", videoPath));
        } catch (IOException e) {
            String exception = StacktraceUtils.stackTraceToString(e);
            logger.logAsyncError(() -> String.format("Error: Failed to move video %s to trash: %s", videoPath, exception));
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
     * Returns the identifier of a person for a particular video
     * @param video the {@link Video} we are inspecting
     */
    public VideoToPerson getPersonByVideo(Video video) {
        return SearchUtils.findExactlyOne(
                videoToPersonDb.values().stream(),
                x -> x.getVideoIndex() == video.getIndex());
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
     * This method stores the metadata of a video into an audit file - it is similar to our
     * database, except it is for cold storage (at least for now) of changes.
     */
    private void enqueueVideoAudit(
            Video video) {
        actionQueue.enqueue(
                "store a copy of the old video content, timestamped, in an audit file",
                storeVideoAudit(video, videoAuditDirectory, logger, fileWriteStringWrapper));
    }

    static ThrowingRunnable storeVideoAudit(
            Video video,
            Path videoAuditDirectory,
            ILogger logger,
            IFileWriteStringWrapper fileWriteStringWrapper) {
        return () -> {
            try {
                fileWriteStringWrapper.writeString(
                        videoAuditDirectory.resolve(video.getVideoUrl() + ".audit"),
                        String.format("%s\t%s%n", getTimestampIsoInstant(), video.serialize()),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                logger.logAsyncError(() -> String.format("exception thrown while writing to video audit for %s: %s %s",
                        video.getVideoUrl(),
                        exception.getMessage(),
                        StacktraceUtils.stackTraceToString(exception)));
            }
        };
    }

    long handleVideoFile(StreamingMultipartPartition videoPartition, String newFilename) throws IOException {
        long countOfVideoBytes;
        Path videoFile = videoDirectory.resolve(newFilename);
        Files.createFile(videoFile);

        FileChannel fc = FileChannel.open(videoFile, StandardOpenOption.WRITE);
        OutputStream outputStream = Channels.newOutputStream(fc);
        countOfVideoBytes = copy(videoPartition, outputStream);
        return countOfVideoBytes;
    }

    long copy(InputStream source, OutputStream target) throws IOException {
        long countOfBytesRead = 0;
        byte[] buf = new byte[8192];
        int length;
        while ((length = source.read(buf)) != -1) {
            countOfBytesRead += length;
            target.write(buf, 0, length);
        }
        return countOfBytesRead;
    }

    /**
     * make sure they sent a short description
     */
    String checkShortDescription(Body body) {
        String shortDescription;
        try {
            shortDescription = body.getPartitionByName("short_description").getFirst().getContentAsString();
        } catch (Exception ex) {
            shortDescription = body.asString("short_description");
        }
        if (shortDescription == null || shortDescription.isBlank()) {
            throw new InvalidPhotoException("Error: caption (short description) is required");
        }
        return shortDescription;
    }

    /**
     * make sure they sent a photo identifier (when copying)
     */
    String checkPhotoId(Body body) {
        String photoId;
        try {
            photoId = body.getPartitionByName("photo_id").getFirst().getContentAsString();
        } catch (Exception ex) {
            photoId = body.asString("photo_id");
        }
        if (photoId == null || photoId.isBlank()) {
            throw new InvalidPhotoException("Error: photo_id is required");
        }
        return photoId;
    }

    /**
     * make sure they sent a video identifier (when copying)
     */
    Video checkVideoId(Body body) {
        String videoId;
        try {
            videoId = body.getPartitionByName("video_id").getFirst().getContentAsString();
        } catch (NoSuchElementException ex) {
            videoId = body.asString("video_id");
        }
        if (videoId == null || videoId.isBlank()) {
            throw new InvalidPhotoException("Error: video_id is required");
        }
        int videoIdInt = Integer.parseInt(videoId);
        return findExactlyOne(videoDb.values().stream(), x -> x.getIndex() == videoIdInt);
    }

    Person checkPersonId(String personIdString) {
        if (personIdString.isBlank()) {
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
     * they must have a person id to associate with this photo
     */
    Person checkPersonId(Body body) {
        String personIdString;
        try {
            personIdString = body.getPartitionByName("person_id").getFirst().getContentAsString();
        } catch (Exception ex) {
            personIdString = body.asString("person_id");
        }
        if (personIdString == null || personIdString.isBlank()) {
            throw new InvalidPhotoException("Error: a person_id is required and must be valid");
        }
        String finalPersonIdString = personIdString;
        Person foundPerson = SearchUtils
                .findExactlyOne(
                        personDb.values().stream(),
                        x -> x.getId().toString().equals(finalPersonIdString));
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
        final var photoToPerson = new PhotoToPerson(0L, writtenPhotograph.getIndex(), person.getIndex(), newFilename);
        photoToPersonDb.write(photoToPerson);

        // add to queue for resizing
        photoResizing.addConversionToQueue(new ByteArrayInputStream(photoBytes), 20, photoIconDirectory.resolve(newFilename).toFile());
        photoResizing.addConversionToQueue(new ByteArrayInputStream(photoBytes), 150, photoThumbnailDirectory.resolve(newFilename).toFile());
        photoResizing.addConversionToQueue(new ByteArrayInputStream(photoBytes), 1200, photoMediumDirectory.resolve(newFilename).toFile());
        photoResizing.addConversionToQueue(new ByteArrayInputStream(photoBytes), 3000, photoOriginalDirectory.resolve(newFilename).toFile());
        return newFilename;
    }

    /**
     * Writes the metadata for a video into the database.
     */
    void writeVideoData(
            String shortDescription,
            String description,
            String newVideoFilename,
            Person person) {
        final var newVideo = new Video(0L, newVideoFilename, shortDescription, description, "/listphotos/video_poster.jpg");

        Video writtenVideo = videoDb.write(newVideo);
        final var videoToPerson = new VideoToPerson(0L, writtenVideo.getIndex(), person.getIndex(), newVideoFilename);
        videoToPersonDb.write(videoToPerson);
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
        final var photoToPerson = new PhotoToPerson(0L, writtenPhotograph.getIndex(), person.getIndex(), filename);
        photoToPersonDb.write(photoToPerson);
    }

    /**
     * Create a copy of the video metadata for another person, so they can share access
     * to the video bytes but have the video listed in their page with its own
     * description.  Imagine a video with several family members.  You might want
     * to make that video available to each family member, so they could each
     * write a description, but you would only need one copy of the video itself.
     * @param filename the filename of the video itself
     * @param person the person to whom we are attaching this already-existing video.
     */
    void copyVideo(String filename, String shortDescription, String description, Person person, String posterUrl) {
        final var newVideo = new Video(0L, filename, shortDescription, description, posterUrl);
        Video writtenVideo = videoDb.write(newVideo);
        final var videoToPerson = new VideoToPerson(0L, writtenVideo.getIndex(), person.getIndex(), filename);
        videoToPersonDb.write(videoToPerson);
    }

    /**
     * Look at the mime of the photo file we were sent and determine its
     * proper suffix.
     */
    String extractSuffix(StreamingMultipartPartition partition) {
        List<String> contentTypeList = partition.getHeaders().valueByKey("content-type");
        return determineSuffixByContentType(contentTypeList, logger);
    }

    static String determineSuffixByContentType(List<String> contentTypeList, ILogger logger) {
        String suffix;
        if (contentTypeList == null || contentTypeList.size() != 1) {
            logger.logDebug(() -> String.format("setting image file suffix to unknown.  Content type was: %s", contentTypeList));
            suffix = ".unknown";
        } else {
            String contentType = contentTypeList.getFirst();
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
    public IResponse photoDelete(IRequest request, boolean isPost) {
        AuthResult authResult = auth.processAuth(request);

        if (!authResult.isAuthenticated()) {
            return Respond.redirectTo("/");
        }

        // get the id from different spots, depending on if this is a post.
        long id;
        try {
            id = Long.parseLong(isPost ? request.getBody().asString("photoid") :
                    request.getRequestLine().queryString().get("id"));
        } catch (NumberFormatException ex) {
            logger.logDebug(() -> "User failed to include valid id of photograph to delete. " + ex);
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }

        Photograph photo = SearchUtils.findExactlyOne(
                photographDb.values().stream(),
                x1 -> x1.getIndex() == id);

        if (photo == null) {
            logger.logDebug(() -> "User provided an invalid file to delete: " + id);
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }

        // get the person for this photo, so we can redirect to them.
        long personByPhoto = getPersonByPhoto(photo).getPersonIndex();

        try {
            deletePhotograph(authResult.user(), photo);
        } catch (IOException e) {
            logger.logDebug(() -> "Failed to delete photograph fully: " + e);
            return Response.buildLeanResponse(CODE_500_INTERNAL_SERVER_ERROR);
        }

        auditor.audit(() -> String.format("user %s has deleted a photo, url: %s, that was associated with person %d with a short description of %s",
                authResult.user().getUsername(),
                photo.getPhotoUrl(),
                personByPhoto,
                photo.getShortDescription()
                ), authResult.user());

        if (isPost) {
            Person person = findExactlyOne(personDb.values().stream(), x -> x.getIndex() == personByPhoto);
            return Message.redirect("The photo has been deleted", "/photos?personid=" + person.getId().toString());
        } else {
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_204_NO_CONTENT);
        }
    }


    /**
     * Deletes a video and its related metadata
     * @param isPost if true, we received this as a post, and therefore need to
     *               review the body params and return a redirect.  Otherwise, it
     *               came from a Javascript request, and we'll just return 204
     *               if successful.
     */
    public IResponse videoDelete(IRequest request, boolean isPost) {
        AuthResult authResult = auth.processAuth(request);

        if (!authResult.isAuthenticated()) {
            return Respond.unauthorizedError();
        }

        // get the id from different spots, depending on if this is a post.
        long id;
        try {
            id = Long.parseLong(isPost ? request.getBody().asString("videoid") :
                    request.getRequestLine().queryString().get("id"));
        } catch (NumberFormatException ex) {
            logger.logDebug(() -> "User failed to include valid id of video to delete. " + ex);
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }

        Video video = SearchUtils.findExactlyOne(
                videoDb.values().stream(),
                x1 -> x1.getIndex() == id);

        if (video == null) {
            logger.logDebug(() -> "User provided an invalid file to delete: " + id);
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }

        // get the person for this video, so we can redirect to them.
        long personByVideo = getPersonByVideo(video).getPersonIndex();

        try {
            deleteVideo(authResult.user(), video);
        } catch (IOException e) {
            logger.logDebug(() -> "Failed to delete video fully: " + e);
            return Response.buildLeanResponse(CODE_500_INTERNAL_SERVER_ERROR);
        }

        auditor.audit(() -> String.format("user %s has deleted a video, url: %s, that was associated with person %d with a short description of %s",
                authResult.user().getUsername(),
                video.getVideoUrl(),
                personByVideo,
                video.getShortDescription()
                ), authResult.user());

        if (isPost) {
            Person person = findExactlyOne(personDb.values().stream(), x -> x.getIndex() == personByVideo);
            return Message.redirect("The video has been deleted", "/photos?personid=" + person.getId().toString());
        } else {
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_204_NO_CONTENT);
        }
    }

    /**
     * Receives text information of a photograph
     * @param isPost if true, this is being sent as a POST request, and we'll
     *               handle accordingly.  Otherwise, a request sent by Javascript.
     */
    public IResponse photoDescriptionUpdate(IRequest request, boolean isPost) {
        AuthResult authResult = auth.processAuth(request);
        if (!authResult.isAuthenticated()) {
            return Respond.redirectTo("/");
        }
        String updatedShortDescription = request.getBody().asString("caption");
        String updatedDescription = request.getBody().asString("long_description");
        long photoId;
        try {
            photoId = Long.parseLong(request.getBody().asString("photoid"));
        } catch (NumberFormatException ex) {
            logger.logDebug(() -> "User failed to include valid id of photograph. " + ex);
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
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
                    updatedDescription);
            photographDb.write(dataUpdate);

            if (isPost) {
                // get the person for this photo, so we can redirect to them.
                long personByPhoto = getPersonByPhoto(originalPhoto).getPersonIndex();
                Person person = findExactlyOne(personDb.values().stream(), x -> x.getIndex() == personByPhoto);

                return Message.redirect("The photo's description has been modified", "/photos?personid=" + person.getId().toString());
            } else {
                auditor.audit(() -> String.format("user %s has modified the description for photo id: %d", authResult.user().getUsername(), photoId), authResult.user());
                return Response.buildLeanResponse(CODE_204_NO_CONTENT);
            }
        } else {
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        }
    }

    /**
     * Receives text information of a video
     * @param isPost if true, this is being sent as a POST request, and we'll
     *               handle accordingly.  Otherwise, a request sent by Javascript.
     */
    public IResponse videoDescriptionUpdate(IRequest request, boolean isPost) {
        AuthResult authResult = auth.processAuth(request);
        if (!authResult.isAuthenticated()) {
            return Respond.redirectTo("/");
        }
        String updatedShortDescription = request.getBody().asString("caption");
        String updatedDescription = request.getBody().asString("long_description");
        long videoId;
        try {
            videoId = Long.parseLong(request.getBody().asString("videoid"));
        } catch (NumberFormatException ex) {
            logger.logDebug(() -> "User failed to include valid id of video. " + ex);
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }

        Video originalVideo = SearchUtils.findExactlyOne(videoDb.values().stream(), x -> x.getIndex() == videoId);
        if (originalVideo != null && originalVideo != Video.EMPTY) {
            // store the old data in the audit log
            enqueueVideoAudit(originalVideo);

            // update the video data
            Video dataUpdate = new Video(
                    originalVideo.getIndex(),
                    originalVideo.getVideoUrl(),
                    updatedShortDescription,
                    updatedDescription,
                    originalVideo.getPoster());
            videoDb.write(dataUpdate);

            if (isPost) {
                // get the person for this video, so we can redirect to them.
                long personByVideo = getPersonByVideo(originalVideo).getPersonIndex();
                Person person = findExactlyOne(personDb.values().stream(), x -> x.getIndex() == personByVideo);

                return Message.redirect("The video's description has been modified", "/photos?personid=" + person.getId().toString());
            } else {
                auditor.audit(() -> String.format("user %s has modified the description for video id: %d", authResult.user().getUsername(), videoId), authResult.user());
                return Response.buildLeanResponse(CODE_204_NO_CONTENT);
            }
        } else {
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        }

    }

    /**
     * Receives a new poster of a video
     * @param isPost if true, this is being sent as a POST request, and we'll
     *               handle accordingly.  Otherwise, a request sent by Javascript.
     */
    public IResponse setVideoPoster(IRequest request, boolean isPost) {
        AuthResult authResult = auth.processAuth(request);
        if (!authResult.isAuthenticated()) {
            return Respond.userInputError();
        }
        String posterValue = isPost ? request.getBody().asString("postervalue") : request.getBody().asString();
        long videoId;
        try {
            videoId = Long.parseLong(isPost ? request.getBody().asString("videoid") : request.getRequestLine().queryString().get("id"));
        } catch (NumberFormatException ex) {
            logger.logDebug(() -> "User failed to include valid id of video. " + ex);
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }

        Video originalVideo = SearchUtils.findExactlyOne(videoDb.values().stream(), x -> x.getIndex() == videoId);
        if (originalVideo != null && originalVideo != Video.EMPTY) {
            // store the old data in the audit log
            enqueueVideoAudit(originalVideo);

            // update the video data
            Video dataUpdate = new Video(
                    originalVideo.getIndex(),
                    originalVideo.getVideoUrl(),
                    originalVideo.getShortDescription(),
                    originalVideo.getDescription(),
                    posterValue);
            videoDb.write(dataUpdate);

            if (isPost) {
                // get the person for this video, so we can redirect to them.
                long personByVideo = getPersonByVideo(originalVideo).getPersonIndex();
                Person person = findExactlyOne(personDb.values().stream(), x -> x.getIndex() == personByVideo);

                return Message.redirect("The video's poster has been modified", "/photos?personid=" + person.getId().toString());
            } else {
                auditor.audit(() -> String.format("user %s has modified the poster for video id: %d", authResult.user().getUsername(), videoId), authResult.user());
                return Response.buildLeanResponse(CODE_204_NO_CONTENT);
            }
        } else {
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        }

    }
}
