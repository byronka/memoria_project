package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.IPersonLruCache;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.minum.database.Db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * This class is responsible for providing a list of {@link Person}
 * depending on search criteria
 */
public class PersonSearch {

    private final Db<Person> personDb;
    private final IPersonLruCache personLruCache;

    public PersonSearch(Db<Person> personDb, IPersonLruCache personLruCache) {

        this.personDb = personDb;
        this.personLruCache = personLruCache;
    }


    /**
     * Given a search query string, we'll grab some {@link Person} data
     */
    public List<Person> getPeople(String searchQuery) {
        // maximum persons we will show
        int maxPersonCount = 20;

        if (searchQuery != null && ! searchQuery.isBlank()) {
            return personDb
                    .values().stream()
                    .filter(person -> ! person.getDeathday().equals(com.renomad.inmra.featurelogic.persons.Date.EMPTY) &&
                            ! personLruCache.getCachedPersonFile(person).getImageUrl().isBlank() &&
                            person.getName().toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT)))
                    .limit(maxPersonCount)
                    .toList();
        } else {
            return new ArrayList<>();
        }
    }


    /**
     * Grab a random group of people to show on the homepage
     * @param numberOfPeople the number of people we want to show
     */
    public List<Person> getRandomPeople(int numberOfPeople) {
        List<Person> personsToShow = personDb
                .values().stream()
                .filter(person -> !person.getDeathday().equals(com.renomad.inmra.featurelogic.persons.Date.EMPTY) &&
                        !personLruCache.getCachedPersonFile(person).getImageUrl().isBlank())
                .collect(Collectors.toList());

        Collections.shuffle(personsToShow);
        return personsToShow.stream().limit(numberOfPeople).toList();
    }
}
