package com.renomad.inmra.featurelogic.persons;

import com.renomad.minum.database.DbData;

import java.util.Objects;
import java.util.UUID;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

public final class PersonMetrics extends DbData<PersonMetrics> {

    public static final PersonMetrics EMPTY = new PersonMetrics(0, "", new UUID(0,0), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Date.EMPTY, Date.EMPTY, "", "", false, 0, 0, 0, 0);

    private long index;
    private final String name;
    private final UUID personUuid;
    private final int bioImageCount;
    private final int bioVideoCount;
    private final int imageCount;
    private final int videoCount;
    private final int spouseCount;
    private final int siblingCount;
    private final int childCount;
    private final int ageYears;
    private final int bioCharCount;
    private final int countCloseRelatives;
    private final int countFirstCousins;
    private final int countAncestors;
    private final int countDescendants;
    private final int countNephewsNieces;
    private final int countUnclesAunts;
    private final Date birthdate;
    private final Date deathdate;
    private final String biographyStart;
    private final String extraFields;
    private final boolean hasHeadshot;
    private final int familyTreeSize;
    private final int notesCharCount;
    private final int summaryCharCount;
    private final int countCousins;

    public PersonMetrics(long index,
                         String name,
                         UUID personUuid,
                         int bioImageCount,
                         int bioVideoCount,
                         int imageCount,
                         int videoCount,
                         int spouseCount,
                         int siblingCount,
                         int childCount,
                         int ageYears,
                         int bioCharCount,
                         int countCloseRelatives,
                         int countFirstCousins,
                         int countAncestors,
                         int countDescendants,
                         int countNephewsNieces,
                         int countUnclesAunts,
                         Date birthdate,
                         Date deathdate,
                         String biographyStart,
                         String extraFields,
                         boolean hasHeadshot,
                         int familyTreeSize,
                         int notesCharCount,
                         int summaryCharCount,
                         int countCousins
    ) {
        this.index = index;
        this.name = name;
        this.personUuid = personUuid;
        this.bioImageCount = bioImageCount;
        this.bioVideoCount = bioVideoCount;
        this.imageCount = imageCount;
        this.videoCount = videoCount;
        this.spouseCount = spouseCount;
        this.siblingCount = siblingCount;
        this.childCount = childCount;
        this.ageYears = ageYears;
        this.bioCharCount = bioCharCount;
        this.countCloseRelatives = countCloseRelatives;
        this.countFirstCousins = countFirstCousins;
        this.countAncestors = countAncestors;
        this.countDescendants = countDescendants;
        this.countNephewsNieces = countNephewsNieces;
        this.countUnclesAunts = countUnclesAunts;
        this.birthdate = birthdate;
        this.deathdate = deathdate;
        this.biographyStart = biographyStart;
        this.extraFields = extraFields;
        this.hasHeadshot = hasHeadshot;
        this.familyTreeSize = familyTreeSize;
        this.notesCharCount = notesCharCount;
        this.summaryCharCount = summaryCharCount;
        this.countCousins = countCousins;
    }

    @Override
    public String serialize() {
        return serializeHelper(
            index,
            name,
            personUuid.toString(),
            bioImageCount,
            bioVideoCount,
            imageCount,
            videoCount,
            spouseCount,
            siblingCount,
            childCount,
            ageYears,
            bioCharCount,
            countCloseRelatives,
            countFirstCousins,
            countAncestors,
            countDescendants,
            countNephewsNieces,
            countUnclesAunts,
            birthdate,
            deathdate,
            biographyStart,
            extraFields,
            hasHeadshot,
            familyTreeSize,
            notesCharCount,
            summaryCharCount,
            countCousins
        );
    }

    @Override
    public PersonMetrics deserialize(String serializedText) {
        final var tokens =  deserializeHelper(serializedText);
        return new PersonMetrics(
                Long.parseLong(tokens.get(0)),
                tokens.get(1),
                UUID.fromString(tokens.get(2)),
                Integer.parseInt(tokens.get(3)),
                Integer.parseInt(tokens.get(4)),
                Integer.parseInt(tokens.get(5)),
                Integer.parseInt(tokens.get(6)),
                Integer.parseInt(tokens.get(7)),
                Integer.parseInt(tokens.get(8)),
                Integer.parseInt(tokens.get(9)),
                Integer.parseInt(tokens.get(10)),
                Integer.parseInt(tokens.get(11)),
                Integer.parseInt(tokens.get(12)),
                Integer.parseInt(tokens.get(13)),
                Integer.parseInt(tokens.get(14)),
                Integer.parseInt(tokens.get(15)),
                Integer.parseInt(tokens.get(16)),
                Integer.parseInt(tokens.get(17)),
                Date.fromString(tokens.get(18)),
                Date.fromString(tokens.get(19)),
                tokens.get(20),
                tokens.get(21),
                Boolean.parseBoolean(tokens.get(22)),
                Integer.parseInt(tokens.get(23)),
                Integer.parseInt(tokens.get(24)),
                Integer.parseInt(tokens.get(25)),
                Integer.parseInt(tokens.get(26))
        );
    }

    @Override
    public long getIndex() {
        return index;
    }

    @Override
    public void setIndex(long index) {
        this.index = index;
    }

    public UUID getPersonUuid() {
        return personUuid;
    }

    public int getBioImageCount() {
        return bioImageCount;
    }

    public int getBioVideoCount() {
        return bioVideoCount;
    }

    public int getImageCount() {
        return imageCount;
    }

    public int getVideoCount() {
        return videoCount;
    }

    public int getSpouseCount() {
        return spouseCount;
    }

    public int getSiblingCount() {
        return siblingCount;
    }

    public int getChildCount() {
        return childCount;
    }

    public int getAgeYears() {
        return ageYears;
    }

    public int getBioCharCount() {
        return bioCharCount;
    }

    public int getCountCloseRelatives() {
        return countCloseRelatives;
    }

    public int getCountFirstCousins() {
        return countFirstCousins;
    }

    public int getCountAncestors() {
        return countAncestors;
    }

    public int getCountDescendants() {
        return countDescendants;
    }

    public int getCountNephewsNieces() {
        return countNephewsNieces;
    }

    public int getCountUnclesAunts() {
        return countUnclesAunts;
    }

    public Date getBirthdate() {
        return birthdate;
    }

    public Date getDeathdate() {
        return deathdate;
    }

    /**
     * Gets the start of the biography - up to 20 characters
     */
    public String getBiographyStart() {
        return biographyStart;
    }

    public String getName() {
        return name;
    }

    public String getExtraFields() {
        return extraFields;
    }

    public boolean hasHeadshot() {
        return hasHeadshot;
    }

    public int getFamilyTreeSize() {
        return familyTreeSize;
    }

    public int getNotesCharCount() {
        return notesCharCount;
    }

    public int getSummarySize() {
        return summaryCharCount;
    }

    public int getCousinsCount() {
        return countCousins;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PersonMetrics that = (PersonMetrics) o;
        return index == that.index && bioImageCount == that.bioImageCount && bioVideoCount == that.bioVideoCount && imageCount == that.imageCount && videoCount == that.videoCount && spouseCount == that.spouseCount && siblingCount == that.siblingCount && childCount == that.childCount && ageYears == that.ageYears && bioCharCount == that.bioCharCount && countCloseRelatives == that.countCloseRelatives && countFirstCousins == that.countFirstCousins && countAncestors == that.countAncestors && countDescendants == that.countDescendants && countNephewsNieces == that.countNephewsNieces && countUnclesAunts == that.countUnclesAunts && hasHeadshot == that.hasHeadshot && familyTreeSize == that.familyTreeSize && notesCharCount == that.notesCharCount && summaryCharCount == that.summaryCharCount && countCousins == that.countCousins && Objects.equals(name, that.name) && Objects.equals(personUuid, that.personUuid) && Objects.equals(birthdate, that.birthdate) && Objects.equals(deathdate, that.deathdate) && Objects.equals(biographyStart, that.biographyStart) && Objects.equals(extraFields, that.extraFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, name, personUuid, bioImageCount, bioVideoCount, imageCount, videoCount, spouseCount, siblingCount, childCount, ageYears, bioCharCount, countCloseRelatives, countFirstCousins, countAncestors, countDescendants, countNephewsNieces, countUnclesAunts, birthdate, deathdate, biographyStart, extraFields, hasHeadshot, familyTreeSize, notesCharCount, summaryCharCount, countCousins);
    }

    @Override
    public String toString() {
        return "PersonMetrics{" +
                "index=" + index +
                ", name='" + name + '\'' +
                ", personUuid=" + personUuid +
                ", bioImageCount=" + bioImageCount +
                ", bioVideoCount=" + bioVideoCount +
                ", imageCount=" + imageCount +
                ", videoCount=" + videoCount +
                ", spouseCount=" + spouseCount +
                ", siblingCount=" + siblingCount +
                ", childCount=" + childCount +
                ", ageYears=" + ageYears +
                ", bioCharCount=" + bioCharCount +
                ", countCloseRelatives=" + countCloseRelatives +
                ", countFirstCousins=" + countFirstCousins +
                ", countAncestors=" + countAncestors +
                ", countDescendants=" + countDescendants +
                ", countNephewsNieces=" + countNephewsNieces +
                ", countUnclesAunts=" + countUnclesAunts +
                ", birthdate=" + birthdate +
                ", deathdate=" + deathdate +
                ", biographyStart='" + biographyStart + '\'' +
                ", extraFields='" + extraFields + '\'' +
                ", hasHeadshot=" + hasHeadshot +
                ", familyTreeSize=" + familyTreeSize +
                ", notesCharCount=" + notesCharCount +
                ", summaryCharCount=" + summaryCharCount +
                ", countCousins=" + countCousins +
                '}';
    }
}
