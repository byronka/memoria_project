package com.renomad.inmra.featurelogic.persons;

import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.auth.GettingOlderLoop;
import com.renomad.inmra.featurelogic.persons.services.FamilyGraphBuilder;
import com.renomad.inmra.featurelogic.photo.*;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.inmra.utils.NavigationHeader;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.state.Context;
import com.renomad.minum.web.IRequest;
import com.renomad.minum.web.IResponse;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * This code is for providing a way to add the details of a human person.
 */
public class PersonEndpoints {

    private final AbstractDb<Person> personDb;
    private final Path personDirectory;
    private final PersonListEndpoints personListEndpoints;
    private final PersonCreateEndpoints personCreateEndpoints;
    protected final AbstractDb<PhotoToPerson> photoToPersonDb;
    protected final IPersonLruCache personLruCache;
    protected final PhotoService photoService;
    protected final RenderPhotoRowsService renderPhotoRowsService;
    protected final FamilyGraphBuilder familyGraphBuilder;

    public PersonEndpoints(Context context,
                           MemoriaContext memoriaContext,
                           AbstractDb<Person> personDb,
                           IAuthUtils auth,
                           AbstractDb<PhotoToPerson> photoToPersonDb,
                           AbstractDb<Photograph> photographDb,
                           AbstractDb<VideoToPerson> videoToPersonDb,
                           AbstractDb<Video> videoDb,
                           AbstractDb<PersonMetrics> personMetricsDb,
                           PhotoService photoService,
                           NavigationHeader navigationHeader,
                           PersonLruCache personLruCache,
                           Map<UUID, PersonMetrics> personMetricsMap,
                           FamilyGraphBuilder familyGraphBuilder,
                           GettingOlderLoop gettingOlderLoop) {

        this.familyGraphBuilder = familyGraphBuilder;
        this.personLruCache = personLruCache;
        this.photoToPersonDb = photoToPersonDb;
        this.personDb = personDb;
        IFileUtils fileUtils = memoriaContext.getFileUtils();
        this.personDirectory = Path.of(context.getConstants().dbDirectory).resolve("person_files");
        this.photoService = photoService;
        this.personListEndpoints = new PersonListEndpoints(context, memoriaContext, auth, personDb, personLruCache, familyGraphBuilder, photoToPersonDb, videoToPersonDb, personMetricsDb, navigationHeader, personMetricsMap);
        this.personCreateEndpoints = new PersonCreateEndpoints(context, memoriaContext, auth, this, navigationHeader, familyGraphBuilder, gettingOlderLoop);
        this.renderPhotoRowsService = new RenderPhotoRowsService(photoToPersonDb, photographDb, videoToPersonDb, videoDb, personDb, fileUtils, personLruCache);
    }

    /**
     * Get the HTML page for showing a user the controls
     * for creating or editing a person
     */
    public IResponse createOrEditPersonGet(IRequest r) {
        return personCreateEndpoints.createOrEditPersonGet(r);
    }

    /**
     * The endpoint a user sends their data to when
     * creating or editing a person
     */
    public IResponse editPersonPost(IRequest r) {
        return personCreateEndpoints.editPersonPost(r);
    }

    /**
     * Get a page showing all the persons
     */
    public IResponse listAllPersonsGet(IRequest request) {
        return personListEndpoints.listAllPersonsGet(request);
    }

    public IResponse headerSearchGet(IRequest request) {
        return personListEndpoints.headerSearchGet(request);
    }

    /**
     * Get a page showing all the persons, so that
     * the user can choose one to edit
     */
    public IResponse editListGet(IRequest request) {
        return personListEndpoints.editListGet(request, false);
    }

    public IResponse innerListGet(IRequest request) {
        return personListEndpoints.editListGet(request, true);
    }

    public Path getPersonDirectory() {
        return personDirectory;
    }

    public AbstractDb<Person> getPersonDb() {
        return personDb;
    }

    public IResponse listPersonGet(IRequest request) {
        return personListEndpoints.viewPersonGet(request);
    }

    public IResponse listPersonGetAllRelatives(IRequest request) {
        return personListEndpoints.viewPersonGetAllRelatives(request);
    }

    public IResponse deletePersonPost(IRequest request) {
        return personCreateEndpoints.deletePerson(request, true);
    }

    public IResponse deletePersonGet(IRequest request) {
        return personCreateEndpoints.deletePersonGet(request);
    }

    public IResponse searchPersonGet(IRequest request) {
        return personListEndpoints.searchPersonGet(request);
    }

    /**
     * Search for persons to add as relations
     */
    public IResponse searchRelationGet(IRequest request) {
        return personListEndpoints.searchRelationGet(request);
    }

    public IResponse addRelationGet(IRequest request) {
        return personCreateEndpoints.addRelationGet(request);
    }

    public IResponse addRelationPost(IRequest request) {
        return personCreateEndpoints.addRelationPost(request);
    }

    public IPersonLruCache getPersonLruCache() {
        return personLruCache;
    }

    public IResponse descendantsPrintableGet(IRequest request) {
        return personListEndpoints.descendantsPrintableGet(request);
    }

    public IResponse ancestorsPrintableGet(IRequest request) {
        return personListEndpoints.ancestorsPrintableGet(request);
    }

    public IResponse removeRelationPost(IRequest request) {
        return personCreateEndpoints.removeRelation(request);
    }

    public IResponse listPersonPrintGet(IRequest request) {
        return personListEndpoints.viewPersonPrintGet(request);
    }
}
