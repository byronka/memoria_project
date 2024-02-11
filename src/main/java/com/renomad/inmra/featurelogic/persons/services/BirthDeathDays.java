package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.Month;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.minum.database.Db;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

/**
 * A class for deriving the birth and death days of
 * all the persons in the database, for display on
 * the homepage.
 */
public class BirthDeathDays {

    private final Db<Person> personDb;

    public BirthDeathDays(Db<Person> personDb) {
        this.personDb = personDb;
    }

    public String addRecentBirthDeathDays() {
        var persons = personDb.values();
        LocalDate now = LocalDate.now();
        return renderAnniversaryString(persons, now);
    }

    /**
     * add persons with birthdates or deathdates within 3 days on either side.
     * @param persons all the persons
     * @param now the current date-time
     */
    static String renderAnniversaryString(Collection<Person> persons, LocalDate now) {
        StringBuilder sb = new StringBuilder();
        List<Person> recentBirthdays = persons.stream().filter(
                x ->    x.getBirthday().toLocalDate().isPresent() &&
                        ! x.getBirthday().month().equals(Month.NONE) &&
                        Math.abs(ChronoUnit.DAYS.between(now, x.getBirthday().toLocalDate().get().withYear(now.getYear()))) <= 3 ).toList();
        List<Person> recentDeathDays = persons.stream().filter(
                x ->  x.getDeathday().toLocalDate().isPresent() &&
                        ! x.getDeathday().month().equals(Month.NONE) &&
                        Math.abs(ChronoUnit.DAYS.between(now, x.getDeathday().toLocalDate().get().withYear(now.getYear()))) <= 3 ).toList();

        if (!recentBirthdays.isEmpty()) {
            sb.append("<h3>Recent birthdays:</h3>");
            for (var person : recentBirthdays) {
                sb.append(String.format("<p><a href=\"person?id=%s\">%s</a> was born on %s</p>", person.getId().toString(), person.getName(), person.getBirthday().getPrettyString()));
            }
        }
        if (!recentDeathDays.isEmpty()) {
            sb.append("<h3>Recent anniversaries of passing:</h3>");
            for (var person : recentDeathDays) {
                sb.append(String.format("<p><a href=\"person?id=%s\">%s</a> passed away on %s</p>", person.getId().toString(), person.getName(), person.getDeathday().getPrettyString()));
            }
        }
        return sb.toString();
    }

}
