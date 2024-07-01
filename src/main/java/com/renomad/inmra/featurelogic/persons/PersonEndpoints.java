package com.renomad.inmra.featurelogic.persons;

import com.renomad.inmra.auth.AuthHeader;
import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.featurelogic.persons.services.FamilyGraphBuilder;
import com.renomad.inmra.featurelogic.photo.PhotoService;
import com.renomad.inmra.featurelogic.photo.PhotoToPerson;
import com.renomad.inmra.featurelogic.photo.Photograph;
import com.renomad.inmra.featurelogic.photo.RenderPhotoRowsService;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.web.Request;
import com.renomad.minum.web.Response;

import java.io.IOException;
import java.nio.file.Path;

/**
 * This code is for providing a way to add the details of a human person.
 */
public class PersonEndpoints {

    private final Db<Person> personDb;
    private final Path personDirectory;
    private final PersonListEndpoints personListEndpoints;
    private final PersonCreateEndpoints personCreateEndpoints;




    protected final Db<PhotoToPerson> photoToPersonDb;
    protected final IPersonLruCache personLruCache;
    protected final AuthHeader authHeader;
    protected final FamilyGraphBuilder familyGraphBuilder;
    protected final PhotoService photoService;
    protected final RenderPhotoRowsService renderPhotoRowsService;

    public PersonEndpoints(Context context,
                           MemoriaContext memoriaContext,
                           Db<Person> personDb,
                           IAuthUtils auth,
                           Db<PhotoToPerson> photoToPersonDb,
                           Db<Photograph> photographDb,
                           PhotoService photoService,
                           AuthHeader authHeader
                           ) {
        Constants constants = context.getConstants();
        var dbDir = Path.of(constants.dbDirectory);

        personDirectory = dbDir.resolve("person_files");
        this.personLruCache = new PersonLruCache(getPersonDirectory());
        this.photoToPersonDb = photoToPersonDb;
        ILogger logger = context.getLogger();
        this.personDb = personDb;
        this.familyGraphBuilder = new FamilyGraphBuilder(context, personDb, personLruCache, logger);
        IFileUtils fileUtils = memoriaContext.fileUtils();

        this.authHeader = authHeader;
        this.photoService = photoService;

        try {
            fileUtils.makeDirectory(personDirectory);
        } catch (IOException ex) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
        }

        this.personListEndpoints = new PersonListEndpoints(context, memoriaContext, auth, this);
        this.personCreateEndpoints = new PersonCreateEndpoints(context, memoriaContext, auth, this);
        this.renderPhotoRowsService = new RenderPhotoRowsService(photoToPersonDb, photographDb, fileUtils);
    }

    /**
     * Get the HTML page for showing a user the controls
     * for creating or editing a person
     */
    public Response createNewPersonGet(Request r) {
        return personCreateEndpoints.createOrEditPersonGet(r);
    }

    /**
     * The endpoint a user sends their data to when
     * creating or editing a person
     */
    public Response editPersonPost(Request r) {
        return personCreateEndpoints.editPersonPost(r);
    }

    /**
     * Get a page showing all the persons
     */
    public Response listAllPersonsGet(Request request) {
        return personListEndpoints.listAllPersonsGet(request);
    }

    /**
     * Get a page showing all the persons, so that
     * the user can choose one to edit
     */
    public Response editListGet(Request request) {
        return personListEndpoints.editListGet(request);
    }

    public Path getPersonDirectory() {
        return personDirectory;
    }

    public Db<Person> getPersonDb() {
        return personDb;
    }

    public Response listPersonGet(Request request) {
        return personListEndpoints.viewPersonGet(request);
    }

    public Response deletePerson(Request request) {
        return personCreateEndpoints.deletePerson(request, false);
    }

    public Response deletePersonPost(Request request) {
        return personCreateEndpoints.deletePerson(request, true);
    }

    public Response searchPersonGet(Request request) {
        return personListEndpoints.searchPersonGet(request);
    }

    /**
     * Search for persons to add as relations
     */
    public Response searchRelationGet(Request request) {
        return personListEndpoints.searchRelationGet(request);
    }

    public Response addRelationPost(Request request) {
        return personCreateEndpoints.addRelationPost(request);
    }

    public IPersonLruCache getPersonLruCache() {
        return personLruCache;
    }
}
