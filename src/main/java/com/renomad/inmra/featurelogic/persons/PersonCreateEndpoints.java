package com.renomad.inmra.featurelogic.persons;

import com.renomad.inmra.auth.AuthResult;
import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.featurelogic.misc.Message;
import com.renomad.inmra.featurelogic.persons.services.PersonCreateServices;
import com.renomad.inmra.featurelogic.persons.services.PersonTrasher;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.inmra.utils.Respond;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.web.Request;
import com.renomad.minum.web.Response;
import com.renomad.minum.web.StatusLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static com.renomad.minum.utils.SearchUtils.findExactlyOne;
import static com.renomad.minum.web.StatusLine.StatusCode.CODE_500_INTERNAL_SERVER_ERROR;

public class PersonCreateEndpoints {
    private final IAuthUtils auth;
    private final PersonEndpoints personEndpoints;
    private final TemplateProcessor personEditTemplateProcessor;
    private final ILogger logger;
    private final PersonCreateServices personCreateServices;
    private final PersonTrasher personTrasher;

    public PersonCreateEndpoints(
            Context context,
            MemoriaContext memoriaContext,
            IAuthUtils auth,
            PersonEndpoints personEndpoints
            ) {
        this.auth = auth;
        this.personEndpoints = personEndpoints;
        var fileUtils = memoriaContext.fileUtils();
        personEditTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/person_edit.html"));
        this.logger = context.getLogger();
        personCreateServices = new PersonCreateServices(
                personEndpoints,
                logger,
                memoriaContext,
                context,
                auth,
                personEndpoints.personLruCache,
                personEndpoints.familyGraphBuilder
                );
        this.personTrasher = new PersonTrasher(
                personEndpoints.getPersonDirectory(),
                Path.of(context.getConstants().dbDirectory),
                logger,
                personEndpoints.getPersonDb(),
                personEndpoints.personLruCache,
                personEndpoints.photoService,
                personEndpoints.familyGraphBuilder,
                fileUtils);
    }

    public Response createOrEditPersonGet(Request r) {
        AuthResult authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return auth.htmlForbidden();
        }
        String id = r.requestLine().queryString().get("id");
        /*
        by default, we will show empty strings for the values for user attributes,
        since we're expecting the user to fill them in
         */
        Map<String, String> templateMap = new HashMap<>();

        if (id == null) {
            // if id is null, we're creating a new person
            String authHeader = personEndpoints.authHeader.getRenderedAuthHeader(r);
            personCreateServices.addEmptyValuesToTemplate(templateMap, authHeader);
            templateMap.put("photo_html", "");
        } else  {
            // if here, we're editing an existing person
            Person person = personEndpoints.getPersonDb().values().stream()
                    .filter(x -> x.getId().toString().equals(id))
                    .findFirst()
                    .orElse(Person.EMPTY);

            PersonFile personFile = personEndpoints.personLruCache.getCachedPersonFile(person);

            templateMap = new HashMap<>();
            templateMap.put("photo_html", personEndpoints.renderPhotoRowsService.renderPreviewPhotoRows(person));
            String authHeader = personEndpoints.authHeader.getRenderedAuthHeader(r, id);
            personCreateServices.addValuesForEditingExistingPerson(templateMap, id, personFile, authHeader);
        }

        return Respond.htmlOk(personEditTemplateProcessor.renderTemplate(templateMap));
    }


    /**
     * Add a spouse to a person.  Create a new person
     * and create appropriate linkages.
     */
    public Response addRelationPost(Request request) {
        AuthResult authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return auth.htmlForbidden();
        }
        String username = authResult.user().getUsername();
        String personId = request.body().asString("person_id");
        String relationTypeString = request.body().asString("relation");
        RelationType relationType;
        try {
            relationType = RelationType.valueOf(relationTypeString.toUpperCase());
        } catch (Exception ex) {
            logger.logDebug(() -> "Relation much be parent, child, spouse or sibling: user provided: " + relationTypeString);
            return Respond.userInputError();
        }
        String relationName = request.body().asString("relation_name_input");

        if (personId.isBlank()) {
            return Respond.userInputError();
        }
        if (relationName.isBlank()) {
            return Respond.userInputError();
        }

        // make sure this id corresponds to a person
        Person person = findExactlyOne(personEndpoints.getPersonDb().values().stream(), x -> x.getId().toString().equals(personId));

        // get the person file data from cache (read from disk if necessary)
        PersonFile personFile = personEndpoints.personLruCache.getCachedPersonFile(person);

        // create a new person who will be the relation.
        String relationLink = String.format(" <a href=\"person?id=%s\">%s</a>", personId, personFile.getName());

        Person newRelationPerson = personCreateServices.buildNewRelation(username, relationName, relationLink, personFile, relationType);

        return Response.redirectTo("/editperson?id=" + newRelationPerson.getId());
    }

    /**
     * Handle a POST request for setting the values on a person.
     */
    public Response editPersonPost(Request r) {
        AuthResult authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return auth.htmlForbidden();
        }
        Person person;
        try {
            person = personCreateServices.processPersonEditPost(r);
        } catch (BadUserInputException ex) {
            logger.logDebug(() -> "bad user input: " + ex);
            return Respond.userInputError();
        }

        return Response.buildLeanResponse(StatusLine.StatusCode.CODE_303_SEE_OTHER, Map.of("location","person?id="+person.getId()));
    }

    /**
     * Deletes a person and their
     * associated data
     * @param isPost if true, we'll handle this as a POST request.
     */
    public Response deletePerson(Request request, boolean isPost) {
        AuthResult authResult = auth.processAuth(request);
        if (!authResult.isAuthenticated()) {
            return auth.htmlForbidden();
        }

        var id = isPost ? request.body().asString("id") :
                request.requestLine().queryString().get("id");

        if (id == null) {
            logger.logDebug(() -> "User failed to include id of person to delete");
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }
        Person person = findExactlyOne(
                personEndpoints.getPersonDb().values().stream(),
                x -> x.getId().toString().equals(id));

        if (person == null) {
            logger.logDebug(() -> "User provided an id that matched no one: " + id);
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }

        String username = authResult.user().getUsername();
        logger.logAudit(() -> String.format("%s is deleting person: %s", username, person));

        try {
            personTrasher.moveToTrash(username, person);
        } catch (IOException e) {
            String exception = StacktraceUtils.stackTraceToString(e);
            logger.logAsyncError(() -> String.format("Error: Failed to put person %s (%d) in trash: %s", person.getName(), person.getIndex(), exception));
            return Response.buildLeanResponse(CODE_500_INTERNAL_SERVER_ERROR);
        }

        if (isPost) {
            String message = String.format("A person named %s has been deleted", person.getName());
            return Message.redirect(message, "/editpersons");
        } else {
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_204_NO_CONTENT);
        }
    }


}
