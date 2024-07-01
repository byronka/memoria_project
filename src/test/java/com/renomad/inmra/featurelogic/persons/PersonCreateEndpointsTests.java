package com.renomad.inmra.featurelogic.persons;

import com.renomad.inmra.auth.AuthHeader;
import com.renomad.inmra.auth.AuthResult;
import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.auth.User;
import com.renomad.inmra.featurelogic.photo.PhotoService;
import com.renomad.inmra.featurelogic.photo.PhotoToPerson;
import com.renomad.inmra.featurelogic.photo.Photograph;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.state.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.queue.ActionQueueKiller;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.LRUCache;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.web.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.buildTestingContext;

public class PersonCreateEndpointsTests {
    private static Context context;
    private static TestLogger logger;
    private static String defaultRemoteRequester;
    private static Headers fakeHeaders;
    private static RequestLine fakeStartLine;
    private static Db<Person> personDb;
    private static PersonCreateEndpoints personCreateEndpoints;
    private static UUID defaultPersonId;
    private static String uuidString;
    private static com.renomad.minum.utils.FileUtils minumFileUtils;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("unit_tests");
        var memoriaContext = MemoriaContext.buildMemoriaContext(context);
        minumFileUtils = new FileUtils(context.getLogger(), context.getConstants());
        logger = (TestLogger)context.getLogger();
        defaultRemoteRequester = "";

        IAuthUtils fakeAuth = makeFakeAuthUtils();
        fakeHeaders = new Headers(List.of());
        fakeStartLine = RequestLine.empty();
        personDb = context.getDb("personcreateendpointstests_deleting_user_birthdate", Person.EMPTY);
        var photoDb = context.getDb("personcreateendpointstests_photodb", Photograph.EMPTY);
        var photoToPersonDb = context.getDb("personcreateendpointstests_photo_to_person", PhotoToPerson.EMPTY);
        Map<String, byte[]> photoLruCache = LRUCache.getLruCache();
        var photoService = new PhotoService(context, memoriaContext, photoDb, photoLruCache, photoToPersonDb, personDb, fakeAuth);
        var authHeader = new AuthHeader(fakeAuth, memoriaContext);
        PersonEndpoints fakePersonEndpoints = new PersonEndpoints(context, memoriaContext, personDb, fakeAuth, photoToPersonDb, photoDb, photoService, authHeader);
        personCreateEndpoints = new PersonCreateEndpoints(context, memoriaContext, fakeAuth, fakePersonEndpoints);
        uuidString = "31f40bdc-aca6-4f23-93b6-955291937f4d";
        defaultPersonId = UUID.fromString(uuidString);
    }

    @AfterClass
    public static void tearDownClass() {
        MyThread.sleep(50);
        new ActionQueueKiller(context).killAllQueues();
        context.getExecutorService().shutdownNow();
        context.getLogger().stop();
    }


    /*
     I noticed that when I added a birthdate to someone, and
     then deleted it afterwards, the system was not correctly
     deleting that data.
     */
    @Test
    public void test_UserBirthdate_Delete() {
        //have to set up some state first.

        Person person = new Person(
                0L,
                defaultPersonId,
                "testPerson",
                new Date(2023, Month.JANUARY, 4), Date.EMPTY);
        try {
            // add that person to our database
            personDb.write(person);
            // create an incoming request, with a blank value for
            // the born_input (implying we want to remove the born value)
            var bodyWithDeletedBirthdate = new Body(
                    Map.of(
                            "id", uuidString.getBytes(),
                            "name_input", "name does not matter".getBytes(),
                            "born_input", "".getBytes()),
                    new byte[0],
                    Map.of()
            );
            var request = new Request(
                    fakeHeaders,
                    fakeStartLine,
                    bodyWithDeletedBirthdate,
                    defaultRemoteRequester);

            // handle the request
            personCreateEndpoints.editPersonPost(request);

            // check that the person in the database has an empty value for their born date
            Person resultPerson = personDb.values().stream().toList().get(0);
            assertEquals(resultPerson.getBirthday(), Date.EMPTY);
            minumFileUtils.deleteDirectoryRecursivelyIfExists(
                    Path.of("out").resolve("personcreateendpointstests_deleting_user_birthdate"),
                    logger);
        } finally {
            personDb.delete(person);
        }
    }


    /**
     * Create a mocked-out IAuthUtils.  Our tests merely needs this so
     * we can move on to test the later code past checking for a valid authentication
     */
    private static IAuthUtils makeFakeAuthUtils() {
        return new IAuthUtils() {
            @Override
            public AuthResult processAuth(Request request) {
                LocalDateTime ldt = LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 1, 1);
                return new AuthResult(true, ldt.toInstant(ZoneOffset.UTC), User.EMPTY);
            }

            @Override
            public String getForbiddenPage() {
                return null;
            }

            @Override
            public Response htmlForbidden() {
                return null;
            }
        };
    }
}
