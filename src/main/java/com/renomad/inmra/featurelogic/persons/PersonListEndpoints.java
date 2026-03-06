package com.renomad.inmra.featurelogic.persons;

import com.renomad.inmra.auth.AuthResult;
import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.auth.PrivacyCheckStatus;
import com.renomad.inmra.featurelogic.persons.services.*;
import com.renomad.inmra.featurelogic.photo.PhotoToPerson;
import com.renomad.inmra.featurelogic.photo.VideoToPerson;
import com.renomad.inmra.utils.*;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.SearchUtils;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.utils.StringUtils;
import com.renomad.minum.web.IRequest;
import com.renomad.minum.web.IResponse;
import com.renomad.minum.web.Response;
import com.renomad.minum.web.WebServerException;

import java.util.*;
import java.util.stream.Stream;

import static com.renomad.inmra.featurelogic.persons.FamilyGraph.calculateOrdinals;
import static com.renomad.inmra.featurelogic.persons.services.InterestingScore.getInterestingScore;
import static com.renomad.inmra.utils.RandomnessUtils.randomSample;
import static com.renomad.minum.utils.StringUtils.safeHtml;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

public class PersonListEndpoints {
    private final TemplateProcessor personEditListPageTemplateProcessor;
    private final TemplateProcessor personListPageShortTemplateProcessor;
    private final TemplateProcessor printableTreeTemplateProcessor;
    private final IAuthUtils auth;
    private final ILogger logger;
    private final FamilyGraphBuilder familyGraphBuilder;
    private final NavigationHeader navigationHeader;
    private final Map<UUID, PersonMetrics> personMetricsMap;
    private final RelationSearch relationSearch;
    private final DetailedViewRenderer detailedViewRenderer;
    private final EnhancedPersonList enhancedPersonList;
    private final PersonSearch personSearch;
    private final HomepagePersonRenderer homepagePersonRenderer;
    private final IPersonLruCache personLruCache;
    private final MemoriaContext memoriaContext;
    private final ObscureInformationProcessor oip;
    private final AbstractDb<Person> personDb;


    PersonListEndpoints(
            Context context,
            MemoriaContext memoriaContext,
            IAuthUtils auth,
            AbstractDb<Person> personDb,
            IPersonLruCache personLruCache,
            FamilyGraphBuilder familyGraphBuilder,
            AbstractDb<PhotoToPerson> photoToPersonDb,
            AbstractDb<VideoToPerson> videoToPersonDb,
            AbstractDb<PersonMetrics> personMetricsDb,
            NavigationHeader navigationHeader,
            Map<UUID, PersonMetrics> personMetricsMap) {
        this.auth = auth;
        this.logger = context.getLogger();
        this.familyGraphBuilder = familyGraphBuilder;
        this.navigationHeader = navigationHeader;
        this.personMetricsMap = personMetricsMap;
        this.memoriaContext = memoriaContext;
        IFileUtils fileUtils = memoriaContext.getFileUtils();

        personListPageShortTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/person_list_page_short.html"));
        personEditListPageTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/person_edit_list_page.html"));
        printableTreeTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/printable_tree_template.html"));

        Lifespan lifespan = new Lifespan(logger);

        this.personLruCache = personLruCache;
        this.personDb = personDb;
        var stats = new Stats(logger, fileUtils, personMetricsDb, this.personMetricsMap, photoToPersonDb, videoToPersonDb);
        this.detailedViewRenderer = new DetailedViewRenderer(fileUtils, personLruCache, familyGraphBuilder, lifespan, personDb, logger, stats);
        this.enhancedPersonList = new EnhancedPersonList(logger, fileUtils, personDb, personLruCache, photoToPersonDb, this.personMetricsMap, stats);
        this.personSearch = new PersonSearch(personDb, personLruCache, this.personMetricsMap);
        this.relationSearch = new RelationSearch(personLruCache, personSearch);
        this.homepagePersonRenderer = new HomepagePersonRenderer(fileUtils, personLruCache);
        this.oip = new ObscureInformationProcessor();
    }


    IResponse listAllPersonsGet(IRequest r) {
        String searchQuery = r.getRequestLine().queryString().get("search");
        String otherPersonId = r.getRequestLine().queryString().get("oid");

        UUID optionalOtherPersonId = null;
        if (otherPersonId != null && ! otherPersonId.isBlank()) {
            var deserializedOtherPersonFile = personLruCache.getCachedPersonFile(otherPersonId);
            if (deserializedOtherPersonFile.equals(PersonFile.EMPTY)) {
                logger.logDebug(() -> "While looking at regular person details, a user at ip address ("+ r.getRemoteRequester() +
                        ") provided an id for 'other' person that does not match anyone. ID: " + otherPersonId);
                return Respond.userInputError();
            }
            optionalOtherPersonId = deserializedOtherPersonFile.getId();
        }

        PrivacyCheckStatus privacyCheckStatus = auth.canShowPrivateInformation(r);

        String renderedPersons = renderPersonsForHomepage(searchQuery, privacyCheckStatus.canShowPrivateInformation(), optionalOtherPersonId);

        String navHeader = this.navigationHeader.renderNavigationHeader(r, privacyCheckStatus.isAdminAuthenticated(), privacyCheckStatus.isPrivacyAuthenticated(), "");
        String birthDeathDays = privacyCheckStatus.canShowPrivateInformation() ?
                memoriaContext.getCachedData().getBirthDeathDaysWithLivingRendered() :
                memoriaContext.getCachedData().getBirthDeathDaysRendered();
        Map<String, String> templateValues = Map.of(
                "list_items", renderedPersons,
                "birthAndDeathDays", birthDeathDays == null ? "" : birthDeathDays, // if GettingOlderLoop hasn't finished its startup, this could be null
                "search_query", searchQuery != null && ! searchQuery.isBlank() ? "You searched for: " + safeHtml(searchQuery) : "",
                "navigation_header", navHeader
        );
        return Respond.htmlOk(personListPageShortTemplateProcessor.renderTemplate(templateValues));
    }

    /**
     * This program determines what will be shown on the homepage.
     * <br>
     * There are a few paths this could take.  If coming in with a search query (i.e. a name, or portion thereof), that will be used
     * to find persons, both by finding the exact letters as a match in a name, as well as searching by sound (see {@link DaitchMokotoffSoundex}
     * @param searchQuery some letters - in the happy path, these are a name or portion of a name.
     * @param shouldShowPrivateInformation whether or not we are showing living persons
     * @param optionalOtherPersonId if provided, will be appended to person URL's, to continue finding connections between relatives.
     */
    String renderPersonsForHomepage(String searchQuery, boolean shouldShowPrivateInformation, UUID optionalOtherPersonId) {
        // if there is a search value, we'll run a search
        PersonSearchResult personSearchResult = personSearch.getPeople(searchQuery, shouldShowPrivateInformation, 20);
        Stream<Person> exactMatchesSortedByInteresting = personSearchResult.exactMatches().stream()
                .sorted(Comparator.comparing(x -> getInterestingScore(personMetricsMap.get(x.getId())), Comparator.reverseOrder()));
        Stream<Person> soundsLikeMatchesSortedByInteresting = personSearchResult.soundsLikeMatches().stream()
                .sorted(Comparator.comparing(x -> getInterestingScore(personMetricsMap.get(x.getId())), Comparator.reverseOrder()));
        List<Person> persons = Stream.concat(exactMatchesSortedByInteresting, soundsLikeMatchesSortedByInteresting).toList();

        // if the list of persons is empty, it either means there was no search or no persons were found by the search
        if (persons.isEmpty()) {
            if (searchQuery != null && ! searchQuery.isBlank()) {
                // if there are no persons found, show a suitable message
                return "<li id=\"no_persons_found_alert\">No persons found</li>";
            } else {
                // if there was no search, show a random selection of persons

                // this section is a failsafe - if the GetOlderLoop has not finished its processing, there
                // will not yet be a filled cache of most interesting people yet.  In that case, use this
                // code to get it the slow way.
                if (memoriaContext.getCachedData().getMostInterestingPeopleNoLiving() == null ||
                        memoriaContext.getCachedData().getMostInterestingPeopleIncludingLiving() == null) {
                    var sb = new StringBuilder();
                    List<Person> randomPersons = personSearch.getInterestingPeople(10, shouldShowPrivateInformation);
                    for (Person p : randomPersons) {
                        String renderedTemplate = homepagePersonRenderer.renderPersonTemplate(p, optionalOtherPersonId);
                        sb.append(renderedTemplate);
                    }
                    return sb.toString();
                }

                return selectRandomPeopleForHomepage(memoriaContext, shouldShowPrivateInformation, optionalOtherPersonId, homepagePersonRenderer);
            }
        } else {
            // if there are one or more persons, render their templates
            var sb = new StringBuilder();
            for (Person p : persons) {
                String renderedTemplate = homepagePersonRenderer.renderPersonTemplate(p, optionalOtherPersonId);
                sb.append(renderedTemplate);
            }
            return sb.toString();
        }
    }

    static String selectRandomPeopleForHomepage(MemoriaContext memoriaContext,
                                                boolean shouldShowPrivateInformation,
                                                UUID optionalOtherPersonId,
                                                HomepagePersonRenderer homepagePersonRenderer) {
        var sb = new StringBuilder();
        List<Person> mostInterestingPeople;
        if (shouldShowPrivateInformation) {
            mostInterestingPeople = memoriaContext.getCachedData().getMostInterestingPeopleIncludingLiving().stream().toList();
        } else {
            mostInterestingPeople = memoriaContext.getCachedData().getMostInterestingPeopleNoLiving().stream().toList();
        }
        List<Person> shuffledInterestingPeople = grabXPersons(10, mostInterestingPeople);
        for (Person p : shuffledInterestingPeople) {
            String renderedTemplate = homepagePersonRenderer.renderPersonTemplate(p, optionalOtherPersonId);
            sb.append(renderedTemplate);
        }
        return sb.toString();
    }

    /**
     * grab a certain number of persons randomly from a list.  More performant
     * than using Collections.shuffle.
     * @param i number of persons
     * @param persons a list of persons from which to grab
     */
    private static List<Person> grabXPersons(int i, List<Person> persons) {
        return randomSample(persons, i);
    }

    /**
     * Handles the search requests from the search field on the homepage
     */
    public IResponse searchPersonGet(IRequest request) {
        String otherPersonId = request.getRequestLine().queryString().get("oid");

        UUID optionalOtherPersonId;
        if (otherPersonId != null && ! otherPersonId.isBlank()) {
            var deserializedOtherPersonFile = personLruCache.getCachedPersonFile(otherPersonId);
            if (deserializedOtherPersonFile.equals(PersonFile.EMPTY)) {
                logger.logDebug(() -> "While looking at regular person details, a user at ip address ("+ request.getRemoteRequester() +
                        ") provided an id for 'other' person that does not match anyone. ID: " + otherPersonId);
                return Respond.userInputError();
            }
            optionalOtherPersonId = deserializedOtherPersonFile.getId();
        } else {
            optionalOtherPersonId = null;
        }

        // if this person is alive, we'll redact their info. unless the user is authenticated
        PrivacyCheckStatus privacyCheckStatus = auth.canShowPrivateInformation(request);

        String query = request.getRequestLine().queryString().get("query");
        PersonSearchResult personSearchResult = personSearch.getPeople(query, privacyCheckStatus.canShowPrivateInformation(), 20);
        Stream<Person> exactMatchesSortedByInteresting = personSearchResult.exactMatches().stream()
                .sorted(Comparator.comparing(x -> getInterestingScore(personMetricsMap.get(x.getId())), Comparator.reverseOrder()));
        Stream<Person> soundsLikeMatchesSortedByInteresting = personSearchResult.soundsLikeMatches().stream()
                .sorted(Comparator.comparing(x -> getInterestingScore(personMetricsMap.get(x.getId())), Comparator.reverseOrder()));
        Stream<Person> persons = Stream.concat(exactMatchesSortedByInteresting, soundsLikeMatchesSortedByInteresting);

        List<Map<String, String>> data = persons.map(p -> homepagePersonRenderer.calculateTemplateDataForPersons(p, optionalOtherPersonId)).toList();

        if (data.isEmpty()) {
            return Response.buildLeanResponse(CODE_204_NO_CONTENT);
        } else {
            return Respond.htmlOk(homepagePersonRenderer.renderPersonTemplate(data));
        }

    }

    public IResponse headerSearchGet(IRequest request) {
        String personName = request.getRequestLine().queryString().get("personname");

        UUID personIdUuid = new UUID(0, 0);
        String oidQueryString = "";
        try {
            // make sure the input is valid.  If we got identifiers, convert to UUID or bail

            String personId = request.getRequestLine().queryString().get("id");
            if (personId != null && !personId.isBlank()) {
                personIdUuid = UUID.fromString(personId);
            }


            String otherPersonId = request.getRequestLine().queryString().get("oid");
            if (otherPersonId != null && !otherPersonId.isBlank()) {
                UUID otherPersonUuid = UUID.fromString(otherPersonId);
                oidQueryString = "&oid=" + otherPersonUuid;
            }
        } catch (IllegalArgumentException ex) {
            logger.logDebug(() -> "Error at headerSearchGet: " + ex);
            return Respond.userInputError();
        }

        // if there is no person id provided, redirect to the homepage with search running for a name
        if (personIdUuid.equals(new UUID(0,0))) {
            return Respond.redirectTo("/persons?search=" + StringUtils.encode(personName) + oidQueryString);
        } else {
            return Respond.redirectTo("/person?id=" + personIdUuid + oidQueryString);
        }
    }

    /**
     * If you are logged in and want to edit a person, this is the
     * list of persons
     * @param justReturnInnerList if true, don't render a whole html page but only
     *                            the inner list with pagination, which is used
     *                            when paging with JavaScript.
     */
    IResponse editListGet(IRequest r, boolean justReturnInnerList) {
        AuthResult authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return auth.htmlForbidden();
        }

        // get the search string
        String lowercaseSearch = Objects.requireNonNullElse(r.getRequestLine().queryString().get("search"), "").toLowerCase();
        // get the sort string
        String sort = Objects.requireNonNullElse(r.getRequestLine().queryString().get("sort"), "").toLowerCase();
        // get the filter string
        String filter = Objects.requireNonNullElse(r.getRequestLine().queryString().get("filter"), "").toLowerCase();

        // get the page we're on, or default to page 1 (1 is the first page).
        int page;
        String pageString = Objects.requireNonNullElse(r.getRequestLine().queryString().get("page"), "1");
        try {
            page = Integer.parseInt(pageString);
        } catch (NumberFormatException ex) {
            logger.logDebug(() -> "Bad input for page number ("+pageString+") at editListGet. Exception: " + ex.getMessage());
            return Respond.userInputError();
        }
        String navHeader = navigationHeader.renderNavigationHeader(r, true, true, "");

        EnhancedPersonList.SortResult sortResult = enhancedPersonList.determineSorting(sort);
        EnhancedPersonList.FilterResult filterResult = enhancedPersonList.determineFilter(filter);

        if (justReturnInnerList) {
            return Respond.htmlOk(enhancedPersonList.renderInnerPaginatedList(lowercaseSearch, page, sortResult, sort, filterResult, filter));
        } else {
            Map<String, String> listItems = enhancedPersonList.renderListOfPersons(lowercaseSearch, filterResult, sort, filter, sortResult, page, navHeader);
            return Respond.htmlOk(personEditListPageTemplateProcessor.renderTemplate(listItems));
        }

    }

    /**
     * View details of a person, meant for printing (on paper)
     */
    public IResponse viewPersonPrintGet(IRequest r) {
        PrivacyCheckStatus privacyCheckStatus = auth.canShowPrivateInformation(r);
        if (! privacyCheckStatus.canShowPrivateInformation()) {
            return auth.htmlForbidden();
        }

        // this is the identifier of the person we're looking at
        String id = r.getRequestLine().queryString().get("id");

        if (id == null || id.isBlank()) return Response.buildLeanResponse(CODE_400_BAD_REQUEST);

        PersonFile deserializedPersonFile;
        try {
            deserializedPersonFile = personLruCache.getCachedPersonFile(id);
        } catch (WebServerException ex) {
            logger.logAsyncError(() -> "Error while viewing a person: " + StacktraceUtils.stackTraceToString(ex));
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        } catch (Exception ex) {
            logger.logAsyncError(() -> "Error while viewing a person: " + StacktraceUtils.stackTraceToString(ex));
            return Response.buildLeanResponse(CODE_500_INTERNAL_SERVER_ERROR);
        }

        if (deserializedPersonFile.equals(PersonFile.EMPTY)) {
            return Response.buildLeanResponse(CODE_404_NOT_FOUND);
        }

        String renderedTemplate = detailedViewRenderer.renderPersonViewForPrint(deserializedPersonFile);
        return Respond.htmlOk(renderedTemplate);
    }

    /**
     * View the details of a particular person
     */
    public IResponse viewPersonGet(IRequest r) {

        // this is the identifier of the person we're looking at
        String id = r.getRequestLine().queryString().get("id");

        // this is the identifier of the relation of this person
        String otherPersonId = r.getRequestLine().queryString().get("oid");

        if (id == null || id.isBlank()) return Response.buildLeanResponse(CODE_400_BAD_REQUEST);

        PersonFile deserializedPersonFile;
        UUID optionalOtherPersonId = null;
        try {
            deserializedPersonFile = personLruCache.getCachedPersonFile(id);
            if (otherPersonId != null) {
                var deserializedOtherPersonFile = personLruCache.getCachedPersonFile(otherPersonId);
                if (deserializedOtherPersonFile.equals(PersonFile.EMPTY)) {
                    logger.logDebug(() -> "While looking at regular person details, a user at ip address (" + r.getRemoteRequester() +
                            ") provided an id for 'other' person that does not match anyone. ID: " + otherPersonId);
                    return Respond.userInputError();
                }
                optionalOtherPersonId = deserializedOtherPersonFile.getId();
            }
        } catch (WebServerException ex) {
            logger.logAsyncError(() -> "Error while viewing a person: " + StacktraceUtils.stackTraceToString(ex));
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        } catch (Exception ex) {
            logger.logAsyncError(() -> "Error while viewing a person: " + StacktraceUtils.stackTraceToString(ex));
            return Response.buildLeanResponse(CODE_500_INTERNAL_SERVER_ERROR);
        }

        if (deserializedPersonFile.equals(PersonFile.EMPTY)) {
            return Response.buildLeanResponse(CODE_404_NOT_FOUND);
        }

        // if this person is alive, we'll redact their info. unless the user is authenticated
        PrivacyCheckStatus privacyCheckStatus = auth.canShowPrivateInformation(r);

        String navHeader = this.navigationHeader.renderNavigationHeader(
                r,
                privacyCheckStatus.isAdminAuthenticated(),
                privacyCheckStatus.isPrivacyAuthenticated(),
                id,
                true,
                optionalOtherPersonId);
        String renderedTemplate = detailedViewRenderer.renderPersonView(
                privacyCheckStatus.isAdminAuthenticated(),
                deserializedPersonFile,
                navHeader,
                optionalOtherPersonId,
                privacyCheckStatus.canShowPrivateInformation()
        );
        return Respond.htmlOk(renderedTemplate);
    }

    public IResponse viewPersonGetAllRelatives(IRequest r) {

        // if this person is alive, we'll redact their info. unless the user is authenticated
        PrivacyCheckStatus privacyCheckStatus = auth.canShowPrivateInformation(r);

        // this is the identifier of the person we're looking at
        String id = r.getRequestLine().queryString().get("id");

        // this is the identifier of the relation of this person
        String otherPersonId = r.getRequestLine().queryString().get("oid");

        if (id == null || id.isBlank()) return Response.buildLeanResponse(CODE_400_BAD_REQUEST);

        PersonFile deserializedPersonFile;
        UUID optionalOtherPersonId = null;
        try {
            deserializedPersonFile = personLruCache.getCachedPersonFile(id);

            // prevent users from seeing this page entirely if the person is alive and the user isn't privileged to see them.
            if (oip.shouldObscureInformation(deserializedPersonFile.getBorn(), deserializedPersonFile.getDied(), privacyCheckStatus.canShowPrivateInformation())) {
                return Respond.redirectTo("/person?id=" + id);
            }
            if (otherPersonId != null) {
                var deserializedOtherPersonFile = personLruCache.getCachedPersonFile(otherPersonId);
                if (deserializedOtherPersonFile.equals(PersonFile.EMPTY)) {
                    logger.logDebug(() -> "While looking at a person's close relatives, a user at ip address (" + r.getRemoteRequester() +
                            ") provided an id for 'other' person that does not match anyone. ID: " + otherPersonId);
                    return Respond.userInputError();
                }
                optionalOtherPersonId = deserializedOtherPersonFile.getId();
            }
        } catch (WebServerException ex) {
            logger.logAsyncError(() -> "Error while viewing a person: " + StacktraceUtils.stackTraceToString(ex));
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        } catch (Exception ex) {
            logger.logAsyncError(() -> "Error while viewing a person: " + StacktraceUtils.stackTraceToString(ex));
            return Response.buildLeanResponse(CODE_500_INTERNAL_SERVER_ERROR);
        }

        if (deserializedPersonFile.equals(PersonFile.EMPTY)) {
            return Response.buildLeanResponse(CODE_404_NOT_FOUND);
        }

        String navHeader = navigationHeader.renderNavigationHeader(
                r,
                privacyCheckStatus.isAdminAuthenticated(),
                privacyCheckStatus.isPrivacyAuthenticated(),
                id,
                true,
                optionalOtherPersonId);

        String renderedTemplate = detailedViewRenderer.renderPersonViewAllRelatives(
                deserializedPersonFile,
                navHeader,
                optionalOtherPersonId,
                privacyCheckStatus.canShowPrivateInformation());
        return Respond.htmlOk(renderedTemplate);
    }

    /**
     * Search all persons by name for use in adding relations
     * to a person - this is called by some JavaScript on the
     * edit page for adding new relations like siblings, spouses, etc.
     */
    public IResponse searchRelationGet(IRequest r) {
        // if this person is alive, we'll redact their info. unless the user is authenticated
        PrivacyCheckStatus privacyCheckStatus = auth.canShowPrivateInformation(r);

        String query = r.getRequestLine().queryString().get("query");

        String renderedHtml = relationSearch.searchRelations(query, privacyCheckStatus.canShowPrivateInformation());

        if (renderedHtml.isBlank()) {
            return Response.buildLeanResponse(CODE_204_NO_CONTENT);
        } else {
            return Respond.htmlOk(renderedHtml);
        }
    }

    public IResponse descendantsPrintableGet(IRequest r) {
        // if this person is alive, we'll redact their info. unless the user is authenticated
        PrivacyCheckStatus privacyCheckStatus = auth.canShowPrivateInformation(r);

        // this is the identifier of the person we're looking at
        String id = r.getRequestLine().queryString().get("id");

        if (id == null || id.isBlank()) return Response.buildLeanResponse(CODE_400_BAD_REQUEST);

        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        }

        if (!privacyCheckStatus.canShowPrivateInformation()) {
            return Respond.redirectTo("person-all?id=" + uuid);
        }

        // get the PersonNode for this person, as a first step to calculating their relatives
        // in the family graph.
        PersonNode myPersonNode = SearchUtils.findExactlyOne(
                familyGraphBuilder.getPersonNodes().values().stream(), x -> x.getId().equals(uuid));
        if (myPersonNode == null) {
            return Respond.userInputError();
        }
        Map<PersonNode, Integer> personNodeOrdinalMap = calculateOrdinals(myPersonNode, "child", this.personDb);
        String summary = FamilyGraph.renderPosterityShort(myPersonNode, personLruCache, personNodeOrdinalMap);
        String fullLength = FamilyGraph.renderPosterityLong(personLruCache, personNodeOrdinalMap);
        String result = printableTreeTemplateProcessor.renderTemplate(
                Map.of(
                        "summary", summary,
                        "full_length", fullLength
                ));
        return Respond.htmlOk(result);
    }

    public IResponse ancestorsPrintableGet(IRequest r) {
        // if this person is alive, we'll redact their info. unless the user is authenticated
        PrivacyCheckStatus privacyCheckStatus = auth.canShowPrivateInformation(r);

        // this is the identifier of the person we're looking at
        String id = r.getRequestLine().queryString().get("id");

        if (id == null || id.isBlank()) return Response.buildLeanResponse(CODE_400_BAD_REQUEST);

        if (!privacyCheckStatus.canShowPrivateInformation()) {
            return Respond.redirectTo("person-all?id=" + id);
        }

        // get the PersonNode for this person, as a first step to calculating their relatives
        // in the family graph.
        PersonNode myPersonNode = SearchUtils.findExactlyOne(
                familyGraphBuilder.getPersonNodes().values().stream(), x -> x.getId().toString().equals(id));
        if (myPersonNode == null) {
            return Respond.userInputError();
        }
        Map<PersonNode, Integer> personNodeOrdinalMap = calculateOrdinals(myPersonNode, "parent", this.personDb);
        String summary = FamilyGraph.renderAncestryShort(myPersonNode, personNodeOrdinalMap);
        String fullLength = FamilyGraph.renderAncestryLong(personLruCache, personNodeOrdinalMap);
        String result = printableTreeTemplateProcessor.renderTemplate(
                Map.of(
                        "summary", summary,
                        "full_length", fullLength
                ));
        return Respond.htmlOk(result);
    }

}
