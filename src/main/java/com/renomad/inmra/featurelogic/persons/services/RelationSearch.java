package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.Date;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.minum.database.Db;
import com.renomad.minum.utils.StringUtils;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RelationSearch {

    private final Db<Person> personDb;

    public RelationSearch(Db<Person> personDb) {
        this.personDb = personDb;
    }

    public String searchRelations(String query) {
        Stream<Person> foundPersons = personDb
                .values().stream()
                .filter(
                        x -> x.getName().toLowerCase(Locale.ROOT)
                                .contains(query.toLowerCase(Locale.ROOT))
                )
                .limit(5);

        return foundPersons
                .map(x -> {
                    String birthdayString = "(unknown birthdate)";
                    if (! x.getBirthday().equals(com.renomad.inmra.featurelogic.persons.Date.EMPTY) && ! x.getBirthday().equals(Date.EXISTS_BUT_UNKNOWN)) {
                        birthdayString = "born " + x.getBirthday().getPrettyString();
                    }
                    return String.format("<li><span data-personid=\"%s\" data-personname=\"%s\" >%s %s</span></li>",
                            x.getId().toString(),
                            StringUtils.safeAttr(x.getName()),
                            StringUtils.safeHtml(x.getName()),
                            birthdayString);
                })
                .collect(Collectors.joining("\n"));
    }

}
