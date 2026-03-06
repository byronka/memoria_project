package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.Date;
import com.renomad.inmra.featurelogic.persons.Month;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.minum.database.AbstractDb;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * A class for deriving the birth and death days of
 * all the persons in the database, for display on
 * the homepage.
 */
public class BirthDeathDays {

    private final AbstractDb<Person> personDb;
    private final LocalDate now;

    public BirthDeathDays(AbstractDb<Person> personDb, LocalDate now) {
        this.personDb = personDb;
        this.now = now;
    }

    public String addRecentBirthDeathDays(boolean includeLiving) {
        var persons = personDb.values();
        LocalDate now = this.now == null ? LocalDate.now() : this.now;
        return renderAnniversaryString(persons, now, includeLiving);
    }

    /**
     * add persons with birthdates or deathdates within 3 days on either side.
     * @param persons all the persons
     * @param now the current date-time
     */
    public static String renderAnniversaryString(Collection<Person> persons, LocalDate now, boolean includeLiving) {
        StringBuilder sb = new StringBuilder();
        List<Person> recentBirthdays = persons.stream()
                .filter(
                    x -> x.getBirthday().toLocalDate().isPresent() &&
                        ! x.getBirthday().month().equals(Month.NONE) &&
                        (includeLiving ? true : ! x.getDeathday().equals(Date.EMPTY)) &&  // don't show living people unless allowed
                        Math.abs(ChronoUnit.DAYS.between(now, x.getBirthday().toLocalDate().get().withYear(now.getYear()))) <= 3 )
                .sorted(Comparator.comparing(y -> y.getBirthday().toLocalDate().orElseThrow().withYear(now.getYear())))
                .toList();
        List<Person> recentDeathDays = persons.stream()
                .filter(
                    x -> x.getDeathday().toLocalDate().isPresent() &&
                        ! x.getDeathday().month().equals(Month.NONE) &&
                        Math.abs(ChronoUnit.DAYS.between(now, x.getDeathday().toLocalDate().get().withYear(now.getYear()))) <= 3 )
                .sorted(Comparator.comparing(y -> y.getDeathday().toLocalDate().orElseThrow().withYear(now.getYear())))
                .toList();

        if (!recentBirthdays.isEmpty()) {
            sb.append("<h3>Recent birthdays:</h3>");
            for (var person : recentBirthdays) {
                sb.append(String.format("<p><a href=\"person?id=%s\" class=\"%s\">%s</a> was born on %s</p>",
                        person.getId().toString(), person.getDeathday().equals(Date.EMPTY) ? "living" : "deceased", person.getName(), person.getBirthday().getPrettyString()));
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
