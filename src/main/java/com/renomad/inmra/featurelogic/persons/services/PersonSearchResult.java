package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.Person;

import java.util.List;

/**
 * A container for searches - separate the results into those which have perfect matches
 * versus sounds-like matches.  For example, if we search for "laine", we should get "elaine"
 * in the exactMatches, and maybe "alan" in the soundsLikeMatches
 */
public record PersonSearchResult(List<Person> exactMatches, List<Person> soundsLikeMatches) {
}
