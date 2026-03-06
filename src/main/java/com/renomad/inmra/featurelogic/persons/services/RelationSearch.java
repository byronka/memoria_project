package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.Date;
import com.renomad.inmra.featurelogic.persons.IPersonLruCache;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.featurelogic.persons.PersonFile;
import com.renomad.minum.utils.StringUtils;

import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RelationSearch {

    private final IPersonLruCache personLruCache;
    private final PersonSearch personSearch;

    public RelationSearch(IPersonLruCache personLruCache, PersonSearch personSearch) {
        this.personLruCache = personLruCache;
        this.personSearch = personSearch;
    }

    /**
     * This is used in a search and replace, so we just get the strings
     */
    private static final Pattern linkPattern = Pattern.compile("<.*?>(?<personname>.*?)</a>");

    public String searchRelations(String query, boolean shouldShowPrivateInformation) {
        PersonSearchResult searchResult = personSearch.getPeople(query, shouldShowPrivateInformation,10);
        List<Person> foundPersons = Stream.concat(searchResult.exactMatches().stream(), searchResult.soundsLikeMatches().stream()).toList();

        var sb = new StringBuilder();
        for (int i = 0; i < foundPersons.size() && i < 10; i++) {
            String birthdayString = "(unknown birthdate)";
            Person person = foundPersons.get(i);

            if (! person.getBirthday().equals(com.renomad.inmra.featurelogic.persons.Date.EMPTY) &&
                    ! person.getBirthday().equals(Date.EXISTS_BUT_UNKNOWN)) {
                birthdayString = "born " + person.getBirthday().getPrettyString();
            }

            PersonFile personFile = personLruCache.getCachedPersonFile(person);

            StringJoiner parentsStringJoiner = new StringJoiner(" and ");
            parentsStringJoiner.setEmptyValue("no one");
            Matcher parentsMatcher = linkPattern.matcher(personFile.getParents());
            while (parentsMatcher.find()) {
                parentsStringJoiner.add(parentsMatcher.group("personname"));
            }

            StringJoiner childrenStringJoiner = new StringJoiner(" and ");
            childrenStringJoiner.setEmptyValue("no one");
            Matcher childrenMatcher = linkPattern.matcher(personFile.getChildren());
            while (childrenMatcher.find()) {
                childrenStringJoiner.add(childrenMatcher.group("personname"));
            }

            sb.append(String.format("<li><span data-personid=\"%s\" data-personname=\"%s\" >%s %s, child of %s, parent to %s</span></li>",
                    person.getId().toString(),
                    StringUtils.safeAttr(person.getName()),
                    StringUtils.safeHtml(person.getName()),
                    birthdayString,
                    StringUtils.safeHtml(parentsStringJoiner.toString()),
                    StringUtils.safeHtml(childrenStringJoiner.toString())
                    )).append("\n");
        }

        return sb.toString();
    }

}
