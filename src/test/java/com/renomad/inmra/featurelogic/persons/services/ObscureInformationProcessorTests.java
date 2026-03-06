package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.Date;
import org.junit.Test;

import java.time.LocalDate;
import java.time.Month;

import static com.renomad.inmra.featurelogic.persons.Month.JANUARY;
import static com.renomad.inmra.featurelogic.persons.Month.JUNE;
import static com.renomad.minum.testing.TestFramework.assertFalse;
import static com.renomad.minum.testing.TestFramework.assertTrue;

public class ObscureInformationProcessorTests {

    /**
     * Ordinarily this person would be obscured, but they are just over 110 years old.
     */
    @Test
    public void testCalculation_VeryOld() {
        var oip = new ObscureInformationProcessor(LocalDate.of(2024, Month.JANUARY, 15));
        Date birthDay = new Date(1914, JANUARY, 15);
        Date deathDay = Date.EMPTY;

        boolean shouldObscure = oip.shouldObscureInformation(birthDay, deathDay, false);
        assertFalse(shouldObscure);
    }

    /**
     * Ordinarily this person would be obscured, but they are way over 110 years old.
     */
    @Test
    public void testCalculation_VeryOld2() {
        var oip = new ObscureInformationProcessor(LocalDate.of(2024, Month.JANUARY, 15));
        Date birthDay = new Date(1904, JANUARY, 15);
        Date deathDay = Date.EMPTY;

        boolean shouldObscure = oip.shouldObscureInformation(birthDay, deathDay, false);
        assertFalse(shouldObscure);
    }

    /**
     * this person should be obscured, and they are 109 years old (approximately.  Precision isn't important here)
     */
    @Test
    public void testCalculation_VeryOld3() {
        var oip = new ObscureInformationProcessor(LocalDate.of(2024, Month.JANUARY, 15));
        Date birthDay = new Date(1915, JANUARY, 15);
        Date deathDay = Date.EMPTY;

        boolean shouldObscure = oip.shouldObscureInformation(birthDay, deathDay, false);
        assertTrue(shouldObscure);
    }

    /**
     * this person should be obscured, and they are 46 years old (approximately.  Precision isn't important here)
     */
    @Test
    public void testCalculation_Ordinary() {
        var oip = new ObscureInformationProcessor(LocalDate.of(2024, Month.JANUARY, 15));
        Date birthDay = new Date(1978, JANUARY, 15);
        Date deathDay = Date.EMPTY;

        boolean shouldObscure = oip.shouldObscureInformation(birthDay, deathDay, false);
        assertTrue(shouldObscure);
    }

    /**
     * this person should be obscured, and they just died at 46 years old (approximately.  Precision isn't important here)
     */
    @Test
    public void testCalculation_YoungAndDead() {
        var oip = new ObscureInformationProcessor(LocalDate.of(2024, Month.JANUARY, 15));
        Date birthDay = new Date(1978, JANUARY, 15);
        Date deathDay = new Date(2024, JANUARY, 14);

        boolean shouldObscure = oip.shouldObscureInformation(birthDay, deathDay, false);
        assertFalse(shouldObscure);
    }

    /**
     * this person has no birthdate, so we don't know how old they are.  In that case, we
     * will obscure their information unless they have a death date.
     */
    @Test
    public void testCalculation_NoListedBirthDate() {
        var oip = new ObscureInformationProcessor(LocalDate.of(2024, Month.JANUARY, 15));
        Date birthDay = Date.EMPTY;
        Date deathDay = Date.EMPTY;

        boolean shouldObscure = oip.shouldObscureInformation(birthDay, deathDay, false);
        assertTrue(shouldObscure);
    }

    /**
     * this person has no birthdate, so we don't know how old they are.  In that case, we
     * will obscure their information unless they have a death date.
     */
    @Test
    public void testCalculation_NoListedBirthDate_2() {
        var oip = new ObscureInformationProcessor(LocalDate.of(2024, Month.JANUARY, 15));
        Date birthDay = Date.EMPTY;
        Date deathDay = new Date(2024, JANUARY, 14);

        boolean shouldObscure = oip.shouldObscureInformation(birthDay, deathDay, false);
        assertFalse(shouldObscure);
    }

    /**
     * Basic happy path - definitely still alive
     */
    @Test
    public void testIsLiving_HappyPath() {
        boolean isLiving = ObscureInformationProcessor.isLiving(new Date(1978, JANUARY, 4), Date.EMPTY, LocalDate.of(2025, 3, 16));
        assertTrue(isLiving);
    }

    /**
     * If the person has a birthdate of "unknown", and an empty death date, they will always remain living,
     * until someone fixes the values, we cannot be sure.
     */
    @Test
    public void testIsLiving_UnknownBirthDate() {
        boolean isLiving = ObscureInformationProcessor.isLiving(Date.EXISTS_BUT_UNKNOWN, Date.EMPTY, LocalDate.of(2025, 3, 16));
        assertTrue(isLiving);
    }

    /**
     * If the death date is empty but the person was born a long time ago,
     * they will get counted as dead.
     */
    @Test
    public void testIsLiving_ReallyOld() {
        boolean isLiving = ObscureInformationProcessor.isLiving(new Date(1910, JANUARY, 4), Date.EMPTY, LocalDate.of(2025, 3, 16));
        assertFalse(isLiving);
    }

    /**
     * Simple situation where someone is dead
     */
    @Test
    public void testIsLiving_isDead() {
        boolean isLiving = ObscureInformationProcessor.isLiving(new Date(1970, JANUARY, 4), new Date(2010, JUNE, 16), LocalDate.of(2025, 3, 16));
        assertFalse(isLiving);
    }

    /**
     * Where someone has an unknown birthdate and unknown death date
     */
    @Test
    public void testIsLiving_isDead_Unknown_Unknown() {
        boolean isLiving = ObscureInformationProcessor.isLiving(Date.EXISTS_BUT_UNKNOWN, Date.EXISTS_BUT_UNKNOWN, LocalDate.of(2025, 3, 16));
        assertFalse(isLiving);
    }

    /**
     * Where someone has an empty birthdate and unknown death date
     */
    @Test
    public void testIsLiving_isDead_Empty_And_Unknown() {
        boolean isLiving = ObscureInformationProcessor.isLiving(Date.EMPTY, Date.EXISTS_BUT_UNKNOWN, LocalDate.of(2025, 3, 16));
        assertFalse(isLiving);
    }

    /**
     * Where someone has an valid birthdate and unknown death date
     */
    @Test
    public void testIsLiving_isDead_ValidBirthday_And_Unknown() {
        boolean isLiving = ObscureInformationProcessor.isLiving(new Date(1970, JANUARY, 4), Date.EXISTS_BUT_UNKNOWN, LocalDate.of(2025, 3, 16));
        assertFalse(isLiving);
    }

}
