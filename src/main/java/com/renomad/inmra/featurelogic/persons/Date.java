package com.renomad.inmra.featurelogic.persons;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static com.renomad.minum.utils.Invariants.mustBeTrue;

/**
 * A highly-simplified Date class, meant to only store the year, month, and day
 * in a minimalistic fashion.  This is intended for storing and transmitting
 * the data provided us by HTML date input fields (e.g. "1978-1-4") without all
 * the extra fluff you would see in a typical Date class, like the time zone,
 * exact seconds, and so on.
 */
public record Date(int year, Month month, int day) {

    public static final Date EMPTY = new Date(0,Month.JANUARY,0);
    public static final Date EXISTS_BUT_UNKNOWN = new Date(0,Month.JANUARY,1);

    /**
     * Convert to a form we will use for serializing to disk. Example: 1978.1.4
     */
    public String toString() {
        return "%s.%s.%s".formatted(year, month, day);
    }

    /**
     * Get a formatting of this date that fits the expected shape for
     * transmitting in HTML.  Example: 1978-1-04
     */
    public String toHtmlString() {
        // the year is padded with zero's until it is four digits.
        // the month is padded with zero's until it is two digits.
        // the day is padded with zero's until it is two digits
        return "%04d-%02d-%02d".formatted(year, month.getMonthOrdinal(), day);
    }

    public static long calcYearsBetween(Date earlier, Date later) {
        LocalDate before;
        LocalDate after;
        if (earlier.month.equals(Month.NONE)) {
            before = LocalDate.of(earlier.year, 1, 1);
        } else {
            before = LocalDate.parse(earlier.toHtmlString());
        }
        if (later.month.equals(Month.NONE)) {
            after = LocalDate.of(later.year, 1, 1);
        } else {
            after = LocalDate.parse(later.toHtmlString());
        }
        return ChronoUnit.YEARS.between(before, after);
    }

    /**
     * Get a very human-readable version of the date. Example: January 4, 1978
     */
    public String getPrettyString() {
        if (this.equals(Date.EXISTS_BUT_UNKNOWN)) {
            return "Unknown";
        }
        if (this.equals(Date.EMPTY)) {
            return "";
        }
        // if the month is NONE, then we're dealing
        // with a date that is only a year.
        if (this.month.equals(Month.NONE)) {
            return String.valueOf(year);
        }
        return String.format("%s %s, %s", month.getCapitalizedName(), day, year);
    }

    /**
     * This takes a string in format like 1984.JANUARY.4
     * which is what the toString outputs, back to a Date.
     */
    public static Date fromString(String value) {
        String[] splitValues = value.split("[.]");
        int year = Integer.parseInt(splitValues[0]);
        Month month = Month.valueOf(splitValues[1]);
        int day = Integer.parseInt(splitValues[2]);
        return new Date(year, month, day);
    }


    /**
     * This will receive dates in a format from the HTML date input field
     * like 1921-11-21 and returns {@link Date} values
     * <p>
     *     The input must not be null or blank.
     * </p>
     */
    public static Date extractDate(String dateInput) {
        if (dateInput == null || dateInput.isBlank()) {
            return Date.EMPTY;
        }
        mustBeTrue(dateInput.indexOf('-') > 0, "there must be a dash symbol in this value if we can expect to parse it");
        String[] splitValues = dateInput.split("-");
        int year = Integer.parseInt(splitValues[0]);
        Month month = Month.getByOrdinal(Integer.parseInt(splitValues[1]));
        int day = Integer.parseInt(splitValues[2]);
        return new Date(year, month, day);
    }

    /**
     * Returns the value of this object as a {@link LocalDate}, and
     * returns an empty Optional if the value is a {@link Date#EMPTY},
     * a {@link Date#EXISTS_BUT_UNKNOWN}. If the date is a year-only
     * date (meaning its month value is {@link Month#NONE}, then return
     * it as a LocalDate having that year but month of January and day of 1.
     */
    public Optional<LocalDate> toLocalDate() {
        if (! this.equals(Date.EMPTY) && ! this.equals(Date.EXISTS_BUT_UNKNOWN) && this.month() != Month.NONE) {
            return Optional.of(LocalDate.parse(this.toHtmlString()));
        } else if (this.month() == Month.NONE) {
            return Optional.of(LocalDate.of(this.year(), java.time.Month.JANUARY, 1));
        } else {
            return Optional.empty();
        }
    }
}
