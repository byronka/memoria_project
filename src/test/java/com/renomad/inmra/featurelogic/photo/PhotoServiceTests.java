package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.auth.*;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.IFileWriteStringWrapper;

import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.LRUCache;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.web.*;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.renomad.inmra.featurelogic.photo.PhotoService.determineSuffixByContentType;
import static com.renomad.minum.testing.TestFramework.*;

public class PhotoServiceTests {

    private static TestLogger logger;
    private static Path thumbnailPhotos;
    private static Path mediumPhotos;
    private static Path originalPhotos;
    private static PhotoService photoService;
    private static Context context;
    private AuthResult authResult;
    private Body requestBody;
    private RequestLine requestLine;

    @BeforeClass
    public static void init() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("DB_DIRECTORY", "target/simple_db_for_photo_service_tests");
        context = buildTestingContext("PhotoServiceTests", properties);
        logger = (TestLogger)context.getLogger();
        FileUtils fileUtils = new FileUtils(logger, context.getConstants());

        thumbnailPhotos = Path.of("target/simple_db_for_photo_service_tests/photo_files_thumbnail");
        mediumPhotos = Path.of("target/simple_db_for_photo_service_tests/photo_files_medium");
        originalPhotos = Path.of("target/simple_db_for_photo_service_tests/photo_files_original");

        fileUtils.deleteDirectoryRecursivelyIfExists(thumbnailPhotos);
        fileUtils.deleteDirectoryRecursivelyIfExists(mediumPhotos);
        fileUtils.deleteDirectoryRecursivelyIfExists(originalPhotos);

        fileUtils.makeDirectory(thumbnailPhotos);
        fileUtils.makeDirectory(mediumPhotos);
        fileUtils.makeDirectory(originalPhotos);
    }

    @AfterClass
    public static void cleanup() {
        TestFramework.shutdownTestingContext(context);
    }

    @Before
    public void before() {
        var memoriaContext = MemoriaContext.buildMemoriaContext(context);
        AbstractDb<Photograph> photoDb = context.getDb("photos", Photograph.EMPTY);
        AbstractDb<Video> videoDb = context.getDb("videos", Video.EMPTY);
        AbstractDb<PhotoToPerson> photoToPersonDb = context.getDb("photo_to_person", PhotoToPerson.EMPTY);
        AbstractDb<VideoToPerson> videoToPersonDb = context.getDb("video_to_person", VideoToPerson.EMPTY);
        AbstractDb<Person> personDb = context.getDb("persons", Person.EMPTY);
        IAuthUtils au = new IAuthUtils(){
            @Override public AuthResult processAuth(IRequest request) {return authResult;}
            @Override public String getForbiddenPage() {return null;}
            @Override public IResponse htmlForbidden() {return null;}
            @Override public PrivacyCheckStatus canShowPrivateInformation(IRequest request) { return null; }
        };
        Map<String, byte[]> photoLruCache = LRUCache.getLruCache();
        photoService = new PhotoService(context, memoriaContext, photoDb, videoDb, photoLruCache, photoToPersonDb, videoToPersonDb, personDb, au);
    }

    /**
     * If the provided mime type is not one we recognize, make that clearer
     * for future maintenance by setting its suffix to "unfamiliar_mime_type"
     */
    @Test
    public void testUnfamiliarMimeType() {
        String suffix = determineSuffixByContentType(List.of("foo"), logger);
        assertEquals(suffix, ".unfamiliar_mime_type");
    }

    /**
     * If no mime type is provided, we'll set the suffix to "unknown"
     */
    @Test
    public void testMissingMimeType() {
        String suffix = determineSuffixByContentType(List.of(), logger);
        assertEquals(suffix, ".unknown");
    }

    /**
     * If we recognize the mime type, set the suffix appropriately
     */
    @Test
    public void testKnownMimeType() {
        String suffix = determineSuffixByContentType(List.of("image/png"), logger);
        assertEquals(suffix, ".png");
        suffix = determineSuffixByContentType(List.of("image/jpeg"), logger);
        assertEquals(suffix, ".jpg");
    }

    /**
     * This tests a function that will check if files exist in a few
     * directories before moving on.
     */
    @Test
    public void testWaitUntilPhotosConverted() throws IOException {
        Path dbDir = Path.of("target/simple_db_for_photo_service_tests");
        Files.writeString(thumbnailPhotos.resolve("abc123.jpg"), "testing...");
        Files.writeString(mediumPhotos.resolve("abc123.jpg"), "testing...");
        Files.writeString(originalPhotos.resolve("abc123.jpg"), "testing...");
        // let the disk settle down
        MyThread.sleep(50);
        StopwatchUtils stopwatchUtils = new StopwatchUtils();
        stopwatchUtils.startTimer();

        PhotoService.photoWaiter("abc123.jpg", 10, 50, dbDir, logger);

        long timeTaken = stopwatchUtils.stopTimer();
        // the maximum time to wait would be 10 * 50 = 500, we should get here before that
        assertTrue(timeTaken < 100);
    }

    /**
     * Here, the method never finds the photos
     */
    @Test
    public void testWaitUntilPhotosConverted_NegativeCase() {
        Path dbDir = Path.of("target/simple_db_for_photo_service_tests");
        StopwatchUtils stopwatchUtils = new StopwatchUtils();
        stopwatchUtils.startTimer();

        // since "does_not_exist" will never show up in the directories, this will
        // bail after waiting.
        PhotoService.photoWaiter("does_not_exist", 4, 30, dbDir, logger);

        long timeTaken = stopwatchUtils.stopTimer();
        assertTrue(timeTaken > 100);
    }

    /**
     * If we fail to build the directories, should thrown an exception
     */
    @Test
    public void testBuildingNecessaryDirectories_EdgeCase_CreationFails() {
        IFileUtils fileUtils = buildMockFileUtilsThatThrows();
        Path path = Path.of("");

        PhotoService.buildNecessaryDirectories(fileUtils, path, path, path, path, path, path, path, path, path, path, path, path, logger);

        assertTrue(logger.doesMessageExist("Hi, the directory creation failed"));
    }

    /**
     * A {@link com.renomad.inmra.utils.FileUtils} that always throws
     * an exception from {@link com.renomad.inmra.utils.FileUtils#makeDirectory(Path)}
     */
    private static IFileUtils buildMockFileUtilsThatThrows() {
        return new IFileUtils() {

            @Override
            public void makeDirectory(Path path) throws IOException {
                throw new IOException("Hi, the directory creation failed.");
            }

            @Override
            public String readTemplate(String path) {
                return null;
            }
        };
    }

    @Test
    public void testStoringPhotoToAudit() throws Exception {
        IFileWriteStringWrapper fileWriteStringWrapper = (path, csq, options) -> {
            throw new IOException("foo foo did a foo");
        };
        PhotoService.storePhotoAudit(
                Photograph.EMPTY,
                Path.of(""),
                logger,
                fileWriteStringWrapper).run();
        assertTrue(logger.doesMessageExist("foo foo did a foo"));
    }


    /**
     * If the user sends an unauthenticated request to delete a video, they should
     * get a 401 UNAUTHORIZED response
     */
    @Test
    public void testUnauth() {
        var request = getDefaultRequest();
        authResult = new AuthResult(false, Instant.MIN, User.EMPTY);

        var response = photoService.videoDelete(request, false);

        var statusCode = response.getStatusCode();
        assertEquals(statusCode, StatusLine.StatusCode.CODE_401_UNAUTHORIZED);
    }

    private IRequest getDefaultRequest() {
        return new IRequest() {
            // no headers here means the user won't be considered authenticated - there's no cookies
            // to review.
            @Override public Headers getHeaders() {return null;}
            @Override public RequestLine getRequestLine() {return requestLine;}
            @Override public Body getBody() {return requestBody;}
            @Override public String getRemoteRequester() {return null;}
            @Override public ISocketWrapper getSocketWrapper() {return null;}
            @Override public Iterable<UrlEncodedKeyValue> getUrlEncodedIterable() {return null;}
            @Override public Iterable<StreamingMultipartPartition> getMultipartIterable() {return null;}
            @Override public boolean hasAccessedBody() {return false;}
        };
    }

    /**
     * If the user sends an invalid videoId (a number), like "abc", the
     * system will return 400 bad request.
     * <br>
     * This is to test a POST
     */
    @Test
    public void testInvalidVideoId_Post() {
        var request = getDefaultRequest();
        authResult = new AuthResult(true, Instant.MIN, User.EMPTY);
        requestBody = new Body(Map.of("videoid", "abc".getBytes(StandardCharsets.UTF_8)), new byte[0], List.of(), BodyType.FORM_URL_ENCODED);

        var response = photoService.videoDelete(request, true);

        var statusCode = response.getStatusCode();
        assertEquals(statusCode, StatusLine.StatusCode.CODE_400_BAD_REQUEST);
    }

    /**
     * If the user sends an invalid videoId (a number), like "abc", the
     * system will return 400 bad request.
     */
    @Test
    public void testInvalidVideoId() {
        var request = getDefaultRequest();
        authResult = new AuthResult(true, Instant.MIN, User.EMPTY);
        requestLine = new RequestLine(
                RequestLine.Method.DELETE,
                new PathDetails("", "", Map.of("id", "abc")),
                HttpVersion.ONE_DOT_ONE,
                "",
                logger);

        var response = photoService.videoDelete(request, false);

        var statusCode = response.getStatusCode();
        assertEquals(statusCode, StatusLine.StatusCode.CODE_400_BAD_REQUEST);
    }

    /**
     * If the user requests to delete a video that isn't in our
     * database, a 400 should be returned.
     */
    @Test
    public void testInvalidVideoId_NotInDatabase() {
        var request = getDefaultRequest();
        authResult = new AuthResult(true, Instant.MIN, User.EMPTY);
        requestBody = new Body(Map.of("videoid", "999".getBytes(StandardCharsets.UTF_8)), new byte[0], List.of(), BodyType.FORM_URL_ENCODED);

        var response = photoService.videoDelete(request, true);

        var statusCode = response.getStatusCode();
        assertEquals(statusCode, StatusLine.StatusCode.CODE_400_BAD_REQUEST);
    }
}
