package com.renomad.inmra;

import com.renomad.inmra.auth.*;
import com.renomad.inmra.featurelogic.letsencrypt.LetsEncrypt;
import com.renomad.inmra.featurelogic.misc.Help;
import com.renomad.inmra.featurelogic.misc.Message;
import com.renomad.inmra.featurelogic.persons.PersonLruCache;
import com.renomad.inmra.featurelogic.persons.PersonMetrics;
import com.renomad.inmra.featurelogic.persons.services.FamilyGraphBuilder;
import com.renomad.inmra.featurelogic.photo.*;
import com.renomad.inmra.security.ISecurityUtils;
import com.renomad.inmra.security.SecurityUtils;
import com.renomad.inmra.utils.*;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.logging.Logger;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.LRUCache;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.utils.StringUtils;
import com.renomad.minum.web.*;
import com.renomad.inmra.administrative.Admin;
import com.renomad.inmra.featurelogic.persons.PersonEndpoints;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.featurelogic.version.Versioning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.renomad.minum.web.RequestLine.Method.*;

/**
 * This class is where all code gets registered to work
 * with our web testing.
 * <br><br>
 * example:
 * <pre>{@code
 *     wf.registerPath(RequestLine.Method.GET, "formentry", sd::formEntry);
 * }</pre>
 */
public class TheRegister {

    private final WebFramework webFramework;
    private final AbstractDb<User> userDb;
    private final AuthPages ap;
    private final PersonEndpoints personEndpoints;
    private final Versioning versioning;
    private final UploadPhoto up;
    private final ListPhotos lp;
    private final Admin admin;
    private final LetsEncrypt letsEncrypt;
    private final Message message;
    private final Help help;
    private final MemoriaLogger logger;
    private final MemoriaContext memoriaContext;
    private final Map<UUID, PersonMetrics> personMetricsMap;
    private final FamilyGraphBuilder familyGraphBuilder;
    private final GettingOlderLoop gettingOlderLoop;

    public void registerDomains() {

        // Register an initial user - "admin"
        registerTheAdminUser(userDb, ap);

        webFramework.registerPreHandler(this::preHandlerCode);

        // general pages
        webFramework.registerPath(GET, "index", personEndpoints::listAllPersonsGet);
        webFramework.registerPath(GET, "", personEndpoints::listAllPersonsGet);
        webFramework.registerPath(GET, "message", message::messagePageGet);
        webFramework.registerPath(GET, "general/adminhelp", help::adminHelpGet);

        // photos endpoints
        webFramework.registerPath(GET, "photos", lp::listPhotosPageGet);
        webFramework.registerPath(GET, "photorow", lp::listPhotosPagePhotoRowGet);
        webFramework.registerPath(GET, "videorow", lp::listPhotosPageVideoRowGet);
        webFramework.registerPath(POST, "upload", up::uploadFileReceivePost);
        webFramework.registerPath(GET, "copyphoto", up::copyPhotoGet);
        webFramework.registerPath(GET, "copyvideo", up::copyVideoGet);
        webFramework.registerPath(POST, "copyphoto", up::copyPhotoReceivePost);
        webFramework.registerPath(POST, "copyvideo", up::copyVideoReceivePost);
        webFramework.registerPath(GET, "photo", lp::grabPhotoGet);
        webFramework.registerPath(GET, "video", lp::grabVideoGet);
        webFramework.registerPath(DELETE, "photo", up::photoDelete);
        webFramework.registerPath(DELETE, "video", up::videoDelete);
        webFramework.registerPath(POST, "deletephoto", up::photoDeletePost);
        webFramework.registerPath(POST, "deletevideo", up::videoDeletePost);
        webFramework.registerPath(PATCH, "photodescriptionupdate", up::photoShortDescriptionUpdate);
        webFramework.registerPath(POST, "photodescriptionupdate", up::photoShortDescriptionUpdatePost);
        webFramework.registerPath(PATCH, "videodescriptionupdate", up::videoShortDescriptionUpdate);
        webFramework.registerPath(POST, "videodescriptionupdate", up::videoShortDescriptionUpdatePost);
        webFramework.registerPath(POST, "videoposterupdate", up::videoPosterUpdatePost);
        webFramework.registerPath(PATCH, "videoposterupdate", up::videoPosterUpdate);

        // auth endpoints
        webFramework.registerPath(GET, "login", ap::loginGet);
        webFramework.registerPath(POST, "loginuser", ap::loginUserPost);
        webFramework.registerPath(POST, "logout", ap::logoutPost);
        webFramework.registerPath(GET, "loggedout", ap::loggedoutGet);
        webFramework.registerPath(GET, "register", ap::registerGet);
        webFramework.registerPath(POST, "registeruser", ap::registerUserPost);
        webFramework.registerPath(GET, "resetpassword", ap::resetUserPasswordGet);
        webFramework.registerPath(POST, "resetpassword", ap::resetUserPasswordPost);
        webFramework.registerPath(GET, "privacylogin", ap::privacyLoginGet);
        webFramework.registerPath(POST, "privacylogin", ap::privacyLoginPost);
        webFramework.registerPath(GET, "privacylogout", ap::privacyLogoutGet);

        // Person endpoints
        webFramework.registerPath(GET, "editperson", personEndpoints::createOrEditPersonGet);
        webFramework.registerPath(POST, "editperson", personEndpoints::editPersonPost);
        webFramework.registerPath(GET, "persondelete", personEndpoints::deletePersonGet);
        webFramework.registerPath(POST, "persondelete", personEndpoints::deletePersonPost);
        webFramework.registerPath(GET, "editpersons", personEndpoints::editListGet);
        webFramework.registerPath(GET, "personlist", personEndpoints::innerListGet);
        webFramework.registerPath(GET, "persons", personEndpoints::listAllPersonsGet);
        webFramework.registerPath(GET, "person", personEndpoints::listPersonGet);
        webFramework.registerPath(GET, "personprint", personEndpoints::listPersonPrintGet);
        webFramework.registerPath(GET, "person-all", personEndpoints::listPersonGetAllRelatives);
        webFramework.registerPath(GET, "personsearch", personEndpoints::searchPersonGet);
        webFramework.registerPath(GET, "headersearch", personEndpoints::headerSearchGet);
        webFramework.registerPath(GET, "relationsearch", personEndpoints::searchRelationGet);
        webFramework.registerPath(GET, "descendants_printable", personEndpoints::descendantsPrintableGet);
        webFramework.registerPath(GET, "ancestors_printable", personEndpoints::ancestorsPrintableGet);

        webFramework.registerPath(GET, "versioning", versioning::versionGet);

        // adding relations
        webFramework.registerPath(GET, "addrelation", personEndpoints::addRelationGet);
        webFramework.registerPath(POST, "addrelation", personEndpoints::addRelationPost);

        // removing relations
        webFramework.registerPath(POST, "removerelation", personEndpoints::removeRelationPost);

        // The Administration page - for controlling the system
        webFramework.registerPath(GET, "admin", admin::get);

        // An endpoint for Certbot / letsencrypt
        // see https://eff-certbot.readthedocs.io/en/stable/using.html#webroot
        webFramework.registerPartialPath(GET, ".well-known/acme-challenge", letsEncrypt::challengeResponse);

    }

    private IResponse preHandlerCode(PreHandlerInputs preHandlerInputs) throws Exception {
        IRequest request = preHandlerInputs.clientRequest();
        ThrowingFunction<IRequest, IResponse> endpoint = preHandlerInputs.endpoint();

        // log all requests
        logger.logRequests(() -> {
            List<String> referer = request.getHeaders().valueByKey("referer");
            return String.format("Incoming request from %s: %s.  Referer: %s",
                    request.getRemoteRequester(),
                    request.getRequestLine().getRawValue(),
                    referer != null && ! referer.isEmpty() ? referer.getFirst() : "(None)");
        });

        // start timer
        long startMillis = System.currentTimeMillis();

        if (memoriaContext.getConstants().REGISTER_PREHANDLER) {
            ISocketWrapper sw = preHandlerInputs.sw();
            String path = request.getRequestLine().getPathDetails().getIsolatedPath();
            // redirect to https if they are on the plain-text connection and the path contains "login"
            if (path.contains("login") &&
                    sw.getServerType().equals(HttpServerType.PLAIN_TEXT_HTTP) &&
                    request.getRequestLine().getMethod().equals(GET)) {
                String secureEndpoint = "https://%s/%s".formatted(sw.getHostName(), path);
                logger.logRequests(() -> String.format("Redirecting %s to the secure endpoint, %s", request.getRemoteRequester(), secureEndpoint));
                return Respond.redirectTo(secureEndpoint);
            }
        }
        IResponse response = endpoint.apply(request);
        int bodyLength = response.getBody() == null ? 0 : response.getBody().length;

        String extraHeaders = response.getExtraHeaders().isEmpty() ? "(none)" : String.join(";", response.getExtraHeaders().getHeaderStrings());
        long endMillis = System.currentTimeMillis();
        Long processingTime = endMillis - startMillis;
        logger.logRequests(() -> String.format("Response to %s: %s, headers: %s, body length (in bytes): %d, processed in %d milliseconds",
                request.getRemoteRequester(),
                response.getStatusCode().shortDescription,
                extraHeaders,
                bodyLength,
                processingTime
        ));
        return response;
    }

    public TheRegister(Context context, MemoriaContext memoriaContext) {
        this.memoriaContext = memoriaContext;
        this.webFramework = context.getFullSystem().getWebFramework();
        this.logger = new MemoriaLogger((Logger)context.getLogger());

        ISecurityUtils securityUtils = new SecurityUtils(context.getExecutorService(), context.getLogger());
        securityUtils.initialize();
        memoriaContext.setSecurityUtils(securityUtils);

        // initialize our databases
        AbstractDb<SessionId> sessionDb = context.getDb2("sessions", SessionId.EMPTY).loadData();
        userDb = context.getDb2("users", User.EMPTY).loadData();
        AbstractDb<Photograph> photoDb = context.getDb2("photos", Photograph.EMPTY).loadData();
        AbstractDb<Video> videoDb = context.getDb2("videos", Video.EMPTY).loadData();
        AbstractDb<Person> personDb = context.getDb2("persons", Person.EMPTY).registerIndex("id", x -> x.getId().toString()).loadData();
        AbstractDb<PhotoToPerson> photoToPersonDb = context.getDb2("photo_to_person", PhotoToPerson.EMPTY).registerIndex("persons", x -> String.valueOf(x.getPersonIndex())).loadData();
        AbstractDb<VideoToPerson> videoToPersonDb = context.getDb2("video_to_person", VideoToPerson.EMPTY).registerIndex("persons", x -> String.valueOf(x.getPersonIndex())).loadData();
        AbstractDb<PersonMetrics> personMetricsDb = context.getDb2("person_metrics", PersonMetrics.EMPTY).registerIndex("id", x -> x.getPersonUuid().toString()).loadData();
        Map<String, byte[]> photoLruCache = LRUCache.getLruCache();

        // instantiate the Person LRU cache
        var dbDir = Path.of(context.getConstants().dbDirectory);
        var personDirectory = dbDir.resolve("person_files");
        try {
            var fileUtils = memoriaContext.getFileUtils();
            fileUtils.makeDirectory(personDirectory);
        } catch (IOException ex) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
        }
        var personLruCache = new PersonLruCache(personDirectory, context.getLogger());

        // instantiate the classes
        AuthUtils au = new AuthUtils(sessionDb, userDb, context, memoriaContext);
        AuthHeader authHeader = new AuthHeader(memoriaContext);

        var navigationHeader = new NavigationHeader(memoriaContext, authHeader);
        ap = new AuthPages(au, sessionDb, userDb, context, memoriaContext, securityUtils, navigationHeader);
        new LoopingSessionReviewing(context, ap).initialize();
        PhotoService photoService = new PhotoService(context, memoriaContext, photoDb, videoDb, photoLruCache, photoToPersonDb, videoToPersonDb, personDb, au);

        // the personMetricsMap is necessary to greatly increase the speed of obtaining metrics
        // for a person, which happens when paging through the list as admin.  The
        // LoopingPersonMetricsReview class will update its values every time it runs.
        personMetricsMap = new HashMap<>();
        familyGraphBuilder = new FamilyGraphBuilder(personDb, personLruCache, logger);
        gettingOlderLoop = new GettingOlderLoop(context, memoriaContext, personMetricsDb, familyGraphBuilder, personLruCache, personDb, photoToPersonDb, videoToPersonDb, personMetricsMap).initialize();
        personEndpoints = new PersonEndpoints(context, memoriaContext, personDb, au, photoToPersonDb, photoDb, videoToPersonDb, videoDb, personMetricsDb, photoService, navigationHeader, personLruCache, personMetricsMap, familyGraphBuilder, gettingOlderLoop);
        up = new UploadPhoto(context, memoriaContext, au, photoService, navigationHeader);
        lp = new ListPhotos(context, memoriaContext, au, personEndpoints, photoLruCache, photoToPersonDb, photoDb, videoToPersonDb, videoDb, personDb, navigationHeader, personLruCache);
        admin = new Admin(au, userDb, sessionDb, context, memoriaContext, personDb, photoDb, videoDb, navigationHeader, personMetricsDb);
        letsEncrypt = new LetsEncrypt(context);
        message = new Message(memoriaContext);
        help = new Help(memoriaContext, navigationHeader);
        versioning = new Versioning(au, context, memoriaContext.getFileUtils(), navigationHeader);
    }

    /**
     * If there is no admin user registered for the system yet, this
     * will create it.
     */
    private void registerTheAdminUser(AbstractDb<User> userDb, AuthPages authPages) {

        if (userDb.values().stream().noneMatch(x -> x.getUsername().equals("admin"))) {

            String newPassword = StringUtils.generateSecureRandomString(20);
            try {
                MyThread.sleep(500);
                System.out.println("\n****************************************\n\n");
                System.out.println("Creating a new admin password, see generated file \"admin_password\"\n\n");
                System.out.println("use a username of \"admin\" at http://localhost:8080/login with this password");
                System.out.println("\n\n************************************************\n\n");
                if (memoriaContext.getConstants().DO_NEW_PASSWORD_COUNTDOWN) {
                    showAlertInLogs();
                }
                Files.writeString(Path.of("admin_password"), newPassword);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            authPages.registerUserPost("admin", newPassword);
        }
    }

    private void showAlertInLogs() {
        MyThread.sleep(1000);
        System.out.print("Continuing in 10...");
        MyThread.sleep(1000);
        for (int i = 9; i > 0; i--) {
            System.out.print(i + "...");
            MyThread.sleep(1000);
        }
    }

}
