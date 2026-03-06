package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.PersonMetrics;

public class InterestingScore {
    /**
     * Rank a person by how interesting they are - weighted by their biography size, number of photos and videos
     * in their bio, how many close relatives they have, etc.
     */
    public static int getInterestingScore(PersonMetrics metricsForPerson) {
        if (metricsForPerson == null) {
            // if metrics don't exist for this person, presume they are interesting,
            // rather than returning nothing or throwing a null exception
            return 10_000;
        }
        return metricsForPerson.getBioCharCount() +
                (metricsForPerson.getBioVideoCount() * 5000) +
                (metricsForPerson.getBioImageCount() * 1000) +
                (metricsForPerson.getCountCloseRelatives() * 100) +
                (metricsForPerson.hasHeadshot() ? 1000 : 0);
    }
}
