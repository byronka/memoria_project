package com.renomad.inmra.featurelogic.version;

import com.renomad.inmra.auth.AuthResult;
import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.featurelogic.persons.PersonFile;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.NavigationHeader;
import com.renomad.inmra.utils.Respond;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.SearchUtils;
import com.renomad.minum.utils.StringUtils;
import com.renomad.minum.web.IRequest;
import com.renomad.minum.web.IResponse;
import com.renomad.minum.web.Response;
import com.renomad.minum.web.StatusLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.renomad.inmra.utils.FileUtils.badFilePathPatterns;

public class Versioning {

    private final IAuthUtils authUtils;
    private final VersionUtils versionUtils;
    private final ILogger logger;
    private final Path personFileDirectory;
    private final Path personFileAuditDirectory;
    private final TemplateProcessor versionViewTemplate;
    private final NavigationHeader navigationHeader;

    public Versioning(IAuthUtils authUtils, Context context, IFileUtils fileUtils, NavigationHeader navigationHeader) {
        this.authUtils = authUtils;
        this.versionUtils = new VersionUtils();
        this.logger = context.getLogger();
        var dbDir = Path.of(context.getConstants().dbDirectory);
        this.personFileDirectory = dbDir.resolve("person_files");
        this.personFileAuditDirectory = dbDir.resolve("person_file_audit_logs");
        this.versionViewTemplate = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/version_viewer_template.html"));
        this.navigationHeader = navigationHeader;
    }

    public IResponse versionGet(IRequest request) {
        AuthResult authResult = authUtils.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return authUtils.htmlForbidden();
        }

        String id = request.getRequestLine().queryString().get("id");

        if (id == null || id.isBlank()) {
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }

        PersonFile personFile = getCachedPersonFile(id);
        List<PersonFileVersionEntry> audits = getAudits(id);

        if (audits.isEmpty()) {
            return Respond.htmlOk("<p>There are no previous versions</p>");
        }

        String selectedDateStampString = request.getRequestLine().queryString().get("date");

        if (selectedDateStampString == null || id.isBlank()) {
            selectedDateStampString = audits.getLast().dateTimeStamp().toString();
        }
        ZonedDateTime selectedDateStamp = ZonedDateTime.parse(selectedDateStampString);

        StringBuilder dateOptions = new StringBuilder();
        PersonFileVersionEntry currentEntry = SearchUtils.findExactlyOne(audits.stream(), x -> x.dateTimeStamp().equals(selectedDateStamp));

        for (PersonFileVersionEntry entry : audits) {
            boolean isSelected = entry.dateTimeStamp().equals(selectedDateStamp);
            dateOptions
                    .append(String.format("<option value=\"%s\" %s>\n", entry.dateTimeStamp(), isSelected ? "selected" : ""))
                    .append(entry.dateTimeStamp().truncatedTo(ChronoUnit.MINUTES))
                    .append("\n</option>\n");
        }

        String myNavHeader = navigationHeader.renderNavigationHeader(request, true, true, id, true, null);

        Map<String, String> keyValuePairs = new HashMap<>();
        keyValuePairs.put("navigation_header",myNavHeader);
        keyValuePairs.put("id", id);
        keyValuePairs.put("date_options", dateOptions.toString());
        keyValuePairs.put("person_name", personFile.getName());
        keyValuePairs.put("date_changed", selectedDateStampString);
        keyValuePairs.put("username", currentEntry.userName());
        keyValuePairs.put("born_value", currentEntry.personFile().getBorn().toHtmlString());
        keyValuePairs.put("died_value", currentEntry.personFile().getDied().toHtmlString());
        keyValuePairs.put("gender_value", currentEntry.personFile().getGender().toString());
        keyValuePairs.put("image_url_html_diff", versionUtils.showDiffFromPatch(currentEntry.personFile().getImageUrl(), personFile.getImageUrl()));
        keyValuePairs.put("name_html_diff", versionUtils.showDiffFromPatch(currentEntry.personFile().getName(), personFile.getName()));
        keyValuePairs.put("siblings_html_diff",  versionUtils.showDiffFromPatch(currentEntry.personFile().getSiblings(), personFile.getSiblings()));
        keyValuePairs.put("spouses_html_diff",  versionUtils.showDiffFromPatch(currentEntry.personFile().getSpouses(), personFile.getSpouses()));
        keyValuePairs.put("parents_html_diff",  versionUtils.showDiffFromPatch(currentEntry.personFile().getParents(), personFile.getParents()));
        keyValuePairs.put("children_html_diff",  versionUtils.showDiffFromPatch(currentEntry.personFile().getChildren(), personFile.getChildren()));
        keyValuePairs.put("biography_html_diff",  versionUtils.showDiffFromPatch(currentEntry.personFile().getBiography(), personFile.getBiography()));
        keyValuePairs.put("auth_bio_html_diff",  versionUtils.showDiffFromPatch(currentEntry.personFile().getAuthBio(), personFile.getAuthBio()));
        keyValuePairs.put("notes_html_diff",  versionUtils.showDiffFromPatch(currentEntry.personFile().getNotes(), personFile.getNotes()));
        keyValuePairs.put("extra_fields_html_diff",  versionUtils.showDiffFromPatch(currentEntry.personFile().getExtraFields(), personFile.getExtraFields()));

        return Respond.htmlOk(this.versionViewTemplate.renderTemplate(keyValuePairs));
    }


    private PersonFile getCachedPersonFile(String uuidForPerson) {
        try {
            // the following is just to check that we are able to build a real UUID from the incoming
            // id string, to ensure it's at least a real UUID.
            UUID.fromString(uuidForPerson);
        } catch (IllegalArgumentException ex) {
            logger.logDebug(() -> "Input to getCachedPersonFile was not a valid uuid.  Returning an empty person file.  Value was: " + uuidForPerson);
            return PersonFile.EMPTY;
        }
        if (badFilePathPatterns.matcher(uuidForPerson).find()) {
            logger.logDebug(() -> String.format("Bad path requested for getCachedPersonFile: %s", uuidForPerson));
            return PersonFile.EMPTY;
        }
        String personFileRaw;
        try {
            boolean personFileExists = Files.exists(personFileDirectory.resolve(uuidForPerson));
            if (personFileExists) {
                personFileRaw = Files.readString(personFileDirectory.resolve(uuidForPerson));
            } else {
                logger.logDebug(() -> "requested personfile of " + uuidForPerson + " did not exist.  Returning an empty person file");
                return PersonFile.EMPTY;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return PersonFile.EMPTY.deserialize(personFileRaw);
    }

    private List<PersonFileVersionEntry> getAudits(String uuidForPerson) {
        try {
            // the following is just to check that we are able to build a real UUID from the incoming
            // id string, to ensure it's at least a real UUID.
            UUID.fromString(uuidForPerson);
        } catch (IllegalArgumentException ex) {
            logger.logDebug(() -> "Input to getAudits was not a valid uuid.  Returning an empty person file.  Value was: " + uuidForPerson + ". Returning an empty list.");
            return List.of();
        }
        if (badFilePathPatterns.matcher(uuidForPerson).find()) {
            logger.logDebug(() -> String.format("Bad path requested for getAudits: %s. Returning an empty list.", uuidForPerson));
            return List.of();
        }
        String personAuditFileContents;
        try {
            String auditFilename = uuidForPerson + ".audit";
            boolean personFileExists = Files.exists(personFileAuditDirectory.resolve(auditFilename));
            if (personFileExists) {
                personAuditFileContents = Files.readString(personFileAuditDirectory.resolve(auditFilename));
            } else {
                logger.logDebug(() -> "requested audit of personfile of " + uuidForPerson + " did not exist.  Returning an empty list");
                return List.of();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // each item is a previous version.  The first entry is the oldest
        String[] previousVersions = personAuditFileContents.split("\n");

        ArrayList<PersonFileVersionEntry> datedPersonAudits = new ArrayList<>();
        for (String previousVersion : previousVersions) {
            // this will split into 4 parts - the date, the username who made that audit, their id, and finally the patch data
            String[] split = previousVersion.split("\t");
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(split[0]);
            long userId = Long.parseLong(split[2]);
            PersonFile personFile = PersonFile.EMPTY.deserialize(split[3]);
            String username = StringUtils.decode(split[1]);
            datedPersonAudits.add(new PersonFileVersionEntry( personFile, username, userId, zonedDateTime));
        }

        return datedPersonAudits;
    }

}
