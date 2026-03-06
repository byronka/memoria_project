package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.IPersonLruCache;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.featurelogic.persons.PersonMetrics;
import com.renomad.inmra.utils.DaitchMokotoffSoundex;
import com.renomad.minum.database.AbstractDb;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.renomad.inmra.featurelogic.persons.services.InterestingScore.getInterestingScore;
import static com.renomad.inmra.utils.RandomnessUtils.randomSample;
import static com.renomad.minum.utils.Invariants.mustBeTrue;

/**
 * This class is responsible for providing a list of {@link Person}
 * depending on search criteria
 */
public class PersonSearch {

    private final AbstractDb<Person> personDb;
    private final IPersonLruCache personLruCache;
    private final Map<UUID, PersonMetrics> personMetricsMap;
    private final DaitchMokotoffSoundex soundex;

    public PersonSearch(AbstractDb<Person> personDb, IPersonLruCache personLruCache, Map<UUID, PersonMetrics> personMetricsMap) {

        this.personDb = personDb;
        this.personLruCache = personLruCache;
        this.personMetricsMap = personMetricsMap;
        this.soundex = new DaitchMokotoffSoundex();
    }

    /**
     * Given a search query string, we'll grab some {@link Person} data
     * @param maxPersonCount maximum number of people we will get of two kinds: exact
     *                       matches and sounds-like matches.  We'll first search for
     *                       exact matches, and if there is still room within the maxPersonCount,
     *                       we'll fill the rest with sounds-like matches.
     */
    public PersonSearchResult getPeople(String searchQuery, boolean shouldShowPrivateInformation, int maxPersonCount) {
        if (searchQuery == null || searchQuery.isBlank()) {
            return new PersonSearchResult(List.of(), List.of());
        }

        String[] splitName = searchQuery.split("\\s+", 5);
        mustBeTrue(splitName.length > 0, "if the input string is not null or blank, expect to get at least one value here");
        String firstName = splitName[0];
        // convert their query into a Daitch-Mokotoff soundex code
        String encodedQuery = soundex.encode(firstName);

        Stream<Person> exactMatchResults;
        Stream<Person> soundsLikeResults;
        if (shouldShowPrivateInformation) {
            // find the results that are an exact match to what's typed in
            exactMatchResults = personDb.values().stream()
                    .filter(person ->
                        // the searchQuery is found in their name, case-insensitively
                        person.getName().toLowerCase().contains(firstName.toLowerCase())
                    );
            // find the names that "sound like" the search, and do not have the exact search result in the name
            soundsLikeResults = personDb.values().stream()
                    .filter(person ->
                        ! person.getName().toLowerCase().contains(firstName.toLowerCase()) &&
                        Arrays.stream(person.getName().split("\\s+")).anyMatch(x -> soundex.encode(x).equals(encodedQuery))
                    );
        } else {
            exactMatchResults = personDb.values().stream()
                    .filter(person ->
                            // person is dead
                            ! person.getDeathday().equals(com.renomad.inmra.featurelogic.persons.Date.EMPTY) &&
                            // the searchQuery is found in their name, case-insensitively
                            person.getName().toLowerCase().contains(firstName.toLowerCase())
                    );
            // find the names that "sound like" the search, and do not have the exact search result in the name
            soundsLikeResults = personDb.values().stream()
                    .filter(person ->
                            // person is dead
                            ! person.getDeathday().equals(com.renomad.inmra.featurelogic.persons.Date.EMPTY) &&
                            ! person.getName().toLowerCase().contains(firstName.toLowerCase()) &&
                            Arrays.stream(person.getName().split("\\s+")).anyMatch(x -> soundex.encode(x).equals(encodedQuery))
                    );
        }

        /*
         * If the user provides multiple names, then we'll do our search primarily on the first
         * name (whatever that means - all the characters until the first whitespace) and then
         * filter *that* by each of the extra words provided, using contains and soundex.
         */
        if (splitName.length > 1) {
            Set<Person> exactMatchFiltered = exactMatchResults.collect(Collectors.toSet());
            Set<Person> soundsLikeMatchFiltered = soundsLikeResults.collect(Collectors.toSet());
            for (int i = 1; i < splitName.length; i++) {
                int finalI = i;
                exactMatchFiltered = exactMatchFiltered.stream().filter(person ->
                    // the searchQuery is found in their name, case-insensitively
                    person.getName().toLowerCase().contains(splitName[finalI].toLowerCase()) ||
                    Arrays.stream(person.getName().split("\\s+")).anyMatch(x -> soundex.encode(x).equals(soundex.encode(splitName[finalI])))
                ).collect(Collectors.toSet());

                soundsLikeMatchFiltered = soundsLikeMatchFiltered.stream().filter(person ->
                    // the searchQuery is found in their name, case-insensitively
                    person.getName().toLowerCase().contains(splitName[finalI].toLowerCase()) ||
                    Arrays.stream(person.getName().split("\\s+")).anyMatch(x -> soundex.encode(x).equals(soundex.encode(splitName[finalI])))
                ).collect(Collectors.toSet());

            }

            List<Person> exactMatches = exactMatchFiltered.stream().limit(maxPersonCount).toList();
            List<Person> soundsLikeMatches = soundsLikeMatchFiltered.stream().limit(maxPersonCount - exactMatches.size()).toList();
            return new PersonSearchResult(exactMatches, soundsLikeMatches);
        } else {
            List<Person> exactMatches = exactMatchResults.limit(maxPersonCount).toList();
            List<Person> soundsLikeMatches = soundsLikeResults.limit(maxPersonCount - exactMatches.size()).toList();
            return new PersonSearchResult(exactMatches, soundsLikeMatches);
        }

    }

    public List<Person> getAllInterestingPeople(boolean isAuthenticated) {
        return new ArrayList<>(personDb
                .values().stream()
                .filter(person -> (isAuthenticated || !person.getDeathday().equals(com.renomad.inmra.featurelogic.persons.Date.EMPTY)) &&
                        !personLruCache.getCachedPersonFile(person).getImageUrl().isBlank() &&
                        getInterestingScore(personMetricsMap.get(person.getId())) >= 10_000)
                .toList());
    }

    /**
     * Grab some interesting people to show on the homepage
     *
     * @param numberOfPeople the number of people we want to show
     */
    public List<Person> getInterestingPeople(int numberOfPeople, boolean isAuthenticated) {
        List<Person> personsToShow = getAllInterestingPeople(isAuthenticated);

        return grabXPersons(numberOfPeople, personsToShow);
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
}
