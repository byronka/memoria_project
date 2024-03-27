package com.renomad.inmra.featurelogic;

import com.renomad.inmra.TheRegister;
import com.renomad.inmra.utils.FileUtils;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.Context;
import com.renomad.minum.htmlparsing.HtmlParseNode;
import com.renomad.minum.htmlparsing.TagName;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.testing.RegexUtils;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.web.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.web.StatusLine.StatusCode.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This test is called after the testing framework has started
 * the whole system.  We can now talk to the server like a regular user.
 */
public class FunctionalTests {

    private static TestLogger logger;
    private static Context context;
    private static FunctionalTesting ft;
    private static IFileUtils fileUtils;

    @BeforeClass
    public static void init() throws IOException {
        Properties properties = com.renomad.minum.Constants.getConfiguredProperties();
        properties.setProperty("DB_DIRECTORY", "target/simple_db_for_tests");
        context = buildTestingContext("_integration_test", properties);
        context.getFileUtils().deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().dbDirectory), context.getLogger());

        // override the COUNT_OF_PHOTO_CHECKS since our functional tests don't rely
        // on photo conversion and there's no sense in waiting a while for nothing.
        Properties memoriaProperties = com.renomad.inmra.utils.Constants.getConfiguredProperties();
        memoriaProperties.setProperty("COUNT_OF_PHOTO_CHECKS", "0");
        com.renomad.inmra.utils.Constants constants = new com.renomad.inmra.utils.Constants(memoriaProperties);

        fileUtils = new FileUtils(context.getFileUtils(), constants);
        var memoriaContext = new MemoriaContext(constants, fileUtils);
        new FullSystem(context).start();
        new TheRegister(context, memoriaContext).registerDomains();
        logger = (TestLogger) context.getLogger();
        ft = new FunctionalTesting(context, "localhost", 8080);
    }

    @AfterClass
    public static void cleanup() {
        var fs = context.getFullSystem();
        fs.shutdown();
        context.getLogger().stop();
        context.getExecutorService().shutdownNow();
    }

    /**
     * In these tests, the application has started up and is available
     * at http://localhost:8080.  These tests use non-TLS to keep things
     * a bit simpler.
     * <br>
     * Also note: these tests affect state, so the order of actions is
     * stuck.
     */
    @Test
    public void test_FullEndToEnd() throws Exception {

        logger.test("GET the home page, confirm we see a search field and a logo image"); {
            var response = ft.get("");
            assertFalse(response.searchOne(TagName.LABEL, Map.of("for", "search_by_name")) == HtmlParseNode.EMPTY);
            assertFalse(response.searchOne(TagName.A, Map.of("id", "logo")) == HtmlParseNode.EMPTY);
        }

        logger.test("GET editpersons unauth, expect failure page"); {
            var response = ft.get("editpersons");
            assertEquals(response.statusLine().status(), CODE_403_FORBIDDEN);
        }

        logger.test("GET login page, confirm the username and password fields are there"); {
            var response = ft.get("login");
            assertFalse(response.searchOne(TagName.LABEL, Map.of("for", "username")) == HtmlParseNode.EMPTY);
            assertFalse(response.searchOne(TagName.LABEL, Map.of("for", "password")) == HtmlParseNode.EMPTY);
        }

        String cookieHeader = loginAndGetCookie();

        logger.test("GET editperson, auth'd, expect fields like name and the submit button in the form"); {
            var response = ft.get("editperson", List.of(cookieHeader));
            assertFalse(response.searchOne(TagName.LABEL, Map.of("for", "name_input")) == HtmlParseNode.EMPTY);
            assertFalse(response.searchOne(TagName.LABEL, Map.of("for", "parents_input")) == HtmlParseNode.EMPTY);
        }

        String aliceUrl;
        logger.test("POST a new person, alice, auth'd"); {
            String payload = "id=&image_input=&name_input=Alice+Katz&born_input=1921-11-21&died_input=2020-03-12&siblings_input=Florence&spouses_input=&parents_input=Robert+and+Ethel&children_input=ron&biography_input=%3Cp%3EEllis+was+born+21+November%2C+1921+in+Atlanta%2C+GA.+As+his+Dad+was+a%3C%2Fp%3E";
            var response = ft.post("editperson", payload, List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_303_SEE_OTHER);
            aliceUrl = response.headers().valueByKey("location").get(0);
        }
        String aliceId = RegexUtils.find("id=(?<aliceid>.*)", aliceUrl, "aliceid");

        logger.test("GET the detail view of a person");
        {
            var response = ft.get("person?id=" + aliceId);
            assertEquals(response.searchOne(TagName.H2, Map.of("class","lifespan-name")).innerText().trim(), "Alice Katz");
            assertEquals(response.searchOne(TagName.SPAN, Map.of("class","lifespan-era")).innerText().trim(), "November 21, 1921 to March 12, 2020 (98 years)");
        }

        logger.test("GET the detail view of a person, negative case - bad id");
        {
            var response = ft.get("person?id=" + "abc123");
            assertEquals(response.statusLine().status(), CODE_404_NOT_FOUND);
        }

        logger.test("POST a new person, unauth'd"); {
            String payload = "id=&image_input=&name_input=byron";
            var response = ft.post("editperson", payload);
            assertEquals(response.statusLine().status(), CODE_403_FORBIDDEN);
        }

        // "persons" page - show the data for all persons.

        logger.test("GET editpersons auth'd, expect to find Alice."); {
            var response = ft.get("editpersons", List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_200_OK);
            var aliceResult = response.search(TagName.SPAN, Map.of("class", "name")).get(0);
            assertEquals(aliceResult.innerContent().get(0).textContent().trim(), "Alice Katz");
        }

        logger.test("GET editpersons auth'd again, should use cache, expect to find Alice."); {
            var response = ft.get("editpersons", List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_200_OK);
            var aliceResult = response.search(TagName.SPAN, Map.of("class", "name")).get(0);
            assertEquals(aliceResult.innerContent().get(0).textContent().trim(), "Alice Katz");
        }

        logger.test("POST some new persons, to assist in the sorting test"); {
            String alfonsoPayload = "id=&image_input=&name_input=Alfonso&born_input=1925-9-1&died_input=2005-07-22&siblings_input=&spouses_input=&parents_input=&children_input=&biography_input=";
            ft.post("editperson", alfonsoPayload, List.of(cookieHeader));
            String berenicePayload = "id=&image_input=&name_input=Berenice&born_input=1935-1-4&died_input=1975-03-18&siblings_input=&spouses_input=&parents_input=&children_input=&biography_input=";
            ft.post("editperson", berenicePayload, List.of(cookieHeader));
            String montsePayload = "id=&image_input=&name_input=Montse&born_input=1985-5-19&died_input=8&siblings_input=&spouses_input=&parents_input=&children_input=&biography_input=";
            ft.post("editperson", montsePayload, List.of(cookieHeader));
        }

        // testing the sorting on the persons page

        logger.test("GET editpersons sorted by birthday, ascending"); {
            var response = ft.get("editpersons?sort=bda", List.of(cookieHeader));
            assertEquals(response.searchOne(TagName.P, Map.of("id", "current_sort")).innerText(), "Current sort: Birthday, ascending");
            List<HtmlParseNode> names = response.search(TagName.SPAN, Map.of("class", "name"));
            assertEquals(names.get(0).innerText().trim(), "Alice Katz");
            assertEquals(names.get(1).innerText().trim(), "Alfonso");
            assertEquals(names.get(2).innerText().trim(), "Berenice");
        }

        logger.test("GET editpersons sorted by birthday, descending"); {
            var response = ft.get("editpersons?sort=bdd", List.of(cookieHeader));
            assertEquals(response.searchOne(TagName.P, Map.of("id", "current_sort")).innerText(), "Current sort: Birthday, descending");
            List<HtmlParseNode> names = response.search(TagName.SPAN, Map.of("class", "name"));
            assertEquals(names.get(0).innerText().trim(), "Montse");
            assertEquals(names.get(1).innerText().trim(), "Berenice");
            assertEquals(names.get(2).innerText().trim(), "Alfonso");
        }

        logger.test("GET editpersons sorted by deathday, ascending"); {
            var response = ft.get("editpersons?sort=dda", List.of(cookieHeader));
            assertEquals(response.searchOne(TagName.P, Map.of("id", "current_sort")).innerText(), "Current sort: Deathday, ascending");
            List<HtmlParseNode> names = response.search(TagName.SPAN, Map.of("class", "name"));
            assertEquals(names.get(0).innerText().trim(), "Montse");
            assertEquals(names.get(1).innerText().trim(), "Berenice");
            assertEquals(names.get(2).innerText().trim(), "Alfonso");
            assertEquals(names.get(3).innerText().trim(), "Alice Katz");
        }

        logger.test("GET editpersons sorted by deathday, descending"); {
            var response = ft.get("editpersons?sort=ddd", List.of(cookieHeader));
            assertEquals(response.searchOne(TagName.P, Map.of("id", "current_sort")).innerText(), "Current sort: Deathday, descending");
            List<HtmlParseNode> names = response.search(TagName.SPAN, Map.of("class", "name"));
            assertEquals(names.get(0).innerText().trim(), "Alice Katz");
            assertEquals(names.get(1).innerText().trim(), "Alfonso");
            assertEquals(names.get(2).innerText().trim(), "Berenice");
            assertEquals(names.get(3).innerText().trim(), "Montse");
        }

        logger.test("GET editpersons sorted by name, ascending"); {
            var response = ft.get("editpersons?sort=na", List.of(cookieHeader));
            assertEquals(response.searchOne(TagName.P, Map.of("id", "current_sort")).innerText(), "Current sort: Name, ascending");
            List<HtmlParseNode> names = response.search(TagName.SPAN, Map.of("class", "name"));
            assertEquals(names.get(0).innerText().trim(), "Alfonso");
            assertEquals(names.get(1).innerText().trim(), "Alice Katz");
            assertEquals(names.get(2).innerText().trim(), "Berenice");
            assertEquals(names.get(3).innerText().trim(), "Montse");
        }

        logger.test("GET editpersons sorted by name, descending"); {
            var response = ft.get("editpersons?sort=nd", List.of(cookieHeader));
            assertEquals(response.searchOne(TagName.P, Map.of("id", "current_sort")).innerText(), "Current sort: Name, descending");
            List<HtmlParseNode> names = response.search(TagName.SPAN, Map.of("class", "name"));
            assertEquals(names.get(0).innerText().trim(), "Montse");
            assertEquals(names.get(1).innerText().trim(), "Berenice");
            assertEquals(names.get(2).innerText().trim(), "Alice Katz");
            assertEquals(names.get(3).innerText().trim(), "Alfonso");
        }

        // don't currently have much way to add photos per person in this test, so we'll take a cheap shortcut.

        logger.test("GET editpersons sorted by photo count, ascending"); {
            var response = ft.get("editpersons?sort=pca", List.of(cookieHeader));
            assertEquals(response.searchOne(TagName.P, Map.of("id", "current_sort")).innerText(), "Current sort: Photos count, ascending");
        }

        logger.test("GET editpersons sorted by photo count, descending"); {
            var response = ft.get("editpersons?sort=pcd", List.of(cookieHeader));
            assertEquals(response.searchOne(TagName.P, Map.of("id", "current_sort")).innerText(), "Current sort: Photos count, descending");
        }


        logger.test("When we edit a person, it shows their current details in every field"); {
            var response = ft.get("editperson?id=" + aliceId, List.of(cookieHeader));
            assertEquals(response.searchOne(TagName.INPUT, Map.of("id", "name_input")).tagInfo().attributes().get("value"), "Alice Katz");
        }

        // rename Alice to Foo
        logger.test("Edit a person, Alice, auth'd"); {
            String payload = "id="+aliceId+"&image_input=&name_input=Foo&born_input=&died_input=&siblings_input=&spouses_input=&parents_input=&children_input=&biography_input=";
            var response = ft.post("editperson", payload, List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_303_SEE_OTHER);
        }

        /*
         *******************************************
         **         LOGOUT                        **
         *******************************************
         */

        logger.test("POST logout auth'd"); {
            ft.post("logout", "", List.of(cookieHeader));
        }

        logger.test("GET editpersons auth'd, expect 403"); {
            var response = ft.get("editpersons", List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_403_FORBIDDEN);
        }

        logger.test("grab the photos page unauthenticated - but no .. only auth users can see it.");
        assertEquals(ft.get("photos").statusLine().status(), StatusLine.StatusCode.CODE_303_SEE_OTHER);

        logger.test("Go to the login page, unauthenticated");
        assertEquals(ft.get("login").statusLine().status(), StatusLine.StatusCode.CODE_200_OK);

        logger.test("check out what's on the photos page now, unauthenticated");
        var response2 = ft.get("photos");
        var htmlResponse = response2.body().asString();
        String photoSrc = RegexUtils.find("photo\\?name=[a-z0-9\\-]*", htmlResponse);

        logger.test("look at the contents of a particular photo, unauthenticated");
        ft.get(photoSrc, List.of(cookieHeader));

        logger.test("logout");
        assertEquals(ft.post("logout", "", List.of(cookieHeader)).statusLine().status(), StatusLine.StatusCode.CODE_303_SEE_OTHER);

        logger.test("if we try to upload a photo unauth, we're prevented");
        assertEquals(ft.post("upload", "foo=bar").statusLine().status(), CODE_403_FORBIDDEN);

        logger.test("a person should be disallowed from viewing the page for creating persons, while unauth'd"); {
            var response = ft.get("editperson");
            assertEquals(response.statusLine().status(), CODE_403_FORBIDDEN);
        }

    }

    /**
     * Some very basic examination of the admin page
     */
    @Test
    public void testAdminPage() throws IOException {
        String cookieHeader = loginAndGetCookie();

        logger.test("GET the admin page, auth'd, expect to see a list of inmates in the brig and authenticated users"); {
            var response = ft.get("admin", List.of(cookieHeader));
            assertFalse(response.searchOne(TagName.LABEL, Map.of("for", "users")) == HtmlParseNode.EMPTY);
            assertFalse(response.searchOne(TagName.LABEL, Map.of("for", "sessions")) == HtmlParseNode.EMPTY);
            assertFalse(response.searchOne(TagName.LABEL, Map.of("for", "inmates")) == HtmlParseNode.EMPTY);
        }

        logger.test("POST logout auth'd"); {
            ft.post("logout", "", List.of(cookieHeader));
        }
    }

    /**
     * Basic examination of the LetsEncrypt endpoint.
     * The way this works is that a separate program, "certbot", will save a random
     * file to the disk, and communicate with an external service about it.  That
     * external service will then reach out to us and ask us to see the contents
     * of that file.  That is how it knows we own this domain, "inmra.com".  Then,
     * certbot reaches back out to the service and asks how it went, and creates
     * a proper cert if everything went well.
     * <br>
     * In this test, we will actually play out a *failed* attempt, just because
     * that is a little easier and stabler to test - this all depends on a certain
     * file existing at a certain directory, so we may as well test the failure case.
     */
    @Test
    public void testLetsEncrypt() throws IOException {
        // fail to include the challenge value
        var firstResponse = ft.get(".well-known/acme-challenge");
        assertEquals(firstResponse.statusLine().status(), CODE_400_BAD_REQUEST);

        // include an attack file - like, trying to get into a parent directory
        var attackResponse = ft.get(".well-known/acme-challenge/..hello");
        assertEquals(attackResponse.statusLine().status(), CODE_400_BAD_REQUEST);

        String failureToken = "foobar";
        var failureResponse = ft.get(".well-known/acme-challenge/" + failureToken);
        assertEquals(failureResponse.statusLine().status(), CODE_500_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void testListingPersons() throws IOException {
        var response = ft.get("index?search=foo");
        assertTrue(response.body().asString().contains("No persons found"));
    }

    /**
     * A basic test to check the "list all photos" page
     */
    @Test
    public void testListAllPhotos() throws IOException {
        // cannot see this page unauth'd
        var response = ft.get("photos");
        assertEquals(response.statusLine().status(), CODE_303_SEE_OTHER);


        String cookieHeader = loginAndGetCookie();

        var authResponse = ft.get("photos", List.of(cookieHeader));
        HtmlParseNode titleElement = authResponse.searchOne(TagName.TITLE, Map.of());
        assertEquals(titleElement.innerText(), "List Photos | Inmra");
    }

    /**
     * Examine some of the behavior for getting a photo
     */
    @Test
    public void testGetPhoto() throws IOException {
        // if we pass in an empty string for the id of a photo, get a 404
        var photoResponse = ft.get("photo?name=");
        assertEquals(photoResponse.statusLine().status(), CODE_404_NOT_FOUND);

        // if we pass in an id of a photo that is unrecognized, get a 404
        var photoResponse2 = ft.get("photo?name=foo");
        assertEquals(photoResponse2.statusLine().status(), CODE_404_NOT_FOUND);

        // if we ask for a photo that has 0 bytes, get a 404
        Path path = Path.of("target/simple_db_for_tests/photo_files_medium/bar.jpg");
        Path pathOrginal = Path.of("target/simple_db_for_tests/photo_files_original/bar.jpg");
        fileUtils.makeDirectory(Path.of("target/simple_db_for_tests/photo_files_medium"));
        try {
            Files.write(path, new byte[0]);
            // wait for the operating system to finish receiving the file to disk
            MyThread.sleep(30);
            var photoResponse3 = ft.get("photo?name=bar");
            assertEquals(photoResponse3.statusLine().status(), CODE_404_NOT_FOUND);
        } finally {
            Files.delete(path);
            // wait for the operating system to do its stuff
            MyThread.sleep(20);
        }
        fileUtils.makeDirectory(Path.of("target/simple_db_for_tests/photo_files_original"));
        try {
            Files.write(path, new byte[0]);
            // wait for the operating system to finish receiving the file to disk
            MyThread.sleep(30);
            var photoResponse3 = ft.get("photo?name=bar.jpg");
            assertEquals(photoResponse3.statusLine().status(), CODE_404_NOT_FOUND);
        } finally {
            Files.delete(path);
            // wait for the operating system to do its stuff
            MyThread.sleep(20);
        }

        // if we ask for a photo that has more than 0 bytes, get a 200
        try {
            Files.write(path, new byte[]{1, 2, 3});
            // wait for the operating system to finish receiving the file to disk
            MyThread.sleep(30);
            var photoResponse3 = ft.get("photo?name=bar.jpg");
            assertEquals(photoResponse3.statusLine().status(), CODE_200_OK);
        } finally {
            Files.delete(path);
            // wait for the operating system to do its stuff
            MyThread.sleep(20);
        }

        // if we ask for a photo that has more than 0 bytes, sized original, get a 200
        try {
            Files.write(pathOrginal, new byte[]{1, 2, 3});
            // wait for the operating system to finish receiving the file to disk
            MyThread.sleep(30);
            var photoResponse3 = ft.get("photo?name=bar.jpg&size=original");
            assertEquals(photoResponse3.statusLine().status(), CODE_200_OK);
        } finally {
            Files.delete(pathOrginal);
            // wait for the operating system to do its stuff
            MyThread.sleep(20);
        }
    }

    /**
     * Examine some of the behavior of the upload photo endpoint
     */
    @Test
    public void testUploadPhoto() throws IOException {
        // first, we cannot get this page or post to this page without being auth'd
        // cannot see this page unauth'd
        var postResponse = ft.post("upload", "");
        assertEquals(postResponse.statusLine().status(), CODE_403_FORBIDDEN);

        // login
        String cookieHeader = loginAndGetCookie();

        // fail to send the photograph
        var noPhotoSentResponse = ft.post("upload", "", List.of(cookieHeader));
        assertEquals(noPhotoSentResponse.statusLine().status(), CODE_400_BAD_REQUEST);

        // fail to send the short description
        var noShortDescSentResponse = ft.post("upload", "image_uploads=123", List.of(cookieHeader));
        assertEquals(noShortDescSentResponse.statusLine().status(), CODE_400_BAD_REQUEST);

        // short description sent, but missing a person id
        var missingPersonResponse = ft.post("upload", "image_uploads=123&short_description=cool", List.of(cookieHeader));
        assertEquals(missingPersonResponse.statusLine().status(), CODE_400_BAD_REQUEST);

        // all required fields - but not a multipart, and the id is an invalid UUID
        var invalidUuidResponse = ft.post(
                "upload",
                "image_uploads=123&short_description=cool&person_id=123",
                List.of(cookieHeader));
        assertEquals(invalidUuidResponse.statusLine().status(), CODE_400_BAD_REQUEST);

        // all required fields - but not a multipart, and the id is an invalid id
        var invalidIdResponse = ft.post(
                "upload",
                "image_uploads=123&short_description=cool&person_id=991a0ad8-463a-43c6-a253-88f4746cff76",
                List.of(cookieHeader));
        assertEquals(invalidIdResponse.statusLine().status(), CODE_400_BAD_REQUEST);

        // add a person to add a photograph
        String photoPersonUrl;
        logger.test("POST a new person, photoPerson, auth'd"); {
            String payload = "id=&image_input=&name_input=photoPerson+Katz&born_input=1921-11-21&died_input=&siblings_input=&spouses_input=&parents_input=&children_input=&biography_input=";
            var response = ft.post("editperson", payload, List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_303_SEE_OTHER);
            photoPersonUrl = response.headers().valueByKey("location").get(0);
        }


        // all required fields - but not a multipart, and the id is an invalid id
        var validResponse = ft.post(
                "upload",
                "image_uploads=123&short_description=cool&person_id=" + photoPersonUrl.replace("person?id=", ""),
                List.of(cookieHeader));
        assertEquals(validResponse.statusLine().status(), CODE_303_SEE_OTHER);

        // delete the new user
        ft.send(RequestLine.Method.DELETE, photoPersonUrl);
    }

    /**
     * Just to get a handle on the help page
     */
    @Test
    public void testGetHelp() throws IOException {
        var response = ft.get("general/help.html");
        assertTrue(response.body().asString().contains("This is a family tree and memorial site."));
    }

    /**
     * To get a handle on the behavior of registering a user
     */
    @Test
    public void testRegisterUser() throws IOException {
        // login
        String cookieHeader = loginAndGetCookie();

        var response = ft.get("register", List.of(cookieHeader));
        // make sure we see something expected as a registration page
        assertTrue(response.body().asString().contains("Username:"));
        // try out registering a user
        ft.post("registeruser", "username=foo&password=bar", List.of(cookieHeader));
        // give some time for the database to handle the registration
        var adminPageResponse = ft.get("admin", List.of(cookieHeader));
        assertTrue(adminPageResponse.body().asString().contains("name: foo"));

        // if we try registering that user again, we'll get a complaint
        var complaintResponse = ft.post("registeruser", "username=foo&password=bar", List.of(cookieHeader));
        assertEquals(complaintResponse.statusLine().status(), CODE_401_UNAUTHORIZED);
    }

    @Test
    public void testDeletingPerson() throws IOException {
        String cookieHeader = loginAndGetCookie();

        String johnUrl;
        logger.test("POST a new person, john, auth'd"); {
            String payload = "id=&image_input=&name_input=john+Katz&born_input=1921-11-21&died_input=2020-03-12&siblings_input=Florence&spouses_input=&parents_input=Robert+and+Ethel&children_input=ron&biography_input=%3Cp%3Ejohn+was+born+to+die%3C%2Fp%3E";
            var response = ft.post("editperson", payload, List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_303_SEE_OTHER);
            johnUrl = response.headers().valueByKey("location").get(0);
        }
        String johnId = RegexUtils.find("id=(?<johnid>.*)", johnUrl, "johnid");

        // try deleting, but with some edge cases:

        logger.test("delete, but forget to include an id"); {
            var response = ft.send(RequestLine.Method.DELETE, "person", new byte[0], List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_400_BAD_REQUEST);
        }

        logger.test("delete, but the id is invalid"); {
            var response = ft.send(RequestLine.Method.DELETE, "person?id=bad_id_here", new byte[0], List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_400_BAD_REQUEST);
        }

        logger.test("delete, happy path"); {
            var response = ft.send(RequestLine.Method.DELETE, "person?id=" + johnId, new byte[0], List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_204_NO_CONTENT);
        }

        String georgeUrl;
        logger.test("POST a new person, george, auth'd"); {
            String payload = "id=&image_input=&name_input=george+Katz&born_input=1921-11-21&died_input=2020-03-12&siblings_input=Florence&spouses_input=&parents_input=Robert+and+Ethel&children_input=ron&biography_input=%3Cp%3Ejohn+was+born+to+die%3C%2Fp%3E";
            var response = ft.post("editperson", payload, List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_303_SEE_OTHER);
            georgeUrl = response.headers().valueByKey("location").get(0);
        }
        String georgeId = RegexUtils.find("id=(?<georgeid>.*)", georgeUrl, "georgeid");

        logger.test("delete, sent by POST"); {
            var response = ft.post("persondelete", "id=" + georgeId, List.of(cookieHeader));
            assertEquals(response.headers().valueByKey("location").get(0), "/message?message=A+person+named+george+Katz+has+been+deleted&redirect=%2Feditpersons");
        }
    }

    @Test
    public void testModifyPerson() throws IOException {
        String cookieHeader = loginAndGetCookie();

        String henryUrl;
        logger.test("POST a new person, henry, auth'd"); {
            String payload = "id=&image_input=&name_input=henry+Katz" +
                    "&born_input=1921-11-21&died_input=2020-03-12" +
                    "&siblings_input=Florence&spouses_input=" +
                    "&parents_input=Robert+and+Ethel" +
                    "&children_input=ron" +
                    "&biography_input=%3Cp%3Ehenry+was+born+to+die%3C%2Fp%3E";
            var response = ft.post("editperson", payload, List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_303_SEE_OTHER);
            henryUrl = response.headers().valueByKey("location").get(0);
        }
        String henryId = RegexUtils.find("id=(?<henryid>.*)", henryUrl, "henryid");

        logger.test("modify, but the id is invalid"); {
            var response = ft.post("editperson", "id=bad_id_here&name_input=hello", List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_400_BAD_REQUEST);
        }

        logger.test("modify, happy path"); {
            String body = buildEditPersonBody(henryId);
            var response = ft.post("editperson?id=" + henryId, body, List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_303_SEE_OTHER);
        }
    }

    /**
     * This method is just a helper for constructing a valid body to edit a person
     */
    private static String buildEditPersonBody(String henryId) {
        final var imageInput            =   "";
        final var nameInput             =   "henry";
        final var bornInput             =   "";
        final var bornDateUnknownInput  =   "";
        final var diedInput             =   "";
        final var diedDateUnknownInput  =   "";
        final var siblingsInput         =   "";
        final var spousesInput          =   "";
        final var parentsInput          =   "";
        final var childrenInput         =   "";
        final var biographyInput        =   "";
        final var notesInput            =   "";
        final var genderInput           =   "";
        final var lastModifiedBy        =   "";
        String body = String.format("id=%s&image_input=%s&name_input=%s" +
                "&born_input=%s&born_date_unknown=%s&died_input=%s&death_date_unknown=%s&siblings_input=%s" +
                "&spouses_input=%s&parents_input=%s&children_input=%s" +
                "&biography_input=%s&notes_input=%s&gender_input=%s&last_modified_by=%s",
                henryId,
                imageInput,
                nameInput,
                bornInput,
                bornDateUnknownInput,
                diedInput,
                diedDateUnknownInput,
                siblingsInput,
                spousesInput,
                parentsInput,
                childrenInput,
                biographyInput,
                notesInput,
                genderInput,
                lastModifiedBy
                );
        return body;
    }

    /**
     * It is possible to add relatives through the API easily.  There are
     * four endpoints, corresponding to four relations - parents, siblings, spouses, children.
     * If we have a person, Fred, and use this API with a new name - "Alice McBob" , let's say a sibling, the new
     * sibling will be created with that name, with a link back to Fred, and Fred will have a link to Alice.
     */
    @Test
    public void testAddRelations() throws IOException {
        String cookieHeader = loginAndGetCookie();

        String georgeUrl;
        logger.test("POST a new person, george, auth'd"); {
            String payload = "id=&image_input=&name_input=george+Katz&born_input=1921-11-21&died_input=2020-03-12&siblings_input=Florence&spouses_input=&parents_input=Robert+and+Ethel&children_input=ron&biography_input=%3Cp%3Egeorge+was+born+to+have+siblings%3C%2Fp%3E";
            var response = ft.post("editperson", payload, List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_303_SEE_OTHER);
            georgeUrl = response.headers().valueByKey("location").get(0);
        }
        String georgeId = RegexUtils.find("id=(?<georgeid>.*)", georgeUrl, "georgeid");

        logger.test("POST a new parent for George"); {
            String payload = String.format("person_id=%s&relation_name_input=%s&relation=parent", georgeId, "mango");
            var addParentResponse = ft.post("addrelation", payload, List.of(cookieHeader));
            String newParentLocation = addParentResponse.headers().valueByKey("Location").get(0);
            String cleanedLocation = newParentLocation.replace("/editperson", "person");
            var newRelationResponse = ft.get(cleanedLocation);
            String mangoChild = newRelationResponse.searchOne(TagName.SPAN, Map.of("class", "children")).innerContent().get(0).innerText();
            assertEquals(mangoChild, "george Katz");

            var response = ft.get("person?id=" + georgeId);
            String georgeParent = response.searchOne(TagName.SPAN, Map.of("class", "parents")).innerContent().get(1).innerText();
            assertEquals(georgeParent, "mango");
        }

        logger.test("POST a new child for George"); {
            String payload = String.format("person_id=%s&relation_name_input=%s&relation=child", georgeId, "banana");
            var addChildResponse = ft.post("addrelation", payload, List.of(cookieHeader));
            String newChildLocation = addChildResponse.headers().valueByKey("Location").get(0);
            String cleanedLocation = newChildLocation.replace("/editperson", "person");
            var newRelationResponse = ft.get(cleanedLocation);
            String mangoChild = newRelationResponse.searchOne(TagName.SPAN, Map.of("class", "parents")).innerContent().get(0).innerText();
            assertEquals(mangoChild, "george Katz");

            var response = ft.get("person?id=" + georgeId);
            String georgeParent = response.searchOne(TagName.SPAN, Map.of("class", "children")).innerContent().get(1).innerText();
            assertEquals(georgeParent, "banana");
        }

        logger.test("POST a new sibling for George"); {
            String payload = String.format("person_id=%s&relation_name_input=%s&relation=sibling", georgeId, "carrot");
            var addSiblingResponse = ft.post("addrelation", payload, List.of(cookieHeader));
            String newSiblingLocation = addSiblingResponse.headers().valueByKey("Location").get(0);
            String cleanedLocation = newSiblingLocation.replace("/editperson", "person");
            var newRelationResponse = ft.get(cleanedLocation);
            String mangoChild = newRelationResponse.searchOne(TagName.SPAN, Map.of("class", "siblings")).innerContent().get(0).innerText();
            assertEquals(mangoChild, "george Katz");

            var response = ft.get("person?id=" + georgeId);
            String georgeParent = response.searchOne(TagName.SPAN, Map.of("class", "siblings")).innerContent().get(1).innerText();
            assertEquals(georgeParent, "carrot");
        }

        logger.test("POST a new spouse for George"); {
            String payload = String.format("person_id=%s&relation_name_input=%s&relation=spouse", georgeId, "artichoke");
            var addSpouseResponse = ft.post("addrelation", payload, List.of(cookieHeader));
            String newSpouseLocation = addSpouseResponse.headers().valueByKey("Location").get(0);
            String cleanedLocation = newSpouseLocation.replace("/editperson", "person");
            var newRelationResponse = ft.get(cleanedLocation);
            String mangoChild = newRelationResponse.searchOne(TagName.SPAN, Map.of("class", "spouses")).innerContent().get(0).innerText();
            assertEquals(mangoChild, "george Katz");

            var response = ft.get("person?id=" + georgeId);
            String georgeParent = response.searchOne(TagName.SPAN, Map.of("class", "spouses")).innerContent().get(0).innerText();
            assertEquals(georgeParent, "artichoke");
        }

        logger.test("POST a relation that does not exist (instead of spouse, use house)"); {
            String payload = String.format("person_id=%s&relation_name_input=%s&relation=house", georgeId, "artichoke");
            var addHouseResponse = ft.post("addrelation", payload, List.of(cookieHeader));
            assertEquals(addHouseResponse.statusLine().status(), CODE_400_BAD_REQUEST);
        }

        logger.test("POST a relation unauthenticated"); {
            String payload = String.format("person_id=%s&relation_name_input=%s&relation=spouse", georgeId, "artichoke");
            var response = ft.post("addrelation", payload, List.of());
            assertEquals(response.statusLine().status(), CODE_403_FORBIDDEN);
        }

        logger.test("POST a relation, forget to include the relation's name"); {
            String payload = String.format("person_id=%s&relation_name_input=%s&relation=spouse", georgeId, "");
            var response = ft.post("addrelation", payload, List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_400_BAD_REQUEST);
        }

        logger.test("POST a relation, forget to include the relation's name"); {
            String payload = String.format("person_id=%s&relation=spouse", georgeId);
            var response = ft.post("addrelation", payload, List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_400_BAD_REQUEST);
        }

        logger.test("POST a relation without a target person id"); {
            String payload = String.format("relation_name_input=%s&relation=spouse", "artichoke");
            var response = ft.post("addrelation", payload, List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_400_BAD_REQUEST);
        }

    }

    /**
     * There is a message endpoint that will let us show any simple
     * message to the user, with a redirect target of our choice.
     * It is currently just used for showing messages after the user
     * causes a POST request to be sent to the server.
     */
    @Test
    public void testMessage() throws IOException {

        logger.test("Happy path for the message page");
        {
            var messageResponse = ft.get("message?message=" + URLEncoder.encode("this is a test", UTF_8) +
                    "&redirect=" + URLEncoder.encode("/myredirect?id=foo", UTF_8));
            assertTrue(messageResponse.body().asString().contains("<p>this is a test</p>"));
            assertTrue(messageResponse.body().asString().contains("<p>Please click <a href=\"/myredirect?id=foo\">here</a></p>"));
        }

        logger.test("Sending neither of the query string parameters");
        {
            var messageResponse = ft.get("message");
            assertTrue(messageResponse.body().asString().contains("<p></p>"));
            assertTrue(messageResponse.body().asString().contains("<p>Please click <a href=\"\">here</a></p>"));
        }

        logger.test("Sending a script-injection-attack");
        {
            var messageResponse = ft.get("message?message=" + URLEncoder.encode("<script>console.log('foo')</script>", UTF_8) +
                    "&redirect=" + URLEncoder.encode("<script>console.log('bar')</script>", UTF_8));
            assertTrue(messageResponse.body().asString().contains("<p>&lt;script&gt;console.log('foo')&lt;/script&gt;</p>"));
            assertTrue(messageResponse.body().asString().contains("<p>Please click <a href=\"&lt;script>console.log(&apos;bar&apos;)&lt;/script>\">here</a></p>"));
        }

    }

    @Test
    public void testDeletePhotoEdgeCases() throws IOException {

        String cookieHeader = loginAndGetCookie();

        logger.test("unauthenticated - should redirect us to the index");
        {
            var response = ft.post("deletephoto", "");
            assertEquals(response.headers().valueByKey("location").get(0), "/");
        }
        
        logger.test("authenticated - but did not provide an id of photo to delete - should get 400 user error");
        {
            var response = ft.post("deletephoto", "", List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_400_BAD_REQUEST);
        }

        logger.test("authenticated - provided invalid id - should get 400 user error");
        {
            var response = ft.post("deletephoto", "photoid=abc123", List.of(cookieHeader));
            assertEquals(response.statusLine().status(), CODE_400_BAD_REQUEST);
        }
    }

    /**
     * Just hitting a few edge cases on this method
     */
    @Test
    public void testPhotoLongDescriptionUpdate() throws IOException {
        String cookie = loginAndGetCookie();
        logger.test("if unauthenticated, redirect to /");{
            var response = ft.post("photolongdescupdate", "");
            assertEquals(response.headers().valueByKey("location").get(0), "/");
        }

        logger.test("edge case - photo now found by id - should return 400");{
            var response = ft.post("photolongdescupdate", "long_description=foo&photoid=bar", List.of(cookie));
            assertEquals(response.statusLine().status(), CODE_400_BAD_REQUEST);
        }
    }

    /**
     * Just hitting a few edge cases on this method
     */
    @Test
    public void testPhotoShortDescriptionUpdate() throws IOException {
        String cookie = loginAndGetCookie();
        logger.test("if unauthenticated, redirect to /");{
            var response = ft.post("photocaptionupdate", "");
            assertEquals(response.headers().valueByKey("location").get(0), "/");
        }

        logger.test("edge case - photo now found by id - should return 400");{
            var response = ft.post("photocaptionupdate", "caption=foo&photoid=bar", List.of(cookie));
            assertEquals(response.statusLine().status(), CODE_400_BAD_REQUEST);
        }
    }

    private static String loginAndGetCookie() throws IOException {
        String cookieHeader;
        String password = Files.readString(Path.of(context.getConstants().dbDirectory).resolve(Path.of("admin_password")));
        logger.test("POST login with admin and proper password, get cookie and store for later");
        {
            var response = ft.post("loginuser", "username=admin&password=" + password);
            cookieHeader = "cookie: " + response.headers().valueByKey("set-cookie").get(0);
        }
        return cookieHeader;
    }

    @Test
    public void testLogin() throws IOException {
        String cookie = loginAndGetCookie();

        logger.test("if already authenticated, redirect to /");{
            var response = ft.post("loginuser", "", List.of(cookie));
            assertEquals(response.headers().valueByKey("location").get(0), "/");
        }

        logger.test("if login fails (unrecognized user), reply with a 403 forbidden");{
            var response = ft.post("loginuser", "username=foo&password=");
            assertEquals(response.statusLine().status(), CODE_403_FORBIDDEN);
            MyThread.sleep(150);
            String msg = logger.findFirstMessageThatContains("login attempted, but no user named", 20);
            assertTrue(msg.length() > 0);
        }

        logger.test("if login fails (bad credentials for existing user), reply with a 403"); {
            var response = ft.post("loginuser", "username=admin&password=");
            assertEquals(response.statusLine().status(), CODE_403_FORBIDDEN);
            MyThread.sleep(150);
            String msg = logger.findFirstMessageThatContains("Failed login for user named: ", 20);
            assertTrue(msg.length() > 0);
        }
    }

    @Test
    public void testResetPassword_EdgeCases() throws IOException {
        logger.test("Should not be able to reset a password unauthenticated"); {
            var response = ft.post("resetpassword", "");
            assertEquals(response.statusLine().status(), CODE_401_UNAUTHORIZED);
        }

        /*
         * If the new password is null, blank, or less than 12 characters, reject.
         */
        logger.test("missing password"); {
            String cookie = loginAndGetCookie();
            var response = ft.post("resetpassword", "", List.of(cookie));
            assertEquals(response.statusLine().status(), CODE_400_BAD_REQUEST);
        }
        logger.test("empty password"); {
            String cookie = loginAndGetCookie();
            var response = ft.post("resetpassword", "newpassword=", List.of(cookie));
            assertEquals(response.statusLine().status(), CODE_400_BAD_REQUEST);
        }
        logger.test("short password"); {
            String cookie = loginAndGetCookie();
            var response = ft.post("resetpassword", "newpassword=abc123", List.of(cookie));
            assertEquals(response.statusLine().status(), CODE_400_BAD_REQUEST);
        }
    }


}
