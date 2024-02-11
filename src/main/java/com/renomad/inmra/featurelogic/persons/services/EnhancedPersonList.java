package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.IPersonLruCache;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.featurelogic.photo.PhotoToPerson;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.renomad.minum.utils.StringUtils.safeAttr;
import static com.renomad.minum.utils.StringUtils.safeHtml;

/**
 * This class is responsible for searching and rendering information
 * about persons for the "list all persons" page, which shows buttons
 * to view, edit, view photos, etc.
 */
public class EnhancedPersonList {

    private final Db<Person> personDb;
    private final IPersonLruCache personLruCache;
    private final Db<PhotoToPerson> photoToPersonDb;
    private final TemplateProcessor personEditListItemTemplateProcessor;
    private final Lifespan lifespan;

    public EnhancedPersonList(
            ILogger logger,
            IFileUtils fileUtils,
            Db<Person> personDb,
            IPersonLruCache personLruCache,
            Db<PhotoToPerson> photoToPersonDb
            ) {
        this.personDb = personDb;
        this.personLruCache = personLruCache;
        this.photoToPersonDb = photoToPersonDb;
        personEditListItemTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/person_edit_list_item.html"));
        this.lifespan = new Lifespan(logger);
    }



    private record SortResult(Comparator<Person> personComparator, String currentSortValue) {
    }


    public Map<String, String> renderListOfPersons(String lowercaseSearch, String sort, int page, String authHeaderRendered, String id) {
        // here is where we will store the results of the persons we find.
        var sb = new StringBuilder();
        SortResult sortResult;

        if (! id.isBlank()) {
            sortResult = new SortResult(Comparator.comparing(Person::getIndex), "None");
            Optional<Person> first = personDb.values().stream()
                    .filter(x -> x.getId().toString().equals(id))
                    .findFirst();
            if (first.isPresent()) {
                sb.append(renderEditPersonItem(first.get()));
            }
        } else {
            // get all the persons
            Collection<Person> allPersons = personDb.values();

            // the persons we will iterate over
            Collection<Person> persons;

            // if we have a search string, filter the results case-insensitively,
            // otherwise just use the whole collection.
            if (!lowercaseSearch.isBlank()) {
                persons = allPersons.stream().filter(x -> x.getName().toLowerCase().contains(lowercaseSearch)).toList();
            } else {
                persons = allPersons;
            }

            sortResult = determineSorting(sort);

            // the maximum count of persons we'll show per page.
            int maxPersonsCount = 10;

            // this is where we apply sorting and paging to the list of persons
            persons.stream()
                    .sorted(sortResult.personComparator())
                    .skip((long) maxPersonsCount * (page - 1))
                    .limit(maxPersonsCount)
                    .forEach(x -> sb.append(renderEditPersonItem(x)));
        }

        return Map.of(
                "header", authHeaderRendered,
                "list_items", sb.toString(),
                "current_sort", StringUtils.safeHtml(sortResult.currentSortValue()),
                "current_sort_code", StringUtils.safeAttr(sort),
                "current_search", lowercaseSearch.isBlank() ? "(None)" : StringUtils.safeHtml(lowercaseSearch),
                "current_search_raw", StringUtils.safeAttr(lowercaseSearch),
                "previous_page", page == 1 ? String.valueOf(page) : String.valueOf(page - 1),
                "next_page", String.valueOf(page + 1)
        );
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

        Map<String, String> myMap = new HashMap<>();
        myMap.put("id", deserializedPersonFile.getId().toString());
        myMap.put("name", safeHtml(deserializedPersonFile.getName()));
        myMap.put("name_attr", safeAttr(deserializedPersonFile.getName()));
        myMap.put("photo_count", String.valueOf(getPhotoIdsForPerson(p).size()));
        myMap.put("last_modified",deserializedPersonFile.getLastModified().truncatedTo(ChronoUnit.SECONDS).toString());
        myMap.put("lifespan", lifespan.renderLifespan(deserializedPersonFile));

        if (deserializedPersonFile.getImageUrl().isBlank()) {
            myMap.put("person_image", "");
        } else {
            myMap.put("person_image", String.format(
                    """
                    <img
                        height=110
                        width=100
                        class="person_image"
                        src="%s&amp;size=small"
                        alt="">
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
    private SortResult determineSorting(String sort) {
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
            default -> {
                personComparator = Comparator.comparing(Person::getIndex);
                currentSortValue = "None";
            }
        }
        return new SortResult(personComparator, currentSortValue);
    }

}
