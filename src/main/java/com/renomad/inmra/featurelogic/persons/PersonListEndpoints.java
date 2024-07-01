package com.renomad.inmra.featurelogic.persons;

import com.renomad.inmra.auth.AuthResult;
import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.featurelogic.persons.services.*;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.inmra.utils.Respond;
import com.renomad.minum.state.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.web.Request;
import com.renomad.minum.web.Response;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.renomad.minum.utils.StringUtils.safeHtml;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

public class PersonListEndpoints {
    private final TemplateProcessor personEditListPageTemplateProcessor;
    private final TemplateProcessor personListPageShortTemplateProcessor;
    private final PersonEndpoints personEndpoints;
    private final IAuthUtils auth;
    private final ILogger logger;
    private final RelationSearch relationSearch;
    private final DetailedViewRenderer detailedViewRenderer;
    private final EnhancedPersonList enhancedPersonList;
    private final PersonSearch personSearch;
    private final BirthDeathDays birthDeathDays;
    private final HomepagePersonRenderer homepagePersonRenderer;


    PersonListEndpoints(
            Context context,
            MemoriaContext memoriaContext,
            IAuthUtils auth,
            PersonEndpoints personEndpoints) {
        this.personEndpoints = personEndpoints;
        this.auth = auth;
        this.logger = context.getLogger();
        IFileUtils fileUtils = memoriaContext.fileUtils();

        personListPageShortTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/person_list_page_short.html"));
        personEditListPageTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/person_edit_list_page.html"));

        Db<Person> personDb = personEndpoints.getPersonDb();
        IPersonLruCache personLruCache = personEndpoints.personLruCache;
        Lifespan lifespan = new Lifespan(logger);

        this.relationSearch = new RelationSearch(personDb);
        this.detailedViewRenderer = new DetailedViewRenderer(fileUtils, personLruCache, personEndpoints.familyGraphBuilder, lifespan);
        this.enhancedPersonList = new EnhancedPersonList(logger, fileUtils, personDb, personLruCache, personEndpoints.photoToPersonDb);
        this.personSearch = new PersonSearch(personDb, personLruCache);
        this.birthDeathDays = new BirthDeathDays(personDb);
        this.homepagePersonRenderer = new HomepagePersonRenderer(fileUtils, personLruCache);
    }


    Response listAllPersonsGet(Request r) {
        String searchQuery = r.requestLine().queryString().get("search");
        List<Person> persons = personSearch.getPeople(searchQuery);
        var sb = new StringBuilder();
        if (persons.isEmpty()) {
            if (searchQuery != null && ! searchQuery.isBlank()) {
                // if there are no persons found, show a suitable message
                sb.append("<li>No persons found</li>");
            } else {
                // if there was no search, show a random selection of persons
                List<Person> randomPersons = personSearch.getRandomPeople(10);
                for (Person p : randomPersons) {
                    String renderedTemplate = homepagePersonRenderer.renderPersonTemplate(p);
                    sb.append(renderedTemplate);
                }
            }
        } else {
            // if there are one or more persons, render their templates
            for (Person p : persons) {
                String renderedTemplate = homepagePersonRenderer.renderPersonTemplate(p);
                sb.append(renderedTemplate);
            }
        }

        AuthResult authResult = this.auth.processAuth(r);
        String helpLink = String.format("""
                <a id="help" href="/general/%s.html">ⓘ</a>""", authResult.isAuthenticated() ? "adminhelp" : "help");

        String authHeaderRendered = personEndpoints.authHeader.getRenderedAuthHeader(r);
        Map<String, String> templateValues = Map.of(
                "list_items", sb.toString(),
                "birthAndDeathDays", birthDeathDays.addRecentBirthDeathDays(),
                "header", authHeaderRendered,
                "help_link", helpLink,
                "search_query", searchQuery != null && ! searchQuery.isBlank() ? "You searched for: " + safeHtml(searchQuery) : ""
        );
        return Respond.htmlOk(personListPageShortTemplateProcessor.renderTemplate(templateValues));
    }

    /**
     * Handles the search requests from the search field on the homepage
     */
    public Response searchPersonGet(Request request) {
        String query = request.requestLine().queryString().get("query");
        List<Person> people = personSearch.getPeople(query);
        String names = people.stream()
                .map(homepagePersonRenderer::renderPersonTemplate)
                .collect(Collectors.joining("\n"));
        if (names.isBlank()) {
            return new Response(CODE_204_NO_CONTENT);
        } else {
            return Respond.htmlOk(names);
        }

    }

    /**
     * If you are logged in and want to edit a person, this is the
     * list of persons
     */
    Response editListGet(Request r) {
        AuthResult authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return auth.htmlForbidden();
        }

        // get the search string
        String lowercaseSearch = Objects.requireNonNullElse(r.requestLine().queryString().get("search"), "").toLowerCase();
        // get the sort string
        String sort = Objects.requireNonNullElse(r.requestLine().queryString().get("sort"), "").toLowerCase();
        // get the page we're on, or default to page 1 (1 is the first page).
        int page = Integer.parseInt(Objects.requireNonNullElse(r.requestLine().queryString().get("page"), "1"));
        // get the identifier.  If this is given, we ignore everything else and show just one person
        String id = Objects.requireNonNullElse(r.requestLine().queryString().get("id"), "").toLowerCase();
        String authHeaderRendered = personEndpoints.authHeader.getRenderedAuthHeader(r);
        Map<String, String> listItems = enhancedPersonList.renderListOfPersons(lowercaseSearch, sort, page, authHeaderRendered, id);
        return Respond.htmlOk(personEditListPageTemplateProcessor.renderTemplate(listItems));
    }

    /**
     * View the details of a particular person
     */
    public Response viewPersonGet(Request r) {
        String id = r.requestLine().queryString().get("id");
        if (id == null) return new Response(CODE_400_BAD_REQUEST);

        PersonFile deserializedPersonFile;
        try {
            deserializedPersonFile = personEndpoints.personLruCache.getCachedPersonFile(id);
        } catch (Exception ex) {
            logger.logAsyncError(() -> "Error while viewing a person: " + StacktraceUtils.stackTraceToString(ex));
            return new Response(CODE_500_INTERNAL_SERVER_ERROR);
        }

        if (deserializedPersonFile.equals(PersonFile.EMPTY)) {
            return new Response(CODE_404_NOT_FOUND);
        }

        String renderedAuthHeader = personEndpoints.authHeader.getRenderedAuthHeader(r, id);

        AuthResult authResult = this.auth.processAuth(r);
        String helpLink = String.format("""
                <a id="help" href="/general/%s.html">ⓘ</a>""", authResult.isAuthenticated() ? "adminhelp" : "help");

        String renderedTemplate = detailedViewRenderer.renderPersonView(
                deserializedPersonFile,
                renderedAuthHeader,
                helpLink);
        return Respond.htmlOk(renderedTemplate);

    }

    /**
     * Search all persons by name for use in adding relations
     * to a person - this is called by some JavaScript on the
     * edit page for adding new relations like siblings, spouses, etc.
     */
    public Response searchRelationGet(Request request) {
        String query = request.requestLine().queryString().get("query");

        String renderedHtml = relationSearch.searchRelations(query);

        if (renderedHtml.isBlank()) {
            return new Response(CODE_204_NO_CONTENT);
        } else {
            return Respond.htmlOk(renderedHtml);
        }
    }

}
