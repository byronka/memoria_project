package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.Date;
import com.renomad.inmra.featurelogic.persons.Month;
import com.renomad.inmra.featurelogic.persons.Person;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.renomad.minum.testing.TestFramework.assertEquals;

public class BirthDeathDaysTests {

    private static final UUID DEFAULT_UUID = UUID.fromString("faeb2550-4c4a-4f41-abef-d64dc89dbf20");

    /**
     * On the homepage, we display the birthdays and anniversaries of
     * death of the persons in our database.  But only if within 3 days.
     */
    @Test
    public void testRenderingAnniversaries_happyPath() {
        var personCollection = List.of(new Person(1L, DEFAULT_UUID, "alice", new Date(1922, Month.DECEMBER, 17), new Date(1922, Month.MARCH, 3) ));
        var result = BirthDeathDays.renderAnniversaryString(personCollection, LocalDate.of(2023, 12, 18));
        assertEquals(result, "<h3>Recent birthdays:</h3><p><a href=\"person?id=faeb2550-4c4a-4f41-abef-d64dc89dbf20\">alice</a> was born on December 17, 1922</p>");
    }

    /**
     * If no one has a nearby anniversary, we'll return an empty string
     */
    @Test
    public void testRenderingAnniversaries_edgeCase_NoNearbyAnniversary() {
        var personCollection = List.of(new Person(1L, DEFAULT_UUID, "alice", new Date(1922, Month.DECEMBER, 1), new Date(1922, Month.MARCH, 3) ));
        var result = BirthDeathDays.renderAnniversaryString(personCollection, LocalDate.of(2023, 12, 18));
        assertEquals(result, "");
    }

    /**
     * If there is a birthday and death day, show them both
     */
    @Test
    public void testRenderingAnniversaries_edgeCase_birthAndDeath() {
        var personCollection = List.of(
                new Person(1L, DEFAULT_UUID, "alice", new Date(1922, Month.DECEMBER, 17), new Date(1922, Month.MARCH, 3)),
                new Person(1L, DEFAULT_UUID, "bob", new Date(1922, Month.DECEMBER, 1), new Date(1922, Month.DECEMBER, 20))
        );
        var result = BirthDeathDays.renderAnniversaryString(personCollection, LocalDate.of(2023, 12, 18));
        assertEquals(result, "<h3>Recent birthdays:</h3><p><a href=\"person?id=faeb2550-4c4a-4f41-abef-d64dc89dbf20\">alice</a> was born on " +
                "December 17, 1922</p><h3>Recent anniversaries of passing:</h3><p><a href=\"person?id=faeb2550-4c4a-4f41-abef-d64dc89dbf20\">bob</a> passed " +
                "away on December 20, 1922</p>");
    }


    /**
     * If a person's birth and death dates are close to each other, we'll
     * see them show in the birthday and anniversary section.
     */
    @Test
    public void testRenderingAnniversaries_edgeCase_birthAndDeathSamePerson() {
        var personCollection = List.of(
                new Person(1L, DEFAULT_UUID, "alice", new Date(1922, Month.DECEMBER, 17), new Date(1922, Month.DECEMBER, 19))
        );
        var result = BirthDeathDays.renderAnniversaryString(personCollection, LocalDate.of(2023, 12, 18));
        assertEquals(result, "<h3>Recent birthdays:</h3><p><a href=\"person?id=faeb2550-4c4a-4f41-abef-d64dc89dbf20\">alice</a> was born " +
                "on December 17, 1922</p><h3>Recent anniversaries of passing:</h3><p><a href=\"person?id=faeb2550-4c4a-4f41-abef-d64dc89dbf20\">alice</a> passed " +
                "away on December 19, 1922</p>");
    }
}
