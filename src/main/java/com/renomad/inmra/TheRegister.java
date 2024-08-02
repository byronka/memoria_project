package com.renomad.inmra;

import com.renomad.inmra.auth.*;
import com.renomad.inmra.featurelogic.letsencrypt.LetsEncrypt;
import com.renomad.inmra.featurelogic.misc.Message;
import com.renomad.inmra.featurelogic.photo.*;
import com.renomad.inmra.security.ISecurityUtils;
import com.renomad.inmra.security.SecurityUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.utils.LRUCache;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.utils.StringUtils;
import com.renomad.minum.web.WebFramework;
import com.renomad.inmra.administrative.Admin;
import com.renomad.inmra.featurelogic.persons.PersonEndpoints;
import com.renomad.inmra.featurelogic.persons.Person;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
    private final Db<User> userDb;
    private final AuthPages ap;
    private final PersonEndpoints personEndpoints;
    private final UploadPhoto up;
    private final ListPhotos lp;
    private final Admin admin;
    private final LetsEncrypt letsEncrypt;
    private final Message message;
    private final Constants constants;

    public void registerDomains() {

        // Register an initial user - "admin"
        registerTheAdminUser(userDb, ap);

        // general pages
        webFramework.registerPath(GET, "index", personEndpoints::listAllPersonsGet);
        webFramework.registerPath(GET, "", personEndpoints::listAllPersonsGet);
        webFramework.registerPath(GET, "message", message::messagePageGet);

        // photos stuff
        webFramework.registerPath(GET, "photos", lp::ListPhotosPageGet);
        webFramework.registerPath(POST, "upload", up::uploadPhotoReceivePost);
        webFramework.registerPath(POST, "copyphoto", up::copyPhotoReceivePost);
        webFramework.registerPath(GET, "photo", lp::grabPhotoGet);
        webFramework.registerPath(DELETE, "photo", up::photoDelete);
        webFramework.registerPath(POST, "deletephoto", up::photoDeletePost);
        webFramework.registerPath(PATCH, "photolongdescupdate", up::photoLongDescriptionUpdate);
        webFramework.registerPath(POST, "photolongdescupdate", up::photoLongDescriptionUpdatePost);
        webFramework.registerPath(PATCH, "photocaptionupdate", up::photoShortDescriptionUpdate);
        webFramework.registerPath(POST, "photocaptionupdate", up::photoShortDescriptionUpdatePost);
        webFramework.registerPath(GET, "copyphoto", up::copyPhotoGet);

        // auth stuff
        webFramework.registerPath(GET, "login", ap::loginGet);
        webFramework.registerPath(POST, "loginuser", ap::loginUserPost);
        webFramework.registerPath(POST, "logout", ap::logoutPost);
        webFramework.registerPath(GET, "loggedout", ap::loggedoutGet);
        webFramework.registerPath(GET, "register", ap::registerGet);
        webFramework.registerPath(POST, "registeruser", ap::registerUserPost);
        webFramework.registerPath(GET, "resetpassword", ap::resetUserPasswordGet);
        webFramework.registerPath(POST, "resetpassword", ap::resetUserPasswordPost);

        // Person stuff
        webFramework.registerPath(GET, "editperson", personEndpoints::createNewPersonGet);
        webFramework.registerPath(POST, "editperson", personEndpoints::editPersonPost);
        webFramework.registerPath(DELETE, "person", personEndpoints::deletePerson);
        webFramework.registerPath(POST, "persondelete", personEndpoints::deletePersonPost);
        webFramework.registerPath(GET, "editpersons", personEndpoints::editListGet);
        webFramework.registerPath(GET, "persons", personEndpoints::listAllPersonsGet);
        webFramework.registerPath(GET, "person", personEndpoints::listPersonGet);
        webFramework.registerPath(GET, "personsearch", personEndpoints::searchPersonGet);
        webFramework.registerPath(GET, "relationsearch", personEndpoints::searchRelationGet);

        // adding relations
        webFramework.registerPath(POST, "addrelation", personEndpoints::addRelationPost);

        // The Administration page - for controlling the system
        webFramework.registerPath(GET, "admin", admin::get);

        // An endpoint for Certbot / letsencrypt
        // see https://eff-certbot.readthedocs.io/en/stable/using.html#webroot
        webFramework.registerPartialPath(GET, ".well-known/acme-challenge", letsEncrypt::challengeResponse);
    }

    public TheRegister(Context context, MemoriaContext memoriaContext) {
        this.webFramework = context.getFullSystem().getWebFramework();
        this.constants = context.getConstants();
        ISecurityUtils securityUtils = new SecurityUtils(context.getExecutorService(), context.getLogger());

        // initialize our databases
        Db<SessionId> sessionDb = context.getDb("sessions", SessionId.EMPTY);
        userDb = context.getDb("users", User.EMPTY);
        Db<Photograph> photoDb = context.getDb("photos", Photograph.EMPTY);
        Db<Person> personDb = context.getDb("persons", Person.EMPTY);
        Db<PhotoToPerson> photoToPersonDb = context.getDb("photo_to_person", PhotoToPerson.EMPTY);
        Map<String, byte[]> photoLruCache = LRUCache.getLruCache();

        // instantiate the classes
        AuthUtils au = new AuthUtils(sessionDb, userDb, context, memoriaContext);
        AuthHeader authHeader = new AuthHeader(au, memoriaContext);
        ap = new AuthPages(au, authHeader, sessionDb, userDb, context, memoriaContext, securityUtils);
        new LoopingSessionReviewing(context, ap).initialize();
        PhotoService photoService = new PhotoService(context, memoriaContext, photoDb, photoLruCache, photoToPersonDb, personDb, au);
        personEndpoints = new PersonEndpoints(context, memoriaContext, personDb, au, photoToPersonDb, photoDb, photoService, authHeader);
        up = new UploadPhoto(context, memoriaContext, au, photoService);
        lp = new ListPhotos(context, memoriaContext, au, personEndpoints, photoLruCache, photoToPersonDb, photoDb);
        admin = new Admin(au, userDb, sessionDb, context, memoriaContext);
        letsEncrypt = new LetsEncrypt(context);
        message = new Message(memoriaContext);
    }

    /**
     * If there is no admin user registered for the system yet, this
     * will create it.
     */
    private void registerTheAdminUser(Db<User> userDb, AuthPages authPages) {

        if (userDb.values().stream().noneMatch(x -> x.getUsername().equals("admin"))) {
            String newPassword = StringUtils.generateSecureRandomString(20);
            try {
                MyThread.sleep(500);
                System.out.println("\n****************************************\n\n");
                System.out.println("Creating a new admin password, see \"admin_password\"");
                System.out.println("in the root of the database directory.");
                System.out.println("Use a user name of \"admin\" at localhost:8080\\login");
                System.out.println("\n\n************************************************\n\n");
                Files.writeString(Path.of(constants.dbDirectory).resolve(Path.of("admin_password")), newPassword);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            authPages.registerUserPost("admin", newPassword);
        }
    }

}
