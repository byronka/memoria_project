package com.renomad.inmra.featurelogic.persons;

import com.renomad.minum.database.DbData;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.renomad.minum.utils.SerializationUtils.*;

public class PersonFile extends DbData<PersonFile> {

    public static PersonFile EMPTY = new PersonFile(0L, new UUID(0,0), "","", Date.EMPTY, Date.EMPTY,
            "", "", "", "", "", "", "", Gender.UNKNOWN, Instant.MIN, "");

    private Long index;
    private final UUID id;
    private final String imageUrl;
    private final String name;
    private final Date born;
    private final Date died;
    private final String siblings;
    private final String spouses;
    private final String parents;
    private final String children;
    private final String biography;
    private final Gender gender;
    private final Instant lastModified;
    private final String lastModifiedBy;

    /**
     * These values are not publicly shown, good for stuff you're working
     * on, or wouldn't make for the nicest stuff to show the public.
     */
    private final String notes;

    /**
     * Triples that represent extra fields. For example,
     * the string might look like key1|value1|type1|key2|value2|type2
     */
    private final String extraFields;

    public PersonFile(Long index, UUID id, String imageUrl, String name, Date born, Date died,
                      String siblings, String spouses, String parents, String children,
                      String biography, String notes, String extraFields, Gender gender, Instant lastModified, String lastModifiedBy) {

        this.index = index;
        this.id = id;
        this.imageUrl = imageUrl;
        this.name = name;
        this.born = born;
        this.died = died;
        this.siblings = siblings;
        this.spouses = spouses;
        this.parents = parents;
        this.children = children;
        this.biography = biography;
        this.notes = notes;
        this.extraFields = extraFields;
        this.gender = gender;
        this.lastModified = lastModified;
        this.lastModifiedBy = lastModifiedBy;
    }

    @Override
    public long getIndex() {
        return index;
    }

    @Override
    protected void setIndex(long index) {
        this.index = index;
    }

    @Override
    public String serialize() {
        return serializeHelper(
                index,
                id,
                imageUrl,
                name,
                born,
                died,
                siblings,
                spouses,
                parents,
                children,
                biography,
                notes,
                extraFields,
                gender,
                lastModified,
                lastModifiedBy
                );
    }

    @Override
    public PersonFile deserialize(String serializedText) {
        final var tokens = deserializeHelper(serializedText);

        return new PersonFile(
                Long.parseLong(tokens.get(0)),
                UUID.fromString(tokens.get(1)),
                tokens.get(2),
                tokens.get(3),
                Date.fromString(tokens.get(4)),
                Date.fromString(tokens.get(5)),
                tokens.get(6),
                tokens.get(7),
                tokens.get(8),
                tokens.get(9),
                tokens.get(10),
                tokens.get(11),
                tokens.get(12),
                Gender.deserialize(tokens.get(13)),
                Instant.parse(tokens.get(14)),
                tokens.get(15)
        );
    }

    public UUID getId() {
        return id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getName() {
        return name;
    }

    public Date getBorn() {
        return born;
    }

    public Date getDied() {
        return died;
    }

    public String getSiblings() {
        return siblings;
    }

    public String getSpouses() {
        return spouses;
    }

    public String getParents() {
        return parents;
    }

    public String getChildren() {
        return children;
    }

    public String getBiography() {
        return biography;
    }

    public String getNotes() {
        return notes;
    }

    public Gender getGender() {
        return gender;
    }
    public String getExtraFields() {
        return extraFields;
    }
    public List<ExtraFieldTriple> getExtraFieldsAsList() {
        List<String> strings = deserializeHelper(extraFields);
        var result = new ArrayList<ExtraFieldTriple>();
        int index = 0;
        while((strings.size() - index) > 2) {
            String key = strings.get(index);
            String value = strings.get(index + 1);
            String type = strings.get(index + 2);
            result.add(new ExtraFieldTriple(key, value, type));
            index += 3;
        }
        return result;
    }

    public record ExtraFieldTriple(String key, String value, String type) {
        private String serialize() {
            return serializeHelper(key(), value(), type());
        }

        public static String serialize(List<ExtraFieldTriple> triples) {
            return triples.stream()
                    .map(ExtraFieldTriple::serialize)
                    .collect(Collectors.joining("|"));
        }

        public static ExtraFieldTriple deserialize(String serializedExtraFieldTuple) {
            List<String> tokens = deserializeHelper(serializedExtraFieldTuple);
            return new ExtraFieldTriple(tokens.get(0), tokens.get(1), tokens.get(2));
        }
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PersonFile that = (PersonFile) o;
        return Objects.equals(index, that.index) && Objects.equals(id, that.id) && Objects.equals(imageUrl, that.imageUrl) && Objects.equals(name, that.name) && Objects.equals(born, that.born) && Objects.equals(died, that.died) && Objects.equals(siblings, that.siblings) && Objects.equals(spouses, that.spouses) && Objects.equals(parents, that.parents) && Objects.equals(children, that.children) && Objects.equals(biography, that.biography) && gender == that.gender && Objects.equals(lastModified, that.lastModified) && Objects.equals(lastModifiedBy, that.lastModifiedBy) && Objects.equals(notes, that.notes) && Objects.equals(extraFields, that.extraFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, id, imageUrl, name, born, died, siblings, spouses, parents, children, biography, gender, lastModified, lastModifiedBy, notes, extraFields);
    }

    @Override
    public String toString() {
        return "PersonFile{" +
                "index=" + index +
                ", id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
