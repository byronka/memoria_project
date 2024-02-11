package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.auth.AuthResult;
import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.featurelogic.persons.PersonEndpoints;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.inmra.utils.Respond;
import com.renomad.minum.Constants;
import com.renomad.minum.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.web.Request;
import com.renomad.minum.web.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.renomad.minum.utils.Invariants.mustNotBeNull;
import static com.renomad.minum.utils.SearchUtils.findExactlyOne;
import static com.renomad.minum.utils.StringUtils.safeHtml;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

public class ListPhotos {

    private final TemplateProcessor listPhotosTemplateProcessor;
    private final TemplateProcessor listAllPhotosTemplateProcessor;
    private final TemplateProcessor listAllPhotosCoreTemplateProcessor;
    private final ILogger logger;
    private final Path dbDir;

    private final IAuthUtils auth;
    private final Map<String, byte[]> lruCache;
    private final TemplateProcessor authHeader;
    private final PersonEndpoints personEndpoints;
    private final Constants constants;
    private final RenderPhotoRowsService renderPhotoRowsService;

    public ListPhotos(
            Context context,
            MemoriaContext memoriaContext,
            IAuthUtils auth,
            PersonEndpoints personEndpoints,
            Map<String, byte[]> lruCache,
            Db<PhotoToPerson> photoToPersonDb,
            Db<Photograph> photographDb
            ) {
        this.logger = context.getLogger();
        IFileUtils fileUtils = memoriaContext.fileUtils();
        this.constants = context.getConstants();
        this.dbDir = Path.of(constants.DB_DIRECTORY);
        listPhotosTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("listphotos/list_photos_template.html"));
        listAllPhotosTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("listphotos/list_all_photos_template.html"));
        listAllPhotosCoreTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("listphotos/list_all_photos_core_template.html"));
        this.auth = auth;
        this.lruCache = lruCache;
        authHeader = TemplateProcessor.buildProcessor(fileUtils.readTemplate("general/auth_header.html"));
        this.personEndpoints = personEndpoints;
        this.renderPhotoRowsService = new RenderPhotoRowsService(photoToPersonDb, photographDb, fileUtils);
    }

    public Response ListPhotosPageGet(Request r) {
        AuthResult authResult = auth.processAuth(r);
        if (!authResult.isAuthenticated()) {
            return Response.redirectTo("/");
        }
        String personId = r.requestLine().queryString().get("personid");
        if (personId == null || personId.isBlank()) {
            return listAllPhotos();
        } else {
            return listPhotosForPerson(personId);
        }
    }

    /**
     * This renders html where we expect to show photographs about
     * just one person.
     */
    private Response listPhotosForPerson(String personId) {
        Person foundPerson = findExactlyOne(personEndpoints.getPersonDb().values().stream(), x -> x.getId().toString().equals(personId));
        mustNotBeNull(foundPerson);

        String photoHtml = renderPhotoRowsService.renderPhotoRows(foundPerson);

        String listPhotosHtml = listPhotosTemplateProcessor.renderTemplate(Map.of(
                "header", authHeader.renderTemplate(Map.of("edit_this_person", "")),
                "person_id", personId,
                "person_name", safeHtml(foundPerson.getName()),
                "photo_html", photoHtml
        ));
        return Respond.htmlOk(listPhotosHtml);
    }

    /**
     * This is a specialty page to show ALL the photos in the system
     * categorized by their associated person
     */
    private Response listAllPhotos() {
        // loop through each person, creating one long list of photos,
        // with the associated person at the top of each section.
        var listAllPhotosCoreString = new StringBuilder();
        for (Person person : personEndpoints.getPersonDb().values()) {

            String photoHtml = renderPhotoRowsService.renderPhotoRows(person);

            listAllPhotosCoreString.append(listAllPhotosCoreTemplateProcessor.renderTemplate(Map.of(
                    "person_id", person.getId().toString(),
                    "person_name", safeHtml(person.getName()),
                    "photo_html", photoHtml
            ))).append("\n");
        }

        String listPhotosHtml = listAllPhotosTemplateProcessor.renderTemplate(Map.of(
                "header", authHeader.renderTemplate(Map.of("edit_this_person", "")),
                "list_all_photos_core", listAllPhotosCoreString.toString()
        ));
        return Respond.htmlOk(listPhotosHtml);
    }

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
    public Response grabPhotoGet(Request r) {

        // get the filename from the query string
        String filename = r.requestLine().queryString().get("name");

        logger.logDebug(() -> r.remoteRequester() + " is looking for a photo named " + filename);

        // if the name query is null or blank, return 404
        if (filename == null || filename.isBlank()) {
            return new Response(_404_NOT_FOUND);
        }

        // See docs/image_processing/README.md for more about this design
        Path photoPath;
        var sizeQuery = r.requestLine().queryString().get("size");

        if (sizeQuery == null) {
            sizeQuery = "";
        }

        // we default to the medium-sized files
        if (sizeQuery.equals("small")) {
            photoPath = dbDir.resolve("photo_files_thumbnail").resolve(filename);
        } else if (sizeQuery.equals("original")) {
            photoPath = dbDir.resolve("photo_files_original").resolve(filename);
        } else {
            photoPath = dbDir.resolve("photo_files_medium").resolve(filename);
        }

        // first, is it already in our cache?
        if (lruCache.containsKey(photoPath.toString())) {
            logger.logTrace(() -> "Found " + photoPath + " in the cache. Serving.");
            return new Response(_200_OK, lruCache.get(photoPath.toString()),
                    Map.of(
                            "Cache-Control","max-age=" + constants.STATIC_FILE_CACHE_TIME * 60 + ", immutable",
                            "Content-Type", "image/jpeg"
                    ));
        }

        // If the file has not been processed yet, oh well, return 404.  This is better than returning
        // the original file, which could be huge and would then get stuck in the
        // user's cache.
        if (! Files.exists(photoPath)) {
            logger.logDebug(() -> "User requested a filename of " + photoPath + " that does not exist in the directory");
            return new Response(_404_NOT_FOUND);
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
                return new Response(_404_NOT_FOUND);
            } else {
                String s = finalPhotoPath2 + " photo filesize was " + bytes.length + " bytes.";
                logger.logDebug(() -> s);

                logger.logDebug(() -> "Storing " + finalPhotoPath2 + " in the cache");
                lruCache.put(finalPhotoPath2.toString(), bytes);

                return new Response(_200_OK, bytes,
                        Map.of(
                                "Cache-Control","max-age=" + constants.STATIC_FILE_CACHE_TIME * 60,
                                "Content-Type", "image/jpeg"
                        ));

            }
        } catch (IOException e){
            logger.logAsyncError(() -> "There was an issue reading a file at " + finalPhotoPath2 + ". " + StacktraceUtils.stackTraceToString(e));
            return new Response(_500_INTERNAL_SERVER_ERROR);
        }
    }


}

