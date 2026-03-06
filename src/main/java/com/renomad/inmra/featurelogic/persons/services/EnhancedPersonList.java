package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.*;
import com.renomad.inmra.featurelogic.persons.Date;
import com.renomad.inmra.featurelogic.photo.PhotoToPerson;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.renomad.inmra.featurelogic.persons.services.InterestingScore.getInterestingScore;
import static com.renomad.minum.utils.StringUtils.safeHtml;

/**
 * This class is responsible for searching and rendering information
 * about persons for the "list all persons" page, which shows buttons
 * to view, edit, view photos, etc.
 */
public class EnhancedPersonList {

    private final AbstractDb<Person> personDb;
    private final IPersonLruCache personLruCache;
    private final AbstractDb<PhotoToPerson> photoToPersonDb;
    private final TemplateProcessor personEditListItemTemplateProcessor;
    private final Map<UUID, PersonMetrics> personMetricsMap;
    private final Stats stats;
    private final Lifespan lifespan;
    private final PersonSearch personSearch;

    public EnhancedPersonList(
            ILogger logger,
            IFileUtils fileUtils,
            AbstractDb<Person> personDb,
            IPersonLruCache personLruCache,
            AbstractDb<PhotoToPerson> photoToPersonDb,
            Map<UUID, PersonMetrics> personMetricsMap,
            Stats stats) {
        this.personDb = personDb;
        this.personLruCache = personLruCache;
        this.photoToPersonDb = photoToPersonDb;
        personEditListItemTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/person_edit_list_item.html"));
        this.lifespan = new Lifespan(logger);
        this.personMetricsMap = personMetricsMap;
        this.stats = stats;
        this.personSearch = new PersonSearch(personDb, personLruCache, personMetricsMap);
    }

    /**
     * Depending on which code we receive, we will provide different logical
     * predicates.  These are applied to a list of Persons, and help to filter
     * down the data for the benefit of administrators.
     */
    public FilterResult determineFilter(String filter) {
        switch (filter) {
            case "eap" -> {
                Predicate<Person> filterFunction = (Person person) -> this.personMetricsMap.get(person.getId()).getAgeYears() > 105;
                return new FilterResult("Extremely aged", filterFunction);
            }
            case "lp" -> {
                Predicate<Person> filterFunction = (Person person) ->
                        ObscureInformationProcessor.isLiving(
                                this.personMetricsMap.get(person.getId()).getBirthdate(),
                                this.personMetricsMap.get(person.getId()).getDeathdate(),
                                LocalDate.now());
                return new FilterResult("Living people", filterFunction);
            }
            case "lpkb" -> {
                Predicate<Person> filterFunction = (Person person) ->
                        ObscureInformationProcessor.isLiving(
                                this.personMetricsMap.get(person.getId()).getBirthdate(),
                                this.personMetricsMap.get(person.getId()).getDeathdate(),
                                LocalDate.now()) && ! this.personMetricsMap.get(person.getId()).getBirthdate().equals(Date.EXISTS_BUT_UNKNOWN);
                return new FilterResult("Living people with known birthdate", filterFunction);
            }
            case "dp" -> {
                Predicate<Person> filterFunction = (Person person) ->
                                ! ObscureInformationProcessor.isLiving(
                                this.personMetricsMap.get(person.getId()).getBirthdate(),
                                this.personMetricsMap.get(person.getId()).getDeathdate(),
                                LocalDate.now());
                return new FilterResult("Deceased people", filterFunction);
            }
            case "dpkd" -> {
                Predicate<Person> filterFunction = (Person person) ->
                                ! ObscureInformationProcessor.isLiving(
                                this.personMetricsMap.get(person.getId()).getBirthdate(),
                                this.personMetricsMap.get(person.getId()).getDeathdate(),
                                LocalDate.now()) &&
                                        ! this.personMetricsMap.get(person.getId()).getBirthdate().equals(Date.EXISTS_BUT_UNKNOWN) &&
                                        ! this.personMetricsMap.get(person.getId()).getDeathdate().equals(Date.EXISTS_BUT_UNKNOWN) ;
                return new FilterResult("Deceased people with known dates", filterFunction);
            }
            case "na" -> {
                Predicate<Person> filterFunction = (Person person) -> this.personMetricsMap.get(person.getId()).getCountAncestors() == 0;
                return new FilterResult("No ancestors", filterFunction);
            }
            case "nd" -> {
                Predicate<Person> filterFunction = (Person person) -> this.personMetricsMap.get(person.getId()).getCountDescendants() == 0;
                return new FilterResult("No descendants", filterFunction);
            }
            default -> {
                return new FilterResult("", (Person person) -> true);
            }
        }
    }


    public record SortResult(Comparator<Person> personComparator, String currentSortValue) {
    }

    public record FilterResult(String currentFilterValue, Predicate<Person> filter) {

    }


    public Map<String, String> renderListOfPersons(String lowercaseSearch, FilterResult filter, String currentSortCode, String currentFilterCode, SortResult sortResult, int page, String navigationHeader) {

        String listResultSectionRendered = renderInnerPaginatedList(lowercaseSearch, page, sortResult, currentSortCode, filter, currentFilterCode);

        Map<String, String> keyValueMap = new HashMap<>();
        keyValueMap.put("navigation_header", navigationHeader);
        keyValueMap.put("current_sort", StringUtils.safeHtml(sortResult.currentSortValue()));
        keyValueMap.put("current_sort_for_attr", StringUtils.safeAttr(currentSortCode));
        keyValueMap.put("current_search", lowercaseSearch.isBlank() ? "(None)" : StringUtils.safeHtml(lowercaseSearch));
        keyValueMap.put("current_search_for_attr", StringUtils.safeAttr(lowercaseSearch));
        keyValueMap.put("current_filter", filter.currentFilterValue().isBlank() ? "(None)" : StringUtils.safeHtml(filter.currentFilterValue()));
        keyValueMap.put("current_filter_for_attr", StringUtils.safeAttr(currentFilterCode));
        keyValueMap.put("list_result_section", listResultSectionRendered);
        return keyValueMap;
    }

    /**
     * @param lowercaseSearch the search string, converted to lowercase, since this is a
     *                        case-insensitive search
     * @param page            The page of results to return
     * @param sortResult      A container providing us the code for how we sort our data
     * @param currentSortCode the code for searching, we return to show
     * @param currentFilterCode the code for a filter.  See {@link EnhancedPersonList#determineFilter(String)}
     */
    public String renderInnerPaginatedList(String lowercaseSearch, int page, SortResult sortResult, String currentSortCode, FilterResult filterResult, String currentFilterCode) {
        // the count of all persons found by the current search
        long totalCountPersons = -1;

        // the count of persons shown on the current page
        int pageCountPersons = -1;

        // the maximum count of persons we'll show per page.
        final int maxPersonsPerPage = 10;

        var sb = new StringBuilder();

        // get all the persons
        Collection<Person> allPersons = personDb.values();

        // the persons we will iterate over
        Collection<Person> persons;

        // if we have a search string, filter the results case-insensitively,
        // otherwise just use the whole collection.
        if (!lowercaseSearch.isBlank()) {
            PersonSearchResult result = personSearch.getPeople(lowercaseSearch, true, Integer.MAX_VALUE);
            persons = Stream.concat(result.exactMatches().stream(), result.soundsLikeMatches().stream())
                    .filter(filterResult.filter())  // here we apply a filter, if the user provided it.
                    .toList();
        } else {
            persons = allPersons
                    .stream()
                    .filter(filterResult.filter()) // here we apply a filter, if the user provided it.
                    .toList();
        }

        totalCountPersons = persons.size();

        // this is where we apply sorting and paging to the list of persons
        var listOfPersons = persons.stream()
                .sorted(sortResult.personComparator())
                .skip((long) maxPersonsPerPage * (page - 1))
                .limit(maxPersonsPerPage).toList();

        pageCountPersons = listOfPersons.size();

        for(Person person : listOfPersons) {
            sb.append(renderEditPersonItem(person));
        }

        String items = sb.toString();

        int previousPage = page == 1 ? page : page - 1;
        int nextPage = page + 1;

        // when showing a range of persons, like "persons 11-20", this would be the 11
        int minimumOrdinalPerson = ((page - 1) * maxPersonsPerPage) + 1;

        // when showing a range of persons, like "persons 11-20", this would be the 20
        int maximumOrdinalPerson = ((page - 1) * maxPersonsPerPage) + pageCountPersons;
        boolean morePersonsOnNextPage = maximumOrdinalPerson < totalCountPersons;
        long lastPageOfResults = Math.round(Math.ceil((double) totalCountPersons / maxPersonsPerPage));

        String locationInPaging = String.format("""
                <span class="page-count">
                Page %d of %d, persons %d - %d
                </span>
                """, page, lastPageOfResults, minimumOrdinalPerson, maximumOrdinalPerson);


        String cleanedCurrentSearch = StringUtils.safeAttr(lowercaseSearch);
        String cleanedCurrentSortCode = StringUtils.safeAttr(currentSortCode);
        String cleanedCurrentFilterCode = StringUtils.safeAttr(currentFilterCode);

        String firstPageButtonDisabled = """
                <span class="disabled_paging_button_span first-page">
                    <span class="disabled_paging_button">⏴⏴</span>
                </span>
                """;

        String backButtonDisabled = """
                <span class="disabled_paging_button_span back">
                    <span class="disabled_paging_button">⏴</span>
                </span>
                """;

        String firstPageButton = String.format("""
                <span class="first_page_action paging_action">
                    <form style="display: inline;">
                        <input type="hidden" name="page" value="1">
                        <input type="hidden" name="search" value="%s" />
                        <input type="hidden" name="sort" value="%s" />
                        <input type="hidden" name="filter" value="%s" />
                        <button class="first_page_action_button">⏴⏴</button>
                    </form>
                </span>
                """, cleanedCurrentSearch, cleanedCurrentSortCode, cleanedCurrentFilterCode);

        String backButton = String.format("""
                <span class="back_action paging_action">
                    <form style="display: inline;">
                        <input type="hidden" name="page" value="%d">
                        <input type="hidden" name="search" value="%s" />
                        <input type="hidden" name="sort" value="%s" />
                        <input type="hidden" name="filter" value="%s" />
                        <button class="back_action_button">⏴</button>
                    </form>
                </span>
                """, previousPage, cleanedCurrentSearch, cleanedCurrentSortCode, cleanedCurrentFilterCode);

        String forwardButton = String.format("""
                <span class="forward_action paging_action">
                    <form style="display: inline;">
                        <input type="hidden" name="page" value="%d">
                        <input type="hidden" name="search" value="%s" />
                        <input type="hidden" name="sort" value="%s" />
                        <input type="hidden" name="filter" value="%s" />
                        <button class="forward_action_button">⏵</button>
                    </form>
                </span>
                """, nextPage, cleanedCurrentSearch, cleanedCurrentSortCode, cleanedCurrentFilterCode);

        String lastPageButton = String.format("""
                <span class="last_page_action paging_action">
                    <form style="display: inline;">
                        <input type="hidden" name="page" value="%d">
                        <input type="hidden" name="search" value="%s" />
                        <input type="hidden" name="sort" value="%s" />
                        <input type="hidden" name="filter" value="%s" />
                        <button class="last_page_action_button">⏵⏵</button>
                    </form>
                </span>
                """, lastPageOfResults, cleanedCurrentSearch, cleanedCurrentSortCode, cleanedCurrentFilterCode);

        String forwardButtonDisabled = """
                <span class="disabled_paging_button_span forward">
                        <span class="disabled_paging_button">⏵</span>
                </span>
                """;

        String lastPageButtonDisabled = """
                <span class="disabled_paging_button_span last-page">
                    <span class="disabled_paging_button">⏵⏵</span>
                </span>
                """;


        String listResultSectionTemplate = """
                {{ first_page_button }}
                {{ back_button }}
                {{ page_count }}
                {{ forward_button }}
                {{ last_page_button }}
                <div id="list_person_pane">
                    {{list_items}}
                </div>
                {{ first_page_button }}
                {{ back_button }}
                {{ page_count }}
                {{ forward_button }}
                {{ last_page_button }}
                """;
        Map <String, String> listResultDataMap = new HashMap<>();
        listResultDataMap.put("list_items", items.trim().isEmpty() ? "Nothing to show" : items);
        listResultDataMap.put("first_page_button", page > 1 ? firstPageButton : firstPageButtonDisabled);
        listResultDataMap.put("back_button", page > 1 ? backButton : backButtonDisabled);
        listResultDataMap.put("forward_button", morePersonsOnNextPage ? forwardButton : forwardButtonDisabled);
        listResultDataMap.put("last_page_button", page < lastPageOfResults ? lastPageButton : lastPageButtonDisabled);
        listResultDataMap.put("page_count", locationInPaging);
        TemplateProcessor listResultSectionTemplateProcessor = TemplateProcessor.buildProcessor(listResultSectionTemplate);
        return listResultSectionTemplateProcessor.renderTemplate(listResultDataMap);
    }


    /**
     * Returns a list of identifiers for photos that are associated
     * with a person
     * @param person the {@link Person} we are inspecting for photos
     */
    public List<Long> getPhotoIdsForPerson(Person person) {
        return photoToPersonDb.values().stream()
                .filter(x -> x.getPersonIndex() == person.getIndex())
                .map(PhotoToPerson::getPhotoIndex)
                .toList();
    }

    /**
     * Given the data inside a {@link Person}, we will
     * obtain the necessary information to render an HTML template
     * for editing that person.
     */
    private String renderEditPersonItem(Person p) {
        var deserializedPersonFile = personLruCache.getCachedPersonFile(p);
        String statsRendered = stats.prepareStatsTemplate(p, deserializedPersonFile);

        Map<String, String> myMap = new HashMap<>();
        myMap.put("stats", statsRendered);
        myMap.put("id", deserializedPersonFile.getId().toString());
        myMap.put("name", safeHtml(deserializedPersonFile.getName()));
        myMap.put("last_modified", deserializedPersonFile.getLastModified().truncatedTo(ChronoUnit.SECONDS).toString());
        myMap.put("lifespan", lifespan.renderLifespan(deserializedPersonFile));
        myMap.put("photo_count", String.valueOf(getPhotoIdsForPerson(p).size()));

        if (deserializedPersonFile.getImageUrl().isBlank()) {
            myMap.put("person_image", "");
        } else {
            myMap.put("person_image", String.format(
                    """
                    <div class="image-container">
                        <img
                            class="person_image"
                            src="%s&amp;size=small"
                            alt="">
                    </div>
                    """,
                    deserializedPersonFile.getImageUrl()));
        }
        return personEditListItemTemplateProcessor.renderTemplate(myMap);
    }


    /**
     * Given the sorting code (e.g. bda = birthday ascending)
     * return the comparator we will use on the persons, and
     * the explanation for the sort we will display.
     */
    public SortResult determineSorting(String sort) {
        String currentSortValue;
        Comparator<Person> personComparator;
        switch (sort) {
            case "bda" -> {
                personComparator = Comparator.comparing(x -> x.getBirthday().toLocalDate().orElse(LocalDate.MIN));
                currentSortValue = "Birthday, ascending";
            }
            case "bdd" -> {
                personComparator = Comparator.comparing(x -> x.getBirthday().toLocalDate().orElse(LocalDate.MIN), Comparator.reverseOrder());
                currentSortValue = "Birthday, descending";
            }
            case "dda" -> {
                personComparator = Comparator.comparing(x -> x.getDeathday().toLocalDate().orElse(LocalDate.MIN));
                currentSortValue = "Deathday, ascending";
            }
            case "ddd" -> {
                personComparator = Comparator.comparing(x -> x.getDeathday().toLocalDate().orElse(LocalDate.MIN), Comparator.reverseOrder());
                currentSortValue = "Deathday, descending";
            }
            case "na" -> {
                personComparator = Comparator.comparing(Person::getName);
                currentSortValue = "Name, ascending";
            }
            case "nd" -> {
                personComparator = Comparator.comparing(Person::getName, Comparator.reverseOrder());
                currentSortValue = "Name, descending";
            }
            case "pca" -> {
                personComparator = Comparator.comparing(x -> getPhotoIdsForPerson(x).size());
                currentSortValue = "Photos count, ascending";
            }
            case "pcd" -> {
                personComparator = Comparator.comparing(x -> getPhotoIdsForPerson(x).size(), Comparator.reverseOrder());
                currentSortValue = "Photos count, descending";
            }
            case "lma" -> {
                personComparator = Comparator.comparing(x -> personLruCache.getCachedPersonFile(x.getId().toString()).getLastModified());
                currentSortValue = "Last modified, ascending";
            }
            case "lmd" -> {
                personComparator = Comparator.comparing(x -> personLruCache.getCachedPersonFile(x.getId().toString()).getLastModified(), Comparator.reverseOrder());
                currentSortValue = "Last modified, descending";
            }

            case "cbpa" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getBioImageCount());
                currentSortValue = "Count of biography photos, ascending";
            }

            case "cbpd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getBioImageCount(), Comparator.reverseOrder());
                currentSortValue = "Count of biography photos, descending";
            }

            case "rcbpapa" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getImageCount() == 0 ? 0 :
                        personMetricsMap.get(x.getId()).getBioImageCount() / (double) personMetricsMap.get(x.getId()).getImageCount());
                currentSortValue = "Ratio of biography photos to all photos, ascending";
            }

            case "rcbpapd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getImageCount() == 0 ? 0 :
                        personMetricsMap.get(x.getId()).getBioImageCount() / (double) personMetricsMap.get(x.getId()).getImageCount(), Comparator.reverseOrder());
                currentSortValue = "Ratio of biography photos to all photos, descending";
            }

            case "rcapbpa" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getBioImageCount() == 0 ? 0 :
                        personMetricsMap.get(x.getId()).getImageCount() / (double) personMetricsMap.get(x.getId()).getBioImageCount());
                currentSortValue = "Ratio of all photos to biography photos, ascending";
            }

            case "rcapbpd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getBioImageCount() == 0 ? 0 :
                        personMetricsMap.get(x.getId()).getImageCount() / (double) personMetricsMap.get(x.getId()).getBioImageCount(), Comparator.reverseOrder());
                currentSortValue = "Ratio of all photos to biography photos, descending";
            }

            case "cbva" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getBioVideoCount());
                currentSortValue = "Count of biography videos, ascending";
            }

            case "cbvd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getBioVideoCount(), Comparator.reverseOrder());
                currentSortValue = "Count of biography videos, descending";
            }

            case "rcbvava" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getVideoCount() == 0 ? 0 :
                        personMetricsMap.get(x.getId()).getBioVideoCount() / (double) personMetricsMap.get(x.getId()).getVideoCount());
                currentSortValue = "Ratio of biography videos to all videos, ascending";
            }

            case "rcbvavd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getVideoCount() == 0 ? 0 :
                        personMetricsMap.get(x.getId()).getBioVideoCount() / (double) personMetricsMap.get(x.getId()).getVideoCount(), Comparator.reverseOrder());
                currentSortValue = "Ratio of biography videos to all videos, descending";
            }

            case "rcavbva" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getBioVideoCount() == 0 ? 0 :
                         personMetricsMap.get(x.getId()).getVideoCount() / (double) personMetricsMap.get(x.getId()).getBioVideoCount());
                currentSortValue = "Ratio of all videos to biography videos, ascending";
            }

            case "rcavbvd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getBioVideoCount() == 0 ? 0 :
                        personMetricsMap.get(x.getId()).getVideoCount() / (double) personMetricsMap.get(x.getId()).getBioVideoCount(), Comparator.reverseOrder());
                currentSortValue = "Ratio of all videos to biography videos, descending";
            }

            case "vca" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getVideoCount());
                currentSortValue = "Count of videos, ascending";
            }

            case "vcd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getVideoCount(), Comparator.reverseOrder());
                currentSortValue = "Count of videos, descending";
            }

            case "ccra" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getCountCloseRelatives());
                currentSortValue = "Count of close relatives, ascending";
            }

            case "ccrd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getCountCloseRelatives(), Comparator.reverseOrder());
                currentSortValue = "Count of close relatives, descending";
            }

            case "cfca" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getCountFirstCousins());
                currentSortValue = "Count of first cousins, ascending";
            }

            case "cfcd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getCountFirstCousins(), Comparator.reverseOrder());
                currentSortValue = "Count of first cousins, descending";
            }

            case "cca" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getCousinsCount());
                currentSortValue = "Count of all cousins, ascending";
            }

            case "ccd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getCousinsCount(), Comparator.reverseOrder());
                currentSortValue = "Count of all cousins, descending";
            }

            case "rfcaca" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getCousinsCount()  == 0 ? 0 : (float) personMetricsMap.get(x.getId()).getCountFirstCousins() / personMetricsMap.get(x.getId()).getCousinsCount());
                currentSortValue = "Ratio of first cousins to all cousins, ascending";
            }

            case "rfcacd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getCousinsCount()  == 0 ? 0 : (float) personMetricsMap.get(x.getId()).getCountFirstCousins() / personMetricsMap.get(x.getId()).getCousinsCount(), Comparator.reverseOrder());
                currentSortValue = "Ratio of first cousins to all cousins, descending";
            }

            case "caa" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getCountAncestors());
                currentSortValue = "Count of ancestors, ascending";
            }

            case "cad" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getCountAncestors(), Comparator.reverseOrder());
                currentSortValue = "Count of ancestors, descending";
            }

            case "cda" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getCountDescendants());
                currentSortValue = "Count of descendants, ascending";
            }

            case "cdd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getCountDescendants(), Comparator.reverseOrder());
                currentSortValue = "Count of descendants, descending";
            }

            case "aa" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getAgeYears());
                currentSortValue = "Age, ascending";
            }

            case "ad" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getAgeYears(), Comparator.reverseOrder());
                currentSortValue = "Age, descending";
            }

            case "bsa" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getBioCharCount());
                currentSortValue = "Biography size, ascending";
            }

            case "bsd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getBioCharCount(), Comparator.reverseOrder());
                currentSortValue = "Biography size, descending";
            }

            case "rbsnsa" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getNotesCharCount() == 0 ? 0 :
                        personMetricsMap.get(x.getId()).getBioCharCount() / (double)personMetricsMap.get(x.getId()).getNotesCharCount());
                currentSortValue = "Ratio of biography size to notes size, ascending";
            }

            case "rbsnsd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getNotesCharCount() == 0 ? 0 :
                        personMetricsMap.get(x.getId()).getBioCharCount() / (double)personMetricsMap.get(x.getId()).getNotesCharCount(), Comparator.reverseOrder());
                currentSortValue = "Ratio of biography size to notes size, descending";
            }

            case "rnsbsa" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getBioCharCount() == 0 ? 0 :
                         personMetricsMap.get(x.getId()).getNotesCharCount() / (double)personMetricsMap.get(x.getId()).getBioCharCount());
                currentSortValue = "Ratio of notes size to biography size, ascending";
            }

            case "rnsbsd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getBioCharCount() == 0 ? 0 :
                        personMetricsMap.get(x.getId()).getNotesCharCount() / (double)personMetricsMap.get(x.getId()).getBioCharCount(), Comparator.reverseOrder());
                currentSortValue = "Ratio of notes size to biography size, descending";
            }

            case "inta" -> {
                personComparator = Comparator.comparing(x -> getInterestingScore(personMetricsMap.get(x.getId())));
                currentSortValue = "Interesting (combination of biography, pictures, videos...), ascending";
            }

            case "intd" -> {
                personComparator = Comparator.comparing(x -> getInterestingScore(personMetricsMap.get(x.getId())), Comparator.reverseOrder());
                currentSortValue = "Interesting (combination of biography, pictures, videos...), descending";
            }

            case "nsa" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getNotesCharCount());
                currentSortValue = "Size of notes, ascending";
            }

            case "nsd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getNotesCharCount(), Comparator.reverseOrder());
                currentSortValue = "Size of notes, descending";
            }

            case "ftsa" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getFamilyTreeSize());
                currentSortValue = "Size of family tree, ascending";
            }

            case "ftsd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getFamilyTreeSize(), Comparator.reverseOrder());
                currentSortValue = "Size of family tree, descending";
            }

            case "ssa" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getSummarySize());
                currentSortValue = "Size of summary, ascending";
            }

            case "ssd" -> {
                personComparator = Comparator.comparing(x -> personMetricsMap.get(x.getId()).getSummarySize(), Comparator.reverseOrder());
                currentSortValue = "Size of summary, descending";
            }

            default -> {
                personComparator = Comparator.comparing(Person::getIndex);
                currentSortValue = "None (sorted by index, i.e. by order of when they were added)";
            }
        }
        return new SortResult(personComparator, currentSortValue);
    }

}
