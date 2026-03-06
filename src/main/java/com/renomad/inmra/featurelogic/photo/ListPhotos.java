package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.auth.AuthResult;
import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.auth.PrivacyCheckStatus;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.featurelogic.persons.PersonEndpoints;
import com.renomad.inmra.featurelogic.persons.PersonLruCache;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.inmra.utils.NavigationHeader;
import com.renomad.inmra.utils.Respond;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.InvariantException;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.web.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.renomad.inmra.utils.FileUtils.badFilePathPatterns;
import static com.renomad.minum.utils.SearchUtils.findExactlyOne;
import static com.renomad.minum.utils.StringUtils.safeHtml;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

public class ListPhotos {

    private final TemplateProcessor listPhotosTemplateProcessor;
    private final TemplateProcessor photoTableTemplateProcessor;
    private final ILogger logger;
    private final NavigationHeader navigationHeader;
    private final Path dbDir;

    private final IAuthUtils auth;
    private final Map<String, byte[]> lruCache;
    private final PersonEndpoints personEndpoints;
    private final Constants constants;
    private final RenderPhotoRowsService renderPhotoRowsService;

    public ListPhotos(
            Context context,
            MemoriaContext memoriaContext,
            IAuthUtils auth,
            PersonEndpoints personEndpoints,
            Map<String, byte[]> lruCache,
            AbstractDb<PhotoToPerson> photoToPersonDb,
            AbstractDb<Photograph> photographDb,
            AbstractDb<VideoToPerson> videoToPersonDb,
            AbstractDb<Video> videoDb,
            AbstractDb<Person> personDb,
            NavigationHeader navigationHeader,
            PersonLruCache personLruCache) {
        this.logger = context.getLogger();
        this.navigationHeader = navigationHeader;
        IFileUtils fileUtils = memoriaContext.getFileUtils();
        this.constants = context.getConstants();
        this.dbDir = Path.of(constants.dbDirectory);
        listPhotosTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("listphotos/list_photos_template.html"));
        photoTableTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("listphotos/photo_table_template.html"));
        this.auth = auth;
        this.lruCache = lruCache;
        this.personEndpoints = personEndpoints;
        this.renderPhotoRowsService = new RenderPhotoRowsService(photoToPersonDb, photographDb, videoToPersonDb, videoDb, personDb, fileUtils, personLruCache);
    }

    /**
     * Render the photo list page
     */
    public IResponse listPhotosPageGet(IRequest r) {
        AuthResult authResult = auth.processAuth(r);
        if (!authResult.isAuthenticated()) {
            return Respond.redirectTo("/");
        }
        String personId = r.getRequestLine().queryString().get("personid");
        if (personId == null || personId.isBlank()) {
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        } else {
            return listPhotosForPerson(r, personId);
        }
    }

    /**
     * This renders html where we expect to show photographs about
     * just one person.
     */
    private IResponse listPhotosForPerson(IRequest r, String personId) {
        Person foundPerson = personEndpoints.getPersonDb().findExactlyOne("id", personId);
        if (foundPerson == null) {
            logger.logDebug(() -> "unable to find a person with a personId of " + personId);
            return Respond.userInputError();
        }

        String photoHtml = renderPhotoRowsService.renderPhotoRows(foundPerson);
        String myNavHeader = navigationHeader.renderNavigationHeader(r, true, true, personId, true, null);

        String photoTable = photoTableTemplateProcessor.renderTemplate(Map.of(
                "photo_html", photoHtml
        ));
        String listPhotosHtml = listPhotosTemplateProcessor.renderTemplate(Map.of(
                "navigation_header", myNavHeader,
                "person_id", personId,
                "person_name", safeHtml(foundPerson.getName()),
                "photo_table", photoTable
        ));
        return Respond.htmlOk(listPhotosHtml);
    }

    private final static Pattern IS_JPEG = Pattern.compile("(?i)\\.(jpg|jpeg)$");
    private final static Pattern IS_PNG = Pattern.compile("(?i)\\.(png)$");

    /**
     * Returns a particular photo.
     * <p>
     *     There's some horrendous complexity to this endpoint. Let me
     *     describe some of its requirements:
     * </p>
     * <p>
     *     For one, it's returning data to the user that they themselves
     *     provided us earlier, so there's some volatility at play - it
     *     might not correctly save.  In fact, they may have provided
     *     us a file that's corrupt or empty. And, it takes a bit for
     *     the data to save, so when they ask for it, it might not
     *     be on the disk yet.  Furthermore, because this is data
     *     on disk, there's all the complexity of getting any data
     *     on disk - the disk might fail, some kind of IO error, whatevers.
     * </p>
     * <p>
     *     Then on top of that, there's caching.  We start by trying to
     *     give them the data from the cache, but if it's not there
     *     already, we go through the trouble of reading it from disk
     *     and then add it to the cache.  The cache itself is a LRU-type
     *     cache - least recently used.  In other words, the pics we
     *     haven't shown in the longest time fall out of the cache.
     * </p>
     * <p>
     *     We're also caching on the browser! Yes,
     *     we send a header specifying that we want them to store this
     *     data for a week.
     * </p>
     * <p>
     *     And remember how I said there was a cache? Well, when we delete
     *     files, it's necessary to remove all the sizes of the same image
     *     from the cache.
     * </p>
     * <p>
     *     And naturally, if a user requests a file we don't recognize,
     *     we need to return a 404.  And if the requested size isn't recognized,
     *     we default to something.
     * </p>
     * <p>
     *     But wait, there's more! In order to have the fastest-possible
     *     read of the file, we use some shenanigans with channels and buffers.
     * </p>
     * <p>
     *     Grief.
     * </p>
     *
     */
    public IResponse grabPhotoGet(IRequest r) {

        // get the filename from the query string
        String filename = r.getRequestLine().queryString().get("name");

        logger.logDebug(() -> r.getRemoteRequester() + " is looking for a photo named " + filename);

        // if the name query is null or blank, return 404
        if (filename == null || filename.isBlank()) {
            return Response.buildLeanResponse(CODE_404_NOT_FOUND);
        }

        // if the user is sending characters that suggest hacking, return 404
        if (badFilePathPatterns.matcher(filename).find()) {
            logger.logDebug(() -> String.format("Bad path requested for grabPhotoGet: %s", filename));
            return Response.buildLeanResponse(CODE_404_NOT_FOUND);
        }

        // set the mime for the file we return appropriately
        String mime;
        if (IS_JPEG.matcher(filename).find()) {
            mime = "image/jpeg";
        } else if (IS_PNG.matcher(filename).find()) {
            mime = "image/png";
        } else {
            logger.logDebug(() -> "The requested photo was not a valid filetype: " + filename);
            return Response.buildLeanResponse(CODE_404_NOT_FOUND);
        }

        // See docs/image_processing/README.md for more about this design
        Path photoPath;
        var sizeQuery = r.getRequestLine().queryString().get("size");

        // set the sizequery to a non-null value to avoid null-pointer exceptions later
        if (sizeQuery == null) {
            sizeQuery = "";
        }

        // check the filename has valid characters.
        try {
            FileUtils.checkForBadFilePatterns(filename);
        } catch (InvariantException ex) {
            logger.logDebug(() -> "Error with photo filename. " + ex.getMessage());
            return Respond.userInputError();
        }

        // "archive" photos require authentication. If they aren't auth'd, just return 404.
        if (sizeQuery.equals("archive")) {
            PrivacyCheckStatus privacyCheckStatus = auth.canShowPrivateInformation(r);
            if (privacyCheckStatus.canShowPrivateInformation()) {
                return handleArchivePhoto(r, filename, mime);
            } else {
                logger.logDebug(() -> "User requested an archive file of " + filename + " but is not authenticated. Continuing with size set to original");
                sizeQuery = "original";
            }
        }

        // we default to the medium-sized files.
        photoPath = switch (sizeQuery) {
            case "small" -> dbDir.resolve("photo_files_thumbnail").resolve(filename);
            case "original" -> dbDir.resolve("photo_files_original").resolve(filename);
            case "icon" -> dbDir.resolve("photo_files_icon").resolve(filename);
            default -> dbDir.resolve("photo_files_medium").resolve(filename);
        };

        /*
        The "photo_archive" directory is a special case.  The files there can be
        huge (more than 5-10 megabytes in some cases).  So, we won't be storing
        those in the cache at all.  Also, we will only return the archive files
        if the user is authenticated. So it's a special case - all other image files
        can be downloaded like normal.
         */

        // first, is it already in our cache? (by the way, "archive" photos won't ever be in the cache)
        if (lruCache.containsKey(photoPath.toString())) {
            logger.logTrace(() -> "Found " + photoPath + " in the cache. Serving.");
            return Response.buildResponse(CODE_200_OK,
                    Map.of(
                            "Cache-Control","max-age=" + constants.staticFileCacheTime * 60 + ", immutable",
                            "Content-Type", mime
                    ),
                    lruCache.get(photoPath.toString()));
        }

        // It's not in the cache - but if it's also not in the folder, bail with a 404
        if (! Files.exists(photoPath)) {
            logger.logDebug(() -> "User requested a filename of " + photoPath + " that does not exist in the directory");
            return Response.buildLeanResponse(CODE_404_NOT_FOUND);
        }

        // otherwise, read the bytes
        Path finalPhotoPath2 = photoPath;
        logger.logDebug(() -> "about to read file at " + finalPhotoPath2);

        try (RandomAccessFile reader = new RandomAccessFile(finalPhotoPath2.toString(), "r");
             FileChannel channel = reader.getChannel();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            int bufferSize = 1024;
            if (bufferSize > channel.size()) {
                bufferSize = (int) channel.size();
            }
            ByteBuffer buff = ByteBuffer.allocate(bufferSize);

            while (channel.read(buff) > 0) {
                out.write(buff.array(), 0, buff.position());
                buff.clear();
            }

            byte[] bytes = out.toByteArray();
            if (bytes.length == 0) {
                logger.logDebug(() -> finalPhotoPath2 + " photo filesize was 0.  Sending 404");
                return Response.buildLeanResponse(CODE_404_NOT_FOUND);
            } else {
                String s = finalPhotoPath2 + " photo filesize was " + bytes.length + " bytes.";
                logger.logDebug(() -> s);

                // we won't store archive photos in the cache, they are too large
                logger.logDebug(() -> "Storing " + finalPhotoPath2 + " in the cache");
                lruCache.put(finalPhotoPath2.toString(), bytes);

                return Response.buildResponse(CODE_200_OK,
                        Map.of(
                                "Cache-Control","max-age=" + constants.staticFileCacheTime * 60,
                                "Content-Type", mime
                        ),
                        bytes);

            }
        } catch (IOException e){
            logger.logAsyncError(() -> "There was an issue reading a file at " + finalPhotoPath2 + ". " + StacktraceUtils.stackTraceToString(e));
            return Response.buildLeanResponse(CODE_500_INTERNAL_SERVER_ERROR);
        } catch (InvalidPathException ex) {
            logger.logDebug(() -> "Error reading photo file "+photoPath+" requested by user. " + ex.getMessage());
            return Respond.userInputError();
        } catch (Exception ex) {
            logger.logAsyncError(() -> "There was an issue reading a file at " + photoPath + ". " + StacktraceUtils.stackTraceToString(ex));
            return Response.buildLeanResponse(CODE_500_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * archive photos require special handling because they can be very large
     */
    private IResponse handleArchivePhoto(IRequest r, String filename, String mime) {

        String archivalPhoto = "";
        try {
            archivalPhoto = dbDir.resolve("photo_archive").resolve(filename).toString();

            var extraContentHeaders = Map.of(
                    "Cache-Control","max-age=" + constants.staticFileCacheTime * 60 + ", immutable",
                    "Content-Type", mime
            );
            return Response.buildLargeFileResponse(extraContentHeaders, archivalPhoto, r.getHeaders());
        } catch (IOException e){
            // this is the only branch in the logic where we adjust to use a size of "original" and pass
            // through to the following section of logic.  All the other branches handle edge cases that
            // are more appropriately handled with 400s and 500s.
            if (e.getClass().equals(NoSuchFileException.class)) {
                logger.logDebug(() -> "User requested an archive file of " + filename + " that was not found.  Returning 404 NOT FOUND");
                return Response.buildLeanResponse(CODE_404_NOT_FOUND);
            } else {
                String finalArchivalPhoto = archivalPhoto;
                logger.logAsyncError(() -> "There was an issue reading a file at " + finalArchivalPhoto + ". " + StacktraceUtils.stackTraceToString(e));
                return Response.buildLeanResponse(CODE_500_INTERNAL_SERVER_ERROR);
            }
        } catch (InvalidPathException ex) {
            String finalArchivalPhoto = archivalPhoto;
            logger.logDebug(() -> "Error reading photo file "+ finalArchivalPhoto +" requested by user. " + ex.getMessage());
            return Respond.userInputError();
        } catch (Exception ex) {
            String finalArchivalPhoto = archivalPhoto;
            logger.logAsyncError(() -> "There was an issue reading a file at " + finalArchivalPhoto + ". " + StacktraceUtils.stackTraceToString(ex));
            return Response.buildLeanResponse(CODE_500_INTERNAL_SERVER_ERROR);
        }
    }


    public IResponse grabVideoGet(IRequest r) {

        // get the filename from the query string
        String filename = r.getRequestLine().queryString().get("name");

        logger.logDebug(() -> r.getRemoteRequester() + " is looking for a video named " + filename);

        // if the name query is null or blank, return 404
        if (filename == null || filename.isBlank()) {
            return Response.buildLeanResponse(CODE_404_NOT_FOUND);
        }

        if (badFilePathPatterns.matcher(filename).find()) {
            logger.logDebug(() -> String.format("Bad path requested for grabVideoGet: %s", filename));
            return Response.buildLeanResponse(CODE_404_NOT_FOUND);
        }

        String videoFile = null;
        try {
            videoFile = dbDir.resolve("video_files").resolve(filename).toString();
            var extraContentHeaders = Map.of(
                    "Content-Type", "video/mp4"
            );
            return Response.buildLargeFileResponse(extraContentHeaders, videoFile, r.getHeaders());
        } catch (IOException e){

            if (e.getClass().equals(NoSuchFileException.class)) {
                return Response.buildLeanResponse(CODE_404_NOT_FOUND);
            } else {
                String finalVideoFile = videoFile;
                logger.logAsyncError(() -> "There was an issue reading a file at " + finalVideoFile + ". " + StacktraceUtils.stackTraceToString(e));
                return Response.buildLeanResponse(CODE_500_INTERNAL_SERVER_ERROR);
            }
        } catch (InvalidPathException ex) {
            String finalVideoFile = videoFile;
            logger.logDebug(() -> "Error reading video file "+finalVideoFile+" requested by user. " + ex.getMessage());
            return Respond.userInputError();
        } catch (Exception ex) {
            String finalVideoFile = videoFile;
            logger.logAsyncError(() -> "There was an issue reading a file at " + finalVideoFile + ". " + StacktraceUtils.stackTraceToString(ex));
            return Response.buildLeanResponse(CODE_500_INTERNAL_SERVER_ERROR);
        }

    }

    public IResponse listPhotosPagePhotoRowGet(IRequest r) {
        AuthResult authResult = auth.processAuth(r);
        if (!authResult.isAuthenticated()) {
            return Respond.redirectTo("/");
        }
        String photoId = r.getRequestLine().queryString().get("photoid");
        String personId = r.getRequestLine().queryString().get("personid");

        Person foundPerson = findExactlyOne(personEndpoints.getPersonDb().values().stream(), x -> x.getId().toString().equals(personId));
        if (foundPerson == null) {
            logger.logDebug(() -> "unable to find a person with a personId of " + personId);
            return Respond.userInputError();
        }

        long photoIdLong;
        if (photoId == null || photoId.isBlank()) {
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        } else {
            photoIdLong = Long.parseLong(photoId);
        }

        Set<Long> publishedPhotoIds = renderPhotoRowsService.determinePublishedPhotos(foundPerson);
        String renderedPhotoRow = renderPhotoRowsService.renderInnerPhotoRowString(foundPerson, List.of(photoIdLong), publishedPhotoIds);

        return Response.buildResponse(
                CODE_200_OK,
                Map.of("Content-Type", "text/html"),
                renderedPhotoRow);
    }

    public IResponse listPhotosPageVideoRowGet(IRequest r) {
        AuthResult authResult = auth.processAuth(r);
        if (!authResult.isAuthenticated()) {
            return Respond.redirectTo("/");
        }
        String videoId = r.getRequestLine().queryString().get("videoid");
        String personId = r.getRequestLine().queryString().get("personid");

        Person foundPerson = findExactlyOne(personEndpoints.getPersonDb().values().stream(), x -> x.getId().toString().equals(personId));
        if (foundPerson == null) {
            logger.logDebug(() -> "unable to find a person with a personId of " + personId);
            return Respond.userInputError();
        }

        long videoIdLong;
        if (videoId == null || videoId.isBlank()) {
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        } else {
            videoIdLong = Long.parseLong(videoId);
        }

        Set<Long> publishedVideoIds = renderPhotoRowsService.determinePublishedVideos(foundPerson);
        String renderedVideoRow = renderPhotoRowsService.renderInnerVideoRowString(foundPerson, List.of(videoIdLong), publishedVideoIds);

        return Response.buildResponse(
                CODE_200_OK,
                Map.of("Content-Type", "text/html"),
                renderedVideoRow);
    }

}

