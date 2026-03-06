package com.renomad.inmra.utils;

import com.renomad.inmra.featurelogic.persons.Person;

import java.util.List;

/**
 * Data that is cached for performance gains
 */
public class CachedData {

    /**
     * This is a string of HTML data that we show on the homepage, listing
     * the birth and death days of people in the system.
     */
    private String birthDeathDaysRendered;

    /**
     * This is a string of HTML data that we show on the homepage, listing
     * the birth and death days of people in the system. This version differs
     * from {@link #birthDeathDaysRendered} because it includes the birthdays
     * of living people, and will only be shown to authenticated users.
     */
    private String birthDeathDaysWithLivingRendered;

    /**
     * A list of the "most interesting" people in the system - these are the
     * person entries that have the most data in their biographies, with the
     * largest set of close relations.
     */
    private List<Person> mostInterestingPeopleNoLiving;

    /**
     * Similar to {@link #mostInterestingPeopleNoLiving} except it also
     * includes living people, and is thus only viewable by authenticated users.
     */
    private List<Person> mostInterestingPeopleIncludingLiving;

    public CachedData() {}

    public String getBirthDeathDaysRendered() {
        return birthDeathDaysRendered;
    }

    public void setBirthDeathDaysRendered(String birthDeathDaysRendered) {
        this.birthDeathDaysRendered = birthDeathDaysRendered;
    }

    public String getBirthDeathDaysWithLivingRendered() {
        return birthDeathDaysWithLivingRendered;
    }

    public void setBirthDeathDaysWithLivingRendered(String birthDeathDaysWithLivingRendered) {
        this.birthDeathDaysWithLivingRendered = birthDeathDaysWithLivingRendered;
    }

    public List<Person> getMostInterestingPeopleNoLiving() {
        return mostInterestingPeopleNoLiving;
    }

    public void setMostInterestingPeopleNoLiving(List<Person> mostInterestingPeopleNoLiving) {
        this.mostInterestingPeopleNoLiving = mostInterestingPeopleNoLiving;
    }

    public List<Person> getMostInterestingPeopleIncludingLiving() {
        return mostInterestingPeopleIncludingLiving;
    }

    public void setMostInterestingPeopleIncludingLiving(List<Person> mostInterestingPeopleIncludingLiving) {
        this.mostInterestingPeopleIncludingLiving = mostInterestingPeopleIncludingLiving;
    }
}
