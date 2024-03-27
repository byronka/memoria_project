package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.featurelogic.persons.Date;
import com.renomad.inmra.featurelogic.persons.*;
import com.renomad.inmra.utils.FileWriteStringWrapper;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.Context;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.utils.StringUtils;
import com.renomad.minum.web.Request;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.renomad.minum.utils.SearchUtils.findExactlyOne;

public class PersonCreateServices {


    private final PersonEndpoints personEndpoints;
    private final ILogger logger;
    private final IPersonLruCache personLruCache;
    private final FamilyGraphBuilder familyGraphBuilder;
    private final Random random;
    private final TemplateProcessor extraFieldTemplateProcessor;
    private final IAuthUtils auth;
    private final Path personAuditDirectory;
    private final Path personFileAuditDirectory;
    private final PersonAuditor personAuditor;

    public PersonCreateServices(
            PersonEndpoints personEndpoints,
            ILogger logger,
            MemoriaContext memoriaContext,
            Context context,
            IAuthUtils auth,
            IPersonLruCache personLruCache,
            FamilyGraphBuilder familyGraphBuilder
            ) {
        this.personEndpoints = personEndpoints;
        this.logger = logger;
        this.personLruCache = personLruCache;
        this.familyGraphBuilder = familyGraphBuilder;
        var fileUtils = memoriaContext.fileUtils();
        extraFieldTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/extra_field_template.html"));
        random = new Random();
        this.auth = auth;
        this.personAuditor = new PersonAuditor(context, new FileWriteStringWrapper());

        // we will store old data for persons in the audit directories.  Yes, each time an
        // edit takes place, we'll add the whole data as an audit entry.
        personAuditDirectory = Path.of(context.getConstants().dbDirectory).resolve("person_audit_logs");
        personFileAuditDirectory = Path.of(context.getConstants().dbDirectory).resolve("person_file_audit_logs");
        buildNecessaryDirectories(logger, fileUtils, personAuditDirectory, personFileAuditDirectory);
    }

    static void buildNecessaryDirectories(
            ILogger logger,
            IFileUtils fileUtils,
            Path personAuditDirectory,
            Path personFileAuditDirectory) {
        try {
            fileUtils.makeDirectory(personAuditDirectory);
            fileUtils.makeDirectory(personFileAuditDirectory);
        } catch (IOException e) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(e));
        }
    }


    enum BornOrDied {
        BORN,
        DIED
    }

    public void addValuesForEditingExistingPerson(
            Map<String, String> templateMap,
            String id,
            PersonFile deserializedPersonFile,
            String authHeader
    ) {
        addLifespanDate(templateMap, deserializedPersonFile.getBorn(), BornOrDied.BORN);
        addLifespanDate(templateMap, deserializedPersonFile.getDied(), BornOrDied.DIED);

        templateMap.put("header", authHeader);
        templateMap.put("id", id);
        templateMap.put("title", "Edit " + StringUtils.safeHtml(deserializedPersonFile.getName()));
        templateMap.put("image_input_value", StringUtils.safeAttr(deserializedPersonFile.getImageUrl()));
        templateMap.put("name_input_value", StringUtils.safeAttr(deserializedPersonFile.getName()));
        templateMap.put("siblings_input_value", StringUtils.safeHtml(deserializedPersonFile.getSiblings()));
        templateMap.put("spouses_input_value", StringUtils.safeHtml(deserializedPersonFile.getSpouses()));
        templateMap.put("parents_input_value", StringUtils.safeHtml(deserializedPersonFile.getParents()));
        templateMap.put("children_input_value", StringUtils.safeHtml(deserializedPersonFile.getChildren()));
        templateMap.put("biography_input_value", StringUtils.safeAttr(deserializedPersonFile.getBiography()));
        templateMap.put("notes_input_value", StringUtils.safeAttr(deserializedPersonFile.getNotes()));
        templateMap.put("cancel_href", "/person?id=" + id);
        handleGenderTemplateValues(templateMap, deserializedPersonFile);
        handleExtraFieldValues(templateMap, deserializedPersonFile);
    }



    /**
     * Handles the processing for lifespan dates for a person on the
     * page for editing persons.
     * <br>
     * Because of the inherent complexities, this method was necessary. For
     * example, a person's birth/death date may be unknown.  Or a single value - a year.
     * Or may just be empty.  This method takes care of setting proper values
     * so the template is filled out correctly.
     * <br>
     * This method is called once for each date - birth and death date.
     * <br>
     * Some interesting considerations:
     * We may not know the person's birthdate or deathdate.
     * They may be still alive - so they have no deathdate.
     * @param templateMap the map of key-value pairs for filling out the HTML template - see person_edit.html,
     *                    for born_input_value, died_input_value, born_input_checked, died_input_checked
     * @param date the date value we are working with.
     * @param bornOrDied whether this is a born date or died date.
     */
    static void addLifespanDate(Map<String, String> templateMap, com.renomad.inmra.featurelogic.persons.Date date, BornOrDied bornOrDied) {
        // this will be either "born" or "died", for use in constructing symbols
        String myBornOrDied = bornOrDied.toString().toLowerCase();

        // check if the born or died dates are unknown - meaning, they
        // definitely exist, we just don't know what the date is.
        String isUnknownChecked = "";
        String dateDisabled = "";
        if (date.equals(com.renomad.inmra.featurelogic.persons.Date.EXISTS_BUT_UNKNOWN)) {
            isUnknownChecked = "checked";
            dateDisabled = "disabled";
        }

        String isYearOnlyChecked = "";
        String dateValue = "";
        String dateInputType = "date";
        if (date.month().equals(Month.NONE)) {
            isYearOnlyChecked = "checked";
            dateValue = String.valueOf(date.year());
            dateInputType = "number";
        } else if (
                ! date.equals(com.renomad.inmra.featurelogic.persons.Date.EXISTS_BUT_UNKNOWN) &&
                ! date.equals(com.renomad.inmra.featurelogic.persons.Date.EMPTY)) {
            dateValue = date.toHtmlString();
        }

        // can set some values at this point
        templateMap.put("is_"+ myBornOrDied +"_date_unknown_checked", isUnknownChecked);
        templateMap.put("is_" + myBornOrDied + "_date_unknown", dateDisabled);
        templateMap.put("is_" + myBornOrDied +"_date_year_only_checked", isYearOnlyChecked);
        templateMap.put(myBornOrDied + "_date_input_type", dateInputType);
        templateMap.put(myBornOrDied +"_input_value", dateValue);
    }

    public Person buildNewRelation(
            String username,
            String relationName,
            String relationLink,
            PersonFile personFile,
            RelationType relationType) {
        RelationInputs relationInputs = fillRelationInputs(relationLink, relationType);
        // create the new relation
        Person newRelationPerson = createOrUpdatePersonData(
                null, username, relationName, "", relationInputs, "", "", Gender.UNKNOWN, com.renomad.inmra.featurelogic.persons.Date.EMPTY, com.renomad.inmra.featurelogic.persons.Date.EMPTY, "");

        // update the existing person with information about the new relation
        attachNewPersonToExistingPerson(username, personFile, relationType, newRelationPerson);
        return newRelationPerson;
    }

    /**
     * Modify the existing person - add the new relation's data to the
     * appropriate relation slot - e.g. parent, sibling, etc.
     * @param username the person doing this, so we can log who is doing this.
     * @param existingPersonFile the existing person to whom we are attaching the new relation
     * @param newRelationPerson the newly-created person we are attaching.
     */
    private void attachNewPersonToExistingPerson(
            String username,
            PersonFile existingPersonFile,
            RelationType relationType,
            Person newRelationPerson) {
        String extraRelationLink = String.format(" <a href=\"person?id=%s\">%s</a>", newRelationPerson.getId(), newRelationPerson.getName());
        switch (relationType) {
            case PARENT -> updateWithNewRelation(extraRelationLink, "", "", "", existingPersonFile, username);
            case SIBLING -> updateWithNewRelation("", extraRelationLink, "", "", existingPersonFile, username);
            case SPOUSE -> updateWithNewRelation("", "", extraRelationLink, "", existingPersonFile, username);
            case CHILD -> updateWithNewRelation("", "", "", extraRelationLink, existingPersonFile, username);
        }
    }

    /**
     * Depending on what kind of relationship we are creating, we will fill
     * out the new inputs differently.  For example, if we are creating a
     * new parent, then we will fill out the new person's child input with
     * data of the existing person.
     * @param relationLink the existing person's data, which we will add to the new
     *                     person's information in the appropriate relationship slot.
     * @param relationType The relationship of the new person we are creating.
     */
    static RelationInputs fillRelationInputs(String relationLink, RelationType relationType) {
        String parentLink = "";
        String siblingLink = "";
        String spouseLink = "";
        String childLink = "";
        switch (relationType) {
            case PARENT -> childLink = relationLink;
            case SIBLING -> siblingLink = relationLink;
            case SPOUSE -> spouseLink = relationLink;
            case CHILD -> parentLink = relationLink;
        }
        return new RelationInputs(parentLink, siblingLink, spouseLink, childLink);
    }

    record RelationInputs(String parentInput, String siblingInput, String spouseInput, String childInput) {
    }

    /**
     * This method takes extra data for the relations fields, so we can
     * add anchor tags to other relations like siblings, parents, etc.
     * @param username the username of the user carrying out this action, used for logging
     * @param existingPersonFile the existing person, for whom we just created a new relation
     */
    private void updateWithNewRelation(
            String extraParents,
            String extraSiblings,
            String extraSpouses,
            String extraChildren,
            PersonFile existingPersonFile,
            String username) {
        RelationInputs relationInputs = new RelationInputs(
                existingPersonFile.getParents() + extraParents,
                existingPersonFile.getSiblings() + extraSiblings,
                existingPersonFile.getSpouses() + extraSpouses,
                existingPersonFile.getChildren() + extraChildren);
        com.renomad.inmra.featurelogic.persons.Date bornDate = existingPersonFile.getBorn();
        com.renomad.inmra.featurelogic.persons.Date diedDate = existingPersonFile.getDied();
        createOrUpdatePersonData(
                existingPersonFile.getId().toString(),
                username,
                existingPersonFile.getName(),
                existingPersonFile.getImageUrl(),
                relationInputs,
                existingPersonFile.getBiography(),
                existingPersonFile.getNotes(),
                existingPersonFile.getGender(),
                bornDate,
                diedDate,
                existingPersonFile.getExtraFields());
    }

    /**
     * When editing an existing {@link Person}, preparing the template with
     * proper values for the extra fields is a complex operation.
     */
    private void handleExtraFieldValues(Map<String, String> templateMap, PersonFile deserializedPersonFile) {
        // add the extra fields
        StringBuilder extraFieldsBuilder = new StringBuilder();
        List<PersonFile.ExtraFieldTriple> extraFields = deserializedPersonFile.getExtraFieldsAsList();
        int index = 1;
        for(var field : extraFields) {
            String renderedField = extraFieldTemplateProcessor.renderTemplate(Map.of(
                    "count", "_" + index,
                    "key", StringUtils.safeAttr(field.key()),
                    "key_html_cleaned", StringUtils.safeHtml(field.key()),
                    "type", StringUtils.safeAttr(field.type()),
                    "value", StringUtils.safeAttr(field.value()),
                    "value_html_cleaned", StringUtils.safeHtml(field.value())
            ));
            extraFieldsBuilder.append(renderedField);
            index += 1;
        }
        templateMap.put("extra_fields", extraFieldsBuilder.toString());
        templateMap.put("extra_field_count", String.valueOf(extraFields.size()));
        templateMap.put("extra_field_array", IntStream.rangeClosed(1,extraFields.size()).boxed().map(String::valueOf).collect(Collectors.joining(",")));
    }

    /**
     * When editing an existing {@link Person}, we need to set the proper
     * value for their gender field.
     */
    private static void handleGenderTemplateValues(Map<String, String> templateMap, PersonFile deserializedPersonFile) {
        // handle the gender radio buttons
        Gender gender = deserializedPersonFile.getGender();
        String unset_checked = "";
        String male_checked = "";
        String female_checked = "";

        switch (gender) {
            case MALE:
                male_checked = "checked";
                break;
            case FEMALE:
                female_checked = "checked";
                break;
            default:
                unset_checked = "checked";
                break;
        }
        templateMap.put("unset_checked", unset_checked);
        templateMap.put("male_checked", male_checked);
        templateMap.put("female_checked", female_checked);
    }

    /**
     * This one is easy.  For setting up the template when creating
     * a totally new Person, it's pretty much always the same.
     */
    public void addEmptyValuesToTemplate(Map<String, String> templateMap, String authHeader) {
        templateMap.put("header", authHeader);
        templateMap.put("title", "Add New Person");
        templateMap.put("id",                               "");
        templateMap.put("image_input_value",                "");
        templateMap.put("name_input_value",                 "");
        templateMap.put("born_input_value",                 "");
        templateMap.put("is_born_date_unknown_checked",     "");
        templateMap.put("is_born_date_unknown",             "");
        templateMap.put("is_born_date_year_only_checked",   "");
        templateMap.put("born_date_input_type",             "date");
        templateMap.put("died_input_value",                 "");
        templateMap.put("is_died_date_unknown_checked",     "");
        templateMap.put("is_died_date_unknown",     "");
        templateMap.put("is_died_date_year_only_checked",   "");
        templateMap.put("died_date_input_type",             "date");
        templateMap.put("siblings_input_value",              "");
        templateMap.put("spouses_input_value",               "");
        templateMap.put("parents_input_value",               "");
        templateMap.put("children_input_value",              "");
        templateMap.put("biography_input_value",             "");
        templateMap.put("notes_input_value",                 "");
        templateMap.put("unset_checked",              "checked");
        templateMap.put("male_checked",                      "");
        templateMap.put("female_checked",                    "");
        templateMap.put("extra_fields",                      "");
        templateMap.put("cancel_href",      "/editpersons");
        templateMap.put("extra_field_count", String.valueOf(0));
        templateMap.put("extra_field_array", "");
    }

    /**
     * This is the code that processes a POST request from
     * the client wanting to create or edit a person.
     * <br>
     * This method primarily focuses on extracting the data from
     * the key-value pairs sent in the body.
     */
    public Person processPersonEditPost(Request r) throws BadUserInputException {
        final var id                    =   r.body().asString("id");
        final var imageInput            =   r.body().asString("image_input");
        final var nameInput             =   r.body().asString("name_input");
        final var bornInput             =   r.body().asString("born_input");
        final var bornDateUnknownInput  =   r.body().asString("born_date_unknown");
        final var diedInput             =   r.body().asString("died_input");
        final var diedDateUnknownInput  =   r.body().asString("death_date_unknown");
        final var siblingsInput         =   r.body().asString("siblings_input");
        final var spousesInput          =   r.body().asString("spouses_input");
        final var parentsInput          =   r.body().asString("parents_input");
        final var childrenInput         =   r.body().asString("children_input");
        final var biographyInput        =   r.body().asString("biography_input");
        final var notesInput            =   r.body().asString("notes_input");
        final var genderInput           =   r.body().asString("gender_input");


        return processPersonData(r, nameInput, bornInput, bornDateUnknownInput,
                diedInput, diedDateUnknownInput, id, imageInput, siblingsInput,
                spousesInput, parentsInput, childrenInput, biographyInput, notesInput, genderInput);
    }

    /**
     * Handles a bit of intermediate data processing before
     * writing the data to disk and database.
     */
    private Person processPersonData(
            Request r,
            String nameInput,
            String bornInput,
            String bornDateUnknownInput,
            String diedInput,
            String diedDateUnknownInput,
            String id,
            String imageInput,
            String siblingsInput,
            String spousesInput,
            String parentsInput,
            String childrenInput,
            String biographyInput,
            String notesInput,
            String genderInput) {
        String username = auth.processAuth(r).user().getUsername();

        // handle the extra fields
        String extraFields = obtainExtraFields(r);

        // if they create or edit a person and give us an empty name,
        // bail with a 400 error.  That's not allowed.
        if (nameInput == null || nameInput.isBlank()) {
            throw new BadUserInputException("nameInput was null or blank", "<p>A person must have a name</p>");
        }

        com.renomad.inmra.featurelogic.persons.Date bornDate = extractDate(bornInput, bornDateUnknownInput);
        com.renomad.inmra.featurelogic.persons.Date diedDate = extractDate(diedInput, diedDateUnknownInput);
        Gender gender = Gender.deserialize(genderInput);
        var relationInputs = new RelationInputs(parentsInput, siblingsInput, spousesInput, childrenInput);

        return createOrUpdatePersonData(id, username, nameInput, imageInput, relationInputs,
                biographyInput, notesInput, gender, bornDate, diedDate, extraFields);
    }

    /**
     * Given the necessary data, create or update a person
     * in the database.
     *
     * @param id       the UUID value of this person, as a string. If null, we're creating a new person.
     * @param username the username of the user carrying out this action, for logging
     */
    private Person createOrUpdatePersonData(
            String id,
            String username,
            String nameInput,
            String imageInput,
            RelationInputs relationInputs,
            String biographyInput,
            String notesInput,
            Gender genderInput,
            com.renomad.inmra.featurelogic.persons.Date bornDate,
            com.renomad.inmra.featurelogic.persons.Date diedDate,
            String extraFields
            ) {
        Person person;
        if (id != null && !id.isBlank()) {
            person = updateExistingPersonInDatabase(id, username, bornDate, diedDate, nameInput);

        } else {
            person = createNewPersonInDatabase(bornDate, diedDate, nameInput, username);
        }

        PersonFile personFile = new PersonFile(
                person.getIndex(),
                person.getId(),
                imageInput,
                nameInput,
                bornDate,
                diedDate,
                relationInputs.siblingInput(),
                relationInputs.spouseInput(),
                relationInputs.parentInput(),
                relationInputs.childInput(),
                biographyInput,
                notesInput,
                extraFields,
                genderInput,
                Instant.now(),
                username
        );

        personAuditor.storePersonToAudit(personFile.getId(), personFile.serialize(), personFileAuditDirectory, personFile.getName());

        try {
            Files.writeString(personEndpoints.getPersonDirectory().resolve(person.getId().toString()), personFile.serialize());
        } catch (IOException e) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(e));
            throw new RuntimeException("Error in updatePersonData: " + e.getMessage(), e);
        }

        // add this person to the LRU cache
        personLruCache.putToPersonFileLruCache(personFile.getId().toString(), personFile);

        familyGraphBuilder.rebuildFamilyTree(personFile);

        return person;
    }

    private Person createNewPersonInDatabase(
            com.renomad.inmra.featurelogic.persons.Date bornDate,
            com.renomad.inmra.featurelogic.persons.Date diedDate,
            String nameInput,
            String username) {
        Person person;
        // if we got back no id, this is a new person
        person = new Person(
                0L,
                UUID.randomUUID(),
                nameInput,
                bornDate,
                diedDate);
        logger.logAudit(() -> String.format("%s is adding information for a new person, %s id: %s", username, nameInput, person.getId()));
        personEndpoints.getPersonDb().write(person);

        personAuditor.storePersonToAudit(person.getId(), person.serialize(), personAuditDirectory, person.getName());

        return person;
    }

    /**
     *
     * @param id the UUID of this person
     * @param username the username of the user carrying out this action, for logging
     */
    private Person updateExistingPersonInDatabase(String id, String username, com.renomad.inmra.featurelogic.persons.Date bornDate, com.renomad.inmra.featurelogic.persons.Date diedDate, String nameInput) {
        Person person;
        // if we got an id, then we're editing an existing person
        person = findExactlyOne(
                this.personEndpoints.getPersonDb().values().stream(),
                x -> x.getId().toString().equals(id));
        // if we don't find anyone with this id, log it and return a 400 complaint.
        if (person == null) {
            int newRand = random.nextInt();
            logger.logDebug(() -> String.format("Error %d: We received a request to edit a person we don't recognize, with id %s", newRand, id));
            throw new BadUserInputException("after looking up a person by id, did not find anyone",
                    String.format("Server error: %d", newRand));
        }
        logger.logAudit(() -> String.format("%s is modifying person: %s", username, person));

        Person updatedPerson = new Person(
                person.getIndex(),
                person.getId(),
                nameInput,
                bornDate,
                diedDate
        );

        personAuditor.storePersonToAudit(updatedPerson.getId(), updatedPerson.serialize(), personAuditDirectory, person.getName());

        personEndpoints.getPersonDb().write(updatedPerson);
        // if we edited a person, remove them from the cache
        personLruCache.removeFromPersonFileLruCache(id);
        return person;
    }

    /**
     * Examines the input from the server and determines what the date
     * should be.  If we get a typical date input string, we'll parse
     * it and return that as the date.  If we get nothing, we'll set
     * the date to Date.EMPTY.  If the user has set the checkbox for
     * date unknown, we'll set the date to Date.EXISTS_BUT_UNKNOWN
     * @param dateInput a string from the client in a form like 2023-11-28, or a year (e.g. 1984) or null, or empty
     * @param dateUnknownInput the value from the client indicating that a date is unknown.  To
     *                         be clear, this means the date exists, but is unknown.  Like, we
     *                         know this person died, we just don't know when.  On the other hand,
     *                         if they haven't died yet, the date is empty (not unknown).
     */
    private static com.renomad.inmra.featurelogic.persons.Date extractDate(String dateInput, String dateUnknownInput) {
        if (dateUnknownInput != null && ! dateUnknownInput.isBlank()) {
            return com.renomad.inmra.featurelogic.persons.Date.EXISTS_BUT_UNKNOWN;
        }
        if (dateInput != null && !dateInput.isEmpty() && dateInput.length() <= 4) {
            try {
                int year = Integer.parseInt(dateInput);
                return new com.renomad.inmra.featurelogic.persons.Date(year, Month.NONE, 0);
            } catch (NumberFormatException ignore) {
                throw new BadUserInputException("User sent weird date value: " + dateInput);
            }
        }
        return (dateInput != null && ! dateInput.isBlank()) ? com.renomad.inmra.featurelogic.persons.Date.extractDate(dateInput) : Date.EMPTY;
    }

    private String obtainExtraFields(Request r) {
        List<String> extraDataKeys = r.body().getKeys().stream().filter(x -> x.startsWith("extra_data_key_")).sorted().toList();

        // a list to hold the extra field triples
        var myTriples = new ArrayList<PersonFile.ExtraFieldTriple>();

        // then we grab the extra fields, looping through the number we were given
        for (int i = 0; i < extraDataKeys.size(); i++) {
            getExtraFieldDataFromBody(r, extraDataKeys, i, myTriples);
        }

        // serialize the triples
        return PersonFile.ExtraFieldTriple.serialize(myTriples);
    }

    /**
     * Loop through the extra field data in the body, putting the
     * data into a collection of triples
     * @param r the request holding extra field data
     * @param extraDataKeys all the keys for extra data
     * @param extraDataIndex which one of the extra fields are we extracting
     * @param myTriples where we will store the extracted data
     */
    private static void getExtraFieldDataFromBody(
            Request r,
            List<String> extraDataKeys,
            int extraDataIndex,
            ArrayList<PersonFile.ExtraFieldTriple> myTriples) {
        // get the first "extra_data_key_n"
        String k = extraDataKeys.get(extraDataIndex);
        // replace the string with empty string so we can easily get the number at the end.
        String numberAtEndString = k.replace("extra_data_key_", "");
        int numberAtEnd = Integer.parseInt(numberAtEndString);
        String key = r.body().asString(k);
        String value = r.body().asString("extra_data_value_" + numberAtEnd);
        String type = r.body().asString("extra_data_type_" + numberAtEnd);

        myTriples.add(new PersonFile.ExtraFieldTriple(key, value, type));
    }


}
