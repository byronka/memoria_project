package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.PersonFile;

import java.time.format.DateTimeParseException;

import com.renomad.inmra.featurelogic.persons.Date;
import com.renomad.minum.logging.ILogger;

/**
 * This class is responsible for generating a lifespan
 * for a person - that is, the text string indicating their
 * birth and death dates.
 */
public class Lifespan {

    private final ILogger logger;

    public Lifespan(ILogger logger) {

        this.logger = logger;
    }

    /**
     * A lifespan is just the two dates: birthdate and deathdate
     */
    public String renderLifespan(PersonFile deserializedPersonFile) {
        // only add the lifespan indicator if they have entered a birthdate or deathdate
        String lifespan;

        // These are the strings straight out of the database, untouched
        String renderedBornString = deserializedPersonFile.getBorn().getPrettyString();
        String renderedDeathString = deserializedPersonFile.getDied().getPrettyString();

        if (renderedBornString.isBlank() && renderedDeathString.isBlank()) {
            // if we have no dates for born or died, the lifespan is just an empty string
            lifespan = "";
        } else {

            // otherwise, let's see what we can show for their lifespan.  If we have valid
            // dates for birth and death, we'll try showing them both, along with their
            // final age.  Otherwise, we'll retreat to just showing their birth and death dates.
            if (!renderedBornString.isBlank() && !renderedDeathString.isBlank() &&
                    ! deserializedPersonFile.getBorn().equals(Date.EMPTY) &&
                    ! deserializedPersonFile.getBorn().equals(Date.EXISTS_BUT_UNKNOWN) &&
                    ! deserializedPersonFile.getDied().equals(Date.EMPTY) &&
                    ! deserializedPersonFile.getDied().equals(Date.EXISTS_BUT_UNKNOWN)
            ) {
                long age;
                try {
                    age = Date.calcYearsBetween(deserializedPersonFile.getBorn(), deserializedPersonFile.getDied());
                } catch (DateTimeParseException ex) {
                    logger.logAsyncError(() -> "Failed to parse dates in addLifespanToTemplate. " + ex);
                    throw ex;
                }

                lifespan = "%s to %s (%d years)".formatted(renderedBornString, renderedDeathString, age);
            } else {
                lifespan = "%s to %s".formatted(renderedBornString, renderedDeathString);
            }

        }
        return lifespan;
    }

}
