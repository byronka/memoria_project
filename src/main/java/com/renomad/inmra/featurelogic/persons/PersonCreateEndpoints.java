package com.renomad.inmra.featurelogic.persons;

import com.renomad.inmra.auth.AuthResult;
import com.renomad.inmra.auth.GettingOlderLoop;
import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.auth.User;
import com.renomad.inmra.featurelogic.persons.services.FamilyGraphBuilder;
import com.renomad.inmra.featurelogic.persons.services.PersonCreateServices;
import com.renomad.inmra.featurelogic.persons.services.PersonTrasher;
import com.renomad.inmra.featurelogic.persons.services.RelationInputs;
import com.renomad.inmra.utils.*;
import com.renomad.minum.htmlparsing.HtmlParseNode;
import com.renomad.minum.htmlparsing.HtmlParser;
import com.renomad.minum.htmlparsing.TagName;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.utils.StringUtils;
import com.renomad.minum.web.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.renomad.inmra.featurelogic.misc.Message.redirect;
import static com.renomad.inmra.utils.HtmlParserSearchUtils.search;
import static com.renomad.minum.utils.SearchUtils.findExactlyOne;
import static com.renomad.minum.web.StatusLine.StatusCode.CODE_500_INTERNAL_SERVER_ERROR;

public class PersonCreateEndpoints {
    private final IAuthUtils auth;
    private final PersonEndpoints personEndpoints;
    private final NavigationHeader navigationHeader;
    private final FamilyGraphBuilder familyGraphBuilder;
    private final TemplateProcessor personEditTemplateProcessor;
    private final TemplateProcessor addRelationActionTemplateProcessor;
    private final TemplateProcessor addRelationChooserTemplateProcessor;
    private final TemplateProcessor deletePersonTemplateProcessor;
    private final ILogger logger;
    private final PersonCreateServices personCreateServices;
    private final PersonTrasher personTrasher;
    private final Auditor auditor;

    public PersonCreateEndpoints(
            Context context,
            MemoriaContext memoriaContext,
            IAuthUtils auth,
            PersonEndpoints personEndpoints,
            NavigationHeader navigationHeader,
            FamilyGraphBuilder familyGraphBuilder,
            GettingOlderLoop gettingOlderLoop
            ) {
        this.auth = auth;
        this.personEndpoints = personEndpoints;
        this.navigationHeader = navigationHeader;
        this.familyGraphBuilder = familyGraphBuilder;
        var fileUtils = memoriaContext.getFileUtils();
        auditor = memoriaContext.getAuditor();
        personEditTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/person_edit.html"));
        addRelationChooserTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/add_relation_chooser.html"));
        addRelationActionTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/add_relation_action_template.html"));
        deletePersonTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/delete_person_template.html"));
        this.logger = context.getLogger();

        personCreateServices = new PersonCreateServices(
                personEndpoints,
                logger,
                memoriaContext,
                context,
                auth,
                personEndpoints.personLruCache,
                personEndpoints.familyGraphBuilder,
                gettingOlderLoop
                );
        this.personTrasher = new PersonTrasher(
                personEndpoints.getPersonDirectory(),
                Path.of(context.getConstants().dbDirectory),
                logger,
                personEndpoints.getPersonDb(),
                personEndpoints.personLruCache,
                personEndpoints.photoService,
                personEndpoints.familyGraphBuilder,
                fileUtils,
                auditor);
    }

    public IResponse createOrEditPersonGet(IRequest r) {
        AuthResult authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return auth.htmlForbidden();
        }
        String id = r.getRequestLine().queryString().get("id");
        /*
        by default, we will show empty strings for the values for user attributes,
        since we're expecting the user to fill them in
         */
        Map<String, String> templateMap = new HashMap<>();



        if (id == null) {

            // create disabled buttons for when only the save button is available.
            String viewAndDeleteButtons = """
                    <button title="These actions are disabled until after the person is created" id="view_read_only_page_link" disabled>View</button>
                    <button title="These actions are disabled until after the person is created" id="versioning_link" disabled>Versioning</button>
                    <button title="These actions are disabled until after the person is created" id="delete_person_link" disabled>Delete</button>
                    """;

            // if id is null, we're creating a new person
            String myNavHeader = navigationHeader.renderNavigationHeader(r, true, true, "");
            personCreateServices.addEmptyValuesToTemplate(templateMap, myNavHeader);
            templateMap.put("photo_html", "");
            templateMap.put("add_parent_action", "<em>Unavailable until after person is created</em>");
            templateMap.put("parent_list", "");
            templateMap.put("add_child_action", "<em>Unavailable until after person is created</em>");
            templateMap.put("child_list", "");
            templateMap.put("add_spouse_action", "<em>Unavailable until after person is created</em>");
            templateMap.put("spouse_list", "");
            templateMap.put("add_sibling_action", "<em>Unavailable until after person is created</em>");
            templateMap.put("sibling_list", "");
            templateMap.put("view_and_delete_buttons", viewAndDeleteButtons);
        } else  {
            UUID idValue = new UUID(0,0);
            try {
                idValue = UUID.fromString(id);
            } catch (IllegalArgumentException ex) {
                logger.logDebug(() -> "Failed to convert id to UUID at createOrEditPersonGet. id value: " + id + ". Error: " + ex.getMessage());
            }

            // if here, we're editing an existing person
            UUID finalIdValue = idValue;
            Person person = personEndpoints.getPersonDb().values().stream()
                    .filter(x -> x.getId().equals(finalIdValue))
                    .findFirst()
                    .orElse(Person.EMPTY);

            if (person.equals(Person.EMPTY)) {
                return Response.buildLeanResponse(StatusLine.StatusCode.CODE_404_NOT_FOUND);
            }

            PersonFile personFile = personEndpoints.personLruCache.getCachedPersonFile(person);

            templateMap = new HashMap<>();
            templateMap.put("photo_html", personEndpoints.renderPhotoRowsService.renderPreviewPhotoRows(person));


            String addParentAction = addRelationActionTemplateProcessor.renderTemplate(
                    Map.of(
                            "id", id,
                            "relation_type_value", "parent"
                    )
            );
            String addSpouseAction = addRelationActionTemplateProcessor.renderTemplate(
                    Map.of(
                            "id", id,
                            "relation_type_value", "spouse"
                    )
            );
            String addSiblingAction = addRelationActionTemplateProcessor.renderTemplate(
                    Map.of(
                            "id", id,
                            "relation_type_value", "sibling"
                    )
            );
            String addChildAction = addRelationActionTemplateProcessor.renderTemplate(
                    Map.of(
                            "id", id,
                            "relation_type_value", "child"
                    )
            );

            String viewAndDeleteButtons = String.format("""
                    <a id="view_read_only_page_link" href="/person?id=%s">View</a>
                    <a id="versioning_link" href="/versioning?id=%s">Versioning</a>
                    <a id="delete_person_link" href="/persondelete?id=%s">Delete</a>
                    """,
                    person.getId().toString(),
                    person.getId().toString(),
                    person.getId().toString()
                    );

            PersonNode personNode = familyGraphBuilder.getPersonNodes().get(person.getId());
            templateMap.put("add_parent_action", addParentAction);
            templateMap.put("parent_list", getRelationNames(personNode, "parent"));
            templateMap.put("add_child_action", addChildAction);
            templateMap.put("child_list", getRelationNames(personNode, "child"));
            templateMap.put("add_spouse_action", addSpouseAction);
            templateMap.put("spouse_list", getRelationNames(personNode, "spouse"));
            templateMap.put("add_sibling_action", addSiblingAction);
            templateMap.put("sibling_list", getRelationNames(personNode, "sibling"));
            templateMap.put("view_and_delete_buttons", viewAndDeleteButtons);

            String myNavHeader = navigationHeader.renderNavigationHeader(r, true, true, finalIdValue.toString());
            personCreateServices.addValuesForEditingExistingPerson(templateMap, finalIdValue.toString(), personFile, myNavHeader);
        }

        return Respond.htmlOk(personEditTemplateProcessor.renderTemplate(templateMap));
    }

    private String getRelationNames(PersonNode personNode, String relation) {
        PersonFile personFile = this.personEndpoints.getPersonLruCache().getCachedPersonFile(personNode.getId().toString());
        String relationData = switch(relation) {
            case "parent" -> personFile.getParents();
            case "child" -> personFile.getChildren();
            case "sibling" -> personFile.getSiblings();
            case "spouse" -> personFile.getSpouses();
            default -> throw new BadUserInputException("The allowed relations are parent, child, sibling, spouse. Provided: " + relation);
        };

        // if the data is blank, or if we don't find any person links in it, return with nothing.
        if (relationData.isBlank()) {
            return "";
        }

        // at this point we know there are at least one person links in the data, so we will
        // parse the data and obtain the person links.
        List<HtmlParseNode> anchorTags = search(relationData, TagName.A);


        return anchorTags.stream()
                // get the anchor tags that resemble links to persons
                .filter(x -> x.getTagInfo().getAttribute("href").contains("person?id"))
                // convert their data to a form for display
                .map(x -> {
                    // this is the data we need that points to a person
                    String relationUuid = x.getTagInfo().getAttribute("href").replaceAll("person\\?id=", "");
                    String relationName = x.innerText();
                    PersonFile relationPersonFile = this.personEndpoints.getPersonLruCache().getCachedPersonFile(relationUuid);

                    // the relation personfile could be empty if deleted, so we'll indicate that.
                    String linkId = String.format("/editperson?id=%s", relationUuid);
                    if (relationPersonFile.equals(PersonFile.EMPTY)) {
                        relationName = relationName + " (MISSING)";
                        linkId = "#";
                    }

                    return String.format(
                            """
                                    <div class="relation-pill">
                                    <a href="%s">%s</a>
                                    <form class="remove-relation-form" method="post" action="removerelation">
                                      <input type="hidden" name="relation" value="%s">
                                      <input type="hidden" name="person_id" value="%s">
                                      <input type="hidden" name="person_to_remove_id" value="%s">
                                      <button class="remove-relation-button" type="submit" alt="remove this person" title="remove this person">X</button>
                                    </form>
                                    </div>
                                    """,
                            linkId,
                            StringUtils.safeHtml(relationName),
                            relation,
                            personNode.getId().toString(),
                            relationUuid
                    );
                })
                .collect(Collectors.joining(", "));
    }

    public IResponse addRelationPost(IRequest request) {
        AuthResult authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return auth.htmlForbidden();
        }

        // if this key has been set, the user wants to connect an *existing* person,
        // so we'll skip creation of a person and jump straight to adding
        // to their relations.
        String otherPersonId = request.getBody().asString("other_person_id");
        RelationType relationType;
        String relationTypeString = request.getBody().asString("relation_type");
        try {
            relationType = RelationType.valueOf(relationTypeString.toUpperCase());
        } catch (Exception ex) {
            logger.logDebug(() -> "Relation much be parent, child, spouse or sibling: user provided: " + relationTypeString);
            return Respond.userInputError();
        }

        String originalPersonId = request.getBody().asString("person_id");
        if (originalPersonId.isBlank()) {
            return Respond.userInputError();
        }
        String relationName = request.getBody().asString("relation_name_input");
        if (relationName.isBlank()) {
            return Respond.userInputError();
        }

        String connectionType = request.getBody().asString("connection_type");
        if (connectionType.isBlank() || !(connectionType.equals("simple") || connectionType.equals("complete"))) {
            return Respond.userInputError();
        }

        try {
            // get the person file data from cache (read from disk if necessary)
            PersonFile personFile = personEndpoints.personLruCache.getCachedPersonFile(originalPersonId);

            if (connectionType.equals("simple")) {
                if (otherPersonId != null && !otherPersonId.isBlank()) {
                    // if there is an existing person, check for creating an ancestry cycle
                    FamilyGraph.checkForCycle(personFile.getName(), personFile.getId(), "<a href=\"person?id=%s\">%s</a>".formatted(otherPersonId, relationName), relationType.toString().toLowerCase(), familyGraphBuilder.getPersonNodes());
                }
                createRelationToPerson(personFile, authResult.user(), otherPersonId, relationName, relationType);
            } else {
                var originalPersonNode = personEndpoints.familyGraphBuilder.getPersonNodes().get(UUID.fromString(originalPersonId));
                if (originalPersonNode == null) {
                    throw new BadUserInputException("Unable to find a person for uuid of originalPersonNode in addRelationPost: " + otherPersonId);
                }
                List<RelationshipConnectionInfo> results =
                        calculateNecessaryConnections(relationType, originalPersonNode, otherPersonId, relationName, authResult.user());
                if (otherPersonId != null && !otherPersonId.isBlank()) {
                    // if there is an existing person, check for creating an ancestry cycle
                    for (RelationshipConnectionInfo result : results) {
                        if (result.otherPersonId() != null && ! result.otherPersonId().isBlank()) {
                            FamilyGraph.checkForCycle(result.relationName, UUID.fromString(result.otherPersonId), "<a href=\"person?id=%s\">%s</a>".formatted(result.personFile().getId(), result.personFile().getName()), result.relationType().toString().toLowerCase(), familyGraphBuilder.getPersonNodes());
                            RelationType oppositeType;
                            if (result.relationType().equals(RelationType.CHILD)) {
                                oppositeType = RelationType.PARENT;
                            } else if (result.relationType().equals(RelationType.PARENT)) {
                                oppositeType = RelationType.CHILD;
                            } else {
                                oppositeType = result.relationType();
                            }
//                            FamilyGraph.checkForCycle(result.relationName, UUID.fromString(result.otherPersonId), "<a href=\"person?id=%s\">%s</a>".formatted(result.personFile().getId(), result.personFile().getName()), result.relationType().toString().toLowerCase(), familyGraphBuilder.getPersonNodes());
                            FamilyGraph.checkForCycle(result.personFile().getName(), result.personFile().getId(), "<a href=\"person?id=%s\">%s</a>".formatted(UUID.fromString(result.otherPersonId), result.relationName), oppositeType.toString().toLowerCase(), familyGraphBuilder.getPersonNodes());
                        }
                    }
                }
                // this value may be null at first when we need to create the person in the first
                // loop, but will have a value after creating them.
                String idExistingPerson = otherPersonId;
                for (RelationshipConnectionInfo result : results) {
                    Person p = createRelationToPerson(result.personFile(), authResult.user(), idExistingPerson, relationName, result.relationType());
                    if (p != null) {
                        idExistingPerson = p.getId().toString();
                    }

                }
            }
            return Respond.redirectTo("/editperson?id=" + originalPersonId);
        } catch (CircularLoopException ex) {
            logger.logDebug(() -> "Cycle in family tree would have been added. " + ex);
            String id = request.getBody().asString("person_id");
            UUID uuid;
            try {
                uuid = UUID.fromString(id);
                return redirect(ex.getMessage(), "/editperson?id=" + uuid);
            } catch (IllegalArgumentException e) {
                throw new BadUserInputException("The provided id was not a valid UUID: " + id);
            }
        } catch (Exception ex) {
            logger.logDebug(() -> "Exception while generating description in addRelationGet: " + ex);
            return Respond.userInputError();
        }

    }

    /**
     * Create a connection between two people, creating the new person if necessary,
     * linking two existing people otherwise, avoiding adding a link if it already exists.
     * Returns the user to the detail page of the originating person.
     * <br>
     * This will return a person if we've created a new person, or null otherwise
     * @param personFile the originating person to whom we are adding a connection
     * @param user the user causing this action to take place
     * @param otherPersonId the id of another person in the system - if this is not null, we
     *                      are connecting our originating person to an existing person, rather
     *                      than creating a new person for the relationship.
     * @param relationName If we are creating a new person for this connection, this is their name
     * @param relationType the type of relation, either sibling, spouse, parent, child.
     */
    private Person createRelationToPerson(PersonFile personFile, User user, String otherPersonId, String relationName, RelationType relationType) {
        String relationLink = String.format("<a href=\"person?id=%s\">%s</a>", personFile.getId(), personFile.getName());
        Person newRelationPerson;
        if (otherPersonId == null || otherPersonId.isBlank()) {
            // if we need to create a new person
            newRelationPerson = personCreateServices.buildNewRelation(user, relationName, relationLink, personFile, relationType);

            auditor.audit(() -> String.format("user %s has created a new relation %s (id: %d) for person %s (id: %s)",
                    user.getUsername(), newRelationPerson.getName(), newRelationPerson.getIndex(), personFile.getName(), personFile.getId()), user);
            return newRelationPerson;
        } else {
            // otherwise we are updating an existing person with a connection to this person.
            newRelationPerson = findExactlyOne(personEndpoints.getPersonDb().values().stream(), x -> x.getId().toString().equals(otherPersonId));
            PersonFile newRelationPersonFile = personEndpoints.personLruCache.getCachedPersonFile(newRelationPerson);
            // update the existing person with information about the new relation
            personCreateServices.connectRelationshipToExistingPerson(user, personFile, newRelationPersonFile, relationType);

            auditor.audit(() -> String.format("user %s has connected a relationship between %s (id: %d) to person %s (id: %s)",
                    user.getUsername(), newRelationPerson.getName(), newRelationPerson.getIndex(), personFile.getName(), personFile.getId()), user);
            return null;
        }
    }

    /**
     * Add a relation to a person.  Create a new person
     * and create appropriate linkages.
     */
    public IResponse addRelationGet(IRequest request) {
        AuthResult authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return auth.htmlForbidden();
        }
        String originalPersonId = request.getRequestLine().queryString().get("person_id");
        String relationTypeString = request.getRequestLine().queryString().get("relation");
        // if this key has been set, the user wants to connect an *existing* person,
        // so we'll skip creation of a person and jump straight to adding
        // to their relations.
        String otherPersonId = request.getRequestLine().queryString().get("other_person_id");
        RelationType relationType;
        try {
            relationType = RelationType.valueOf(relationTypeString.toUpperCase());
        } catch (Exception ex) {
            logger.logDebug(() -> "Relation much be parent, child, spouse or sibling: user provided: " + relationTypeString);
            return Respond.userInputError();
        }
        String relationName = request.getRequestLine().queryString().get("relation_name_input");

        if (originalPersonId.isBlank()) {
            return Respond.userInputError();
        }
        if (relationName.isBlank()) {
            return Respond.userInputError();
        }
        String myNavHeader = navigationHeader.renderNavigationHeader(request, true, true, originalPersonId);
        try {
            // calculate a description of what we'll do in the simple case
            String simpleDescription = calculateDescription(
                    originalPersonId, relationTypeString, otherPersonId, relationName, authResult.user(), false);

            // calculate a description for the thorough case
            String thoroughDescription = calculateDescription(
                    originalPersonId, relationTypeString, otherPersonId, relationName, authResult.user(), true);

            Map<String, String> myMap = new HashMap<>();

            myMap.put("navigation_header", myNavHeader);
            myMap.put("id", StringUtils.safeAttr(originalPersonId));
            myMap.put("other_person_id", StringUtils.safeAttr(otherPersonId));
            myMap.put("relation_name_input", StringUtils.safeAttr(relationName));
            myMap.put("relation_type", relationType.toString().toLowerCase());
            myMap.put("simple_description", simpleDescription);
            myMap.put("thorough_description", thoroughDescription);
            return Response.htmlOk(addRelationChooserTemplateProcessor.renderTemplate(myMap));
        } catch (Exception ex) {
            logger.logDebug(() -> "Exception while generating description in addRelationGet: " + ex);
            return Respond.userInputError();
        }

    }

    public IResponse removeRelation(IRequest request) {
        AuthResult authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return auth.htmlForbidden();
        }
        User user = authResult.user();
        logger.logDebug(() -> user.getUsername() + " is requesting to remove a relation");

        // get the relation type
        String relationTypeString = request.getBody().asString("relation");
        RelationType relationType;
        try {
            relationType = RelationType.valueOf(relationTypeString.toUpperCase());
        } catch (Exception ex) {
            logger.logDebug(() -> "Relation much be parent, child, spouse or sibling: user provided: " + relationTypeString);
            return Respond.userInputError();
        }

        // get the identifiers of the original person and the relation we're removing
        String originalPersonId = request.getBody().asString("person_id");
        String personToRemoveId = request.getBody().asString("person_to_remove_id");

        if (originalPersonId.isBlank()) {
            logger.logDebug(() -> user.getUsername() + " did not send a value for person_id when removing a relation");
            return Respond.userInputError();
        }
        if (personToRemoveId.isBlank()) {
            logger.logDebug(() -> user.getUsername() + " did not send a value for person_to_remove_id when removing a relation");
            return Respond.userInputError();
        }

        Person originalPerson = findExactlyOne(personEndpoints.getPersonDb().values().stream(), x -> x.getId().toString().equals(originalPersonId));
        if (originalPerson == null) {
            logger.logDebug(() -> user.getUsername() + " did not send a valid value for person_id when removing a relation");
            return Respond.userInputError();
        }
        Person personToRemove = findExactlyOne(personEndpoints.getPersonDb().values().stream(), x -> x.getId().toString().equals(personToRemoveId));
        if (personToRemove == null) {
            logger.logDebug(() -> user.getUsername() + " is removing a deleted relation (id: "+ personToRemoveId +") from " + originalPerson.getName() + " (id: " + originalPersonId + ")");
        }
        PersonFile originalPersonFile = personEndpoints.personLruCache.getCachedPersonFile(originalPerson);

        // now we have an original person, and a person we're going to remove from them.

        switch(relationType) {
            case PARENT -> updatePerson(user,
                    originalPersonFile,
                    removeRelationFromLinks(originalPersonFile.getParents(), personToRemoveId),
                    originalPersonFile.getChildren(),
                    originalPersonFile.getSpouses(),
                    originalPersonFile.getSiblings());
            case CHILD -> updatePerson(user,
                    originalPersonFile,
                    originalPersonFile.getParents(),
                    removeRelationFromLinks(originalPersonFile.getChildren(), personToRemoveId),
                    originalPersonFile.getSpouses(),
                    originalPersonFile.getSiblings());
            case SPOUSE -> updatePerson(user,
                    originalPersonFile,
                    originalPersonFile.getParents(),
                    originalPersonFile.getChildren(),
                    removeRelationFromLinks(originalPersonFile.getSpouses(), personToRemoveId),
                    originalPersonFile.getSiblings());
            case SIBLING -> updatePerson(user,
                    originalPersonFile,
                    originalPersonFile.getParents(),
                    originalPersonFile.getChildren(),
                    originalPersonFile.getSpouses(),
                    removeRelationFromLinks(originalPersonFile.getSiblings(), personToRemoveId));
        }

        return Respond.redirectTo("/editperson?id=" + originalPersonId);

    }

    private void updatePerson(User user, PersonFile personFile, String parents, String children, String spouses, String siblings) {
        RelationInputs relationInputs = new RelationInputs(parents, siblings, spouses, children);
        personCreateServices.createOrUpdatePersonData(
                personFile.getId().toString(),
                user,
                personFile.getName(),
                personFile.getImageUrl(),
                relationInputs,
                personFile.getBiography(),
                personFile.getAuthBio(),
                personFile.getNotes(),
                personFile.getGender(),
                personFile.getBorn(),
                personFile.getDied(),
                personFile.getExtraFields()
        );
    }

    /**
     * Remove a relation from a person's list of links to relations
     * @param relationInformation the HTML text in a person's file that includes links to
     *                            a person's relations - parents, siblings, spouses, children
     * @return the relationInformation with the designated person removed
     */
    private String removeRelationFromLinks(String relationInformation, String personToRemoveId) {
        var htmlParser = new HtmlParser();
        Stream<HtmlParseNode> relationLinks = htmlParser.parse(relationInformation).stream()
                .filter(x -> x.getTagInfo().getTagName().equals(TagName.A));
        return relationLinks
                .filter(x -> !x.getTagInfo().getAttribute("href").contains(personToRemoveId))
                .map(HtmlParseNode::toString)
                .collect(Collectors.joining());
    }

    /**
     * This is the data necessary to pass into the method for constructing
     * relationships between persons
     * @param originatingPerson the first person to whom we are adding a connection
     * @param relationToFirstPerson the relation of the person specified in personFile to the
     *                              originating person.  Null if they are the same person.
     * @param personFile the secondary person to whom we are adding a connection, that is,
     *                   a relative of the first person, like when we add a spouse to someone,
     *                   the children get a new parent - they are the second-order effect
     * @param user the user causing this action to take place
     * @param otherPersonId the id of another person in the system - if this is not null, we
     *                      are connecting our originating person to an existing person, rather
     *                      than creating a new person for the relationship.
     * @param relationName If we are creating a new person for this connection, this is their name
     * @param relationType the type of relation, either sibling, spouse, parent, child, the new person
     *                     is to the originating person
     */
    record RelationshipConnectionInfo(PersonFile originatingPerson,
                                      RelationType relationToFirstPerson,
                                      PersonFile personFile, User user, String otherPersonId,
                                      String relationName, RelationType relationType){}

    /**
     * This will generate a string description of a list of actions that will take place to
     * add a new relation to a person.
     * <br>
     * For example, if "carol" is the originating person, and we are adding a new person, "alice"
     * to be her mom, then the output will be something like this:
     * <pre>
     *         * carol will get a new parent: alice, who will get a child of carol
     *         * carol's parent bob will get a new spouse: alice, who will get a spouse of bob
     *         * carol's sibling david will get a new parent: alice, who will get a child of david
     *         * carol's sibling eleanor will get a new parent: alice, who will get a child of eleanor
     *         * carol's sibling gerald will get a new parent: alice, who will get a child of gerald
     *         * carol's sibling kevin will get a new parent: alice, who will get a child of kevin
     *         * carol's sibling henry will get a new parent: alice, who will get a child of henry
     * </pre>
     * @param originalPersonId the originating person, to whom we are adding a relation
     * @param relationTypeString the type of relation, e.g. "sibling", "spouse", etc.
     * @param otherPersonId the string form of a uuid identifier for a person to connect
     * @param relationName the name of a person to create, if there is no otherPersonId id, meaning
     *                     we need to create a new person with that name.
     * @param findAllConnections whether to calculate all the connections that would make sense, or rather
     *                       to just calculate the first-order effect (that is, just, for example, adding
     *                       a parent to a child and nothing else)
     */
    private String calculateDescription(String originalPersonId, String relationTypeString, String otherPersonId,
                                        String relationName, User user, boolean findAllConnections) {
        var originalPersonNode = personEndpoints.familyGraphBuilder.getPersonNodes().get(UUID.fromString(originalPersonId));
        if (originalPersonNode == null) {
            throw new BadUserInputException("Unable to find a person for uuid of originalPersonNode in calculateThoroughDescription: " + otherPersonId);
        }

        String newPersonName;
        if (otherPersonId == null || otherPersonId.isBlank()) {
            // if newPersonNode is null, we are creating a new person, named by relationName
            newPersonName = relationName;
        } else {
            var newPersonNode = personEndpoints.familyGraphBuilder.getPersonNodes().get(UUID.fromString(otherPersonId));
            if (newPersonNode == null) {
                throw new BadUserInputException("Unable to find a person for uuid of newPersonNode in calculateThoroughDescription: " + otherPersonId);
            }
            newPersonName = newPersonNode.getName();
        }

        RelationType relationType = RelationType.valueOf(relationTypeString.toUpperCase());

        List<RelationshipConnectionInfo> results = calculateNecessaryConnections(relationType, originalPersonNode, otherPersonId, relationName, user);

        StringBuilder sb = new StringBuilder();
        for (RelationshipConnectionInfo result : results) {
            String originatingPersonName = StringUtils.safeHtml(result.personFile.getName());
            String relationshipType = result.relationType.toString().toLowerCase();

            // if the connection is to the originating person
            String cleanNewPersonName = StringUtils.safeHtml(newPersonName);
            if (result.originatingPerson().equals(result.personFile())) {
                sb.append(String.format("<li>%s will get a new %s: %s</li>\n",
                        originatingPersonName,
                        relationshipType,
                        cleanNewPersonName));
            } else {
                // otherwise, if the connection is to a relative of the originating person
                sb.append(String.format("<li>%s %s will get a new %s: %s</li>\n",
                        result.relationToFirstPerson().toString().toLowerCase(),
                        originatingPersonName,
                        relationshipType,
                        cleanNewPersonName));
            }
            // break out after the first loop if we don't want to find all the connections
            if (!findAllConnections) break;
        }
        return sb.toString();

    }

    /**
     * This checks whether a relationship is impossible - like someone
     * being their own parent, sibling, child, or spouse.
     */
    private boolean isImpossibleRelationship(PersonNode person1, PersonNode person2) {
        return person1.equals(person2);
    }

    private List<RelationshipConnectionInfo> calculateNecessaryConnections(
            RelationType relationType,
            PersonNode originalPersonNode,
            String otherPersonId,
            String relationName,
            User user) {

        // initialize as empty.  If it stays this way, it means we are connecting to a freshly-created person.
        // if it gets set to something, it means we are connecting to an existing person
        PersonNode newRelationNode = PersonNode.EMPTY;
        // if we're connecting to an existing person, create a node from it and check if impossible relation
        if (otherPersonId != null && ! otherPersonId.isBlank()) {
            UUID otherPersonUuid = UUID.fromString(otherPersonId);
            newRelationNode = personEndpoints.familyGraphBuilder.getPersonNodes().get(otherPersonUuid);
        }
        List<RelationshipConnectionInfo> result = new ArrayList<>();
        IPersonLruCache personLruCache = personEndpoints.getPersonLruCache();
        PersonFile originalPersonFile = personLruCache.getCachedPersonFile(originalPersonNode.getId().toString());
        final RelationshipConnectionInfo firstOrderRelationship = new RelationshipConnectionInfo(
                originalPersonFile, null, originalPersonFile, user, otherPersonId, relationName, relationType);

        // add the simple direct connection from this person to the new parent
        result.add(firstOrderRelationship);

        switch (relationType) {
            case PARENT -> {
                /*
                algorithm: in addition to the basic behavior of attaching a child to a parent, get the
                original person's parents and siblings, and attach the siblings to the new parent
                bidirectionally and attach the existing parent(s) to the new parent bidirectionally as
                spouse
                */
                List<Map.Entry<String, PersonNode>> parents = originalPersonNode.getConnections().stream().filter(x -> x.getKey().equals("parent")).toList();
                List<Map.Entry<String, PersonNode>> siblings = originalPersonNode.getConnections().stream().filter(x -> x.getKey().equals("sibling")).toList();

                for (Map.Entry<String, PersonNode> parent : parents) {
                    // add the connection from parents to the new parent
                    if (isImpossibleRelationship(parent.getValue(), newRelationNode)) continue;
                    PersonFile parentPersonFile = personLruCache.getCachedPersonFile(parent.getValue().getId().toString());
                    result.add(new RelationshipConnectionInfo(originalPersonFile, RelationType.PARENT, parentPersonFile, user, otherPersonId, relationName, RelationType.SPOUSE));
                }
                for (Map.Entry<String, PersonNode> sibling : siblings) {
                    // add the connection from siblings to the new parent
                    if (isImpossibleRelationship(sibling.getValue(), newRelationNode)) continue;
                    PersonFile siblingPersonFile = personLruCache.getCachedPersonFile(sibling.getValue().getId().toString());
                    result.add(new RelationshipConnectionInfo(originalPersonFile, RelationType.SIBLING, siblingPersonFile, user, otherPersonId, relationName, RelationType.PARENT));
                }
            }
            case SPOUSE -> {
                /*
                algorithm: in addition to the basic behavior of attaching two spouses, get the original
                person's children, attach new parent to them bidirectionally.

                This section looks a little different than the others because unlike those, if I add a new
                spouse for someone, it's not like all the other spouses get married to that new spouse!
                 */
                List<Map.Entry<String, PersonNode>> children = originalPersonNode.getConnections().stream().filter(x -> x.getKey().equals("child")).toList();
                for (Map.Entry<String, PersonNode> child : children) {
                    // add the connection from children to the new spouse
                    if (isImpossibleRelationship(child.getValue(), newRelationNode)) continue;
                    PersonFile childPersonFile = personLruCache.getCachedPersonFile(child.getValue().getId().toString());
                    result.add(new RelationshipConnectionInfo(originalPersonFile, RelationType.CHILD, childPersonFile, user, otherPersonId, relationName, RelationType.PARENT));
                }
            }
            case SIBLING -> {
                /*
                algorithm: in addition to the basic behavior of attaching two siblings, get the original person's
                parents and siblings, and attach the new sibling to each of the siblings bidirectionally, and to
                the parents bidirectionally.
                 */
                List<Map.Entry<String, PersonNode>> parents = originalPersonNode.getConnections().stream().filter(x -> x.getKey().equals("parent")).toList();
                List<Map.Entry<String, PersonNode>> siblings = originalPersonNode.getConnections().stream().filter(x -> x.getKey().equals("sibling")).toList();
                for (Map.Entry<String, PersonNode> parent : parents) {
                    // add the connection from parents to the new sibling
                    if (isImpossibleRelationship(parent.getValue(), newRelationNode)) continue;
                    PersonFile parentPersonFile = personLruCache.getCachedPersonFile(parent.getValue().getId().toString());
                    result.add(new RelationshipConnectionInfo(originalPersonFile, RelationType.PARENT, parentPersonFile, user, otherPersonId, relationName, RelationType.CHILD));
                }
                for (Map.Entry<String, PersonNode> sibling : siblings) {
                    // add the connection from siblings to the new sibling
                    if (isImpossibleRelationship(sibling.getValue(), newRelationNode)) continue;
                    PersonFile siblingPersonFile = personLruCache.getCachedPersonFile(sibling.getValue().getId().toString());
                    result.add(new RelationshipConnectionInfo(originalPersonFile, RelationType.SIBLING, siblingPersonFile, user, otherPersonId, relationName, RelationType.SIBLING));
                }
            }
            case CHILD -> {
                /*
                algorithm: in addition to the basic behavior of attaching a child to a parent, get the original
                person's spouses and children, and attach the child to the other parents bidirectionally and as
                a sibling to the other children, bidirectionally
                 */
                List<Map.Entry<String, PersonNode>> children = originalPersonNode.getConnections().stream().filter(x -> x.getKey().equals("child")).toList();
                List<Map.Entry<String, PersonNode>> spouses = originalPersonNode.getConnections().stream().filter(x -> x.getKey().equals("spouse")).toList();
                for (Map.Entry<String, PersonNode> child : children) {
                    // add the connection from children to the new child
                    if (isImpossibleRelationship(child.getValue(), newRelationNode)) continue;
                    PersonFile childPersonFile = personLruCache.getCachedPersonFile(child.getValue().getId().toString());
                    result.add(new RelationshipConnectionInfo(originalPersonFile, RelationType.CHILD, childPersonFile, user, otherPersonId, relationName, RelationType.SIBLING));
                }
                for (Map.Entry<String, PersonNode> spouse : spouses) {
                    // add the connection from spouses to the new child
                    if (isImpossibleRelationship(spouse.getValue(), newRelationNode)) continue;
                    PersonFile spousePersonFile = personLruCache.getCachedPersonFile(spouse.getValue().getId().toString());
                    result.add(new RelationshipConnectionInfo(originalPersonFile, RelationType.SPOUSE, spousePersonFile, user, otherPersonId, relationName, RelationType.CHILD));
                }
            }
            default -> throw new BadUserInputException("No matching relationTypeString at calculateThoroughDescription");
        }
        return result;
    }

    /**
     * Handle a POST request for setting the values on a person.
     */
    public IResponse editPersonPost(IRequest r) {
        AuthResult authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return auth.htmlForbidden();
        }
        Person person;
        try {
            person = personCreateServices.processPersonEditPost(r);
            auditor.audit(() -> String.format("user %s has edited person %s (%d)", authResult.user().getUsername(), person.getName(), person.getIndex()), authResult.user());
        } catch (CircularLoopException ex) {
            logger.logDebug(() -> "Cycle in family tree would have been added. " + ex);
            String id = r.getBody().asString("id");
            UUID uuid;
            try {
                uuid = UUID.fromString(id);
                return redirect(ex.getMessage(), "/editperson?id=" + uuid);
            } catch (IllegalArgumentException e) {
                throw new BadUserInputException("The provided id was not a valid UUID: " + id);
            }
        } catch (BadUserInputException ex) {
            logger.logDebug(() -> "bad user input: " + ex.getMessage());
            return Respond.userInputError();
        }

        return Response.buildLeanResponse(StatusLine.StatusCode.CODE_303_SEE_OTHER, Map.of("location","/editperson?id="+person.getId()));
    }

    /**
     * An endpoint to describe to the user what will happen when deleting
     * a person, and providing a button to delete that person.
     */
    public IResponse deletePersonGet(IRequest request) {
        AuthResult authResult = auth.processAuth(request);
        if (!authResult.isAuthenticated()) {
            return auth.htmlForbidden();
        }

        String id = request.getRequestLine().queryString().get("id");

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

        String myNavHeader = navigationHeader.renderNavigationHeader(request, true, true, id);

        return Respond.htmlOk(deletePersonTemplateProcessor.renderTemplate(Map.of(
                "id", id,
                "person_name", StringUtils.safeHtml(person.getName()),
                "navigation_header", myNavHeader
        )));
    }

    /**
     * Deletes a person and their
     * associated data
     * @param isPost if true, we'll handle this as a POST request.
     */
    public IResponse deletePerson(IRequest request, boolean isPost) {
        AuthResult authResult = auth.processAuth(request);
        if (!authResult.isAuthenticated()) {
            return auth.htmlForbidden();
        }

        var id = isPost ? request.getBody().asString("id") :
                request.getRequestLine().queryString().get("id");

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
        auditor.audit(() -> String.format("%s is deleting person: %s", username, person), authResult.user());

        try {
            personTrasher.moveToTrash(authResult.user(), person);
        } catch (IOException e) {
            String exception = StacktraceUtils.stackTraceToString(e);
            logger.logAsyncError(() -> String.format("Error: Failed to put person %s (%d) in trash: %s", person.getName(), person.getIndex(), exception));
            return Response.buildLeanResponse(CODE_500_INTERNAL_SERVER_ERROR);
        }

        if (isPost) {
            String message = String.format("A person named %s has been deleted", person.getName());
            return redirect(message, "/editpersons");
        } else {
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_204_NO_CONTENT);
        }
    }


}
