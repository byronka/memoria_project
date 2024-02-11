package com.renomad.inmra.featurelogic.persons;

/**
 * Data that represents a month, intended to be used in {@link Date}
 */
public enum Month {
    /**
     * None represents "no month at all", which is
     * used to indicate that the only part of the date
     * is the year.  Pretty common for a lot of ancestors,
     * to only know the year.
     */

    JANUARY("January", 1),
    FEBRUARY("February", 2),
    MARCH("March", 3),
    APRIL("April", 4),
    MAY("May", 5),
    JUNE("June", 6),
    JULY("July", 7),
    AUGUST("August", 8),
    SEPTEMBER("September", 9),
    OCTOBER("October", 10),
    NOVEMBER("November", 11),
    DECEMBER("December", 12),
    NONE("None", 13);

    private final String capitalizedName;
    private final int monthOrdinal;

    Month(String capitalizedName, int monthOrdinal) {
        this.capitalizedName = capitalizedName;
        this.monthOrdinal = monthOrdinal;
    }

    public String getCapitalizedName() {
        return capitalizedName;
    }

    /**
     * Get a numeric value for this month. January equals 1, December equals 12.
     */
    public int getMonthOrdinal() {
        return monthOrdinal;
    }

    /**
     * 1 equals January, 2 equals February, ... 12 equals December
     * <p></p>
     * Anything out of scope gets an OutOfBounds exception thrown.
     */
    public static Month getByOrdinal(int monthOrdinal) {
        return switch (monthOrdinal) {
            case 1 -> Month.JANUARY;
            case 2 -> Month.FEBRUARY;
            case 3 -> Month.MARCH;
            case 4 -> Month.APRIL;
            case 5 -> Month.MAY;
            case 6 -> Month.JUNE;
            case 7 -> Month.JULY;
            case 8 -> Month.AUGUST;
            case 9 -> Month.SEPTEMBER;
            case 10 -> Month.OCTOBER;
            case 11 -> Month.NOVEMBER;
            case 12 -> Month.DECEMBER;
            default -> throw new IndexOutOfBoundsException(String.format("Valid values for months are 1 to 12, inclusive.  You provided: %d", monthOrdinal));
        };
    }
}
