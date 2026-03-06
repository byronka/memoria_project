package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.Date;

import java.time.LocalDate;

/**
 * This little class is for determining whether to show information about a person,
 * or if a privacy password is required.
 */
public class ObscureInformationProcessor {

    private final LocalDate now;

    public ObscureInformationProcessor() {
        this(LocalDate.now());
    }

    public ObscureInformationProcessor(LocalDate now) {
        this.now = now;
    }

    /**
     * Whether to hide information about a person.  For example, if this is a living person.
     */
    public boolean shouldObscureInformation(Date birthDay, Date deathDay, boolean shouldShowPrivateInformation) {
        return !shouldShowPrivateInformation && isLiving(birthDay, deathDay, now);
    }

    public static boolean isLiving(Date birthDay, Date deathDay, LocalDate now) {
        if (birthDay.equals(Date.EXISTS_BUT_UNKNOWN) && deathDay.equals(Date.EMPTY)) {
            return true;
        } else {
            return deathDay.equals(Date.EMPTY) && (birthDay.equals(Date.EMPTY) || now.getYear() - birthDay.year() < 110);
        }
    }

}
