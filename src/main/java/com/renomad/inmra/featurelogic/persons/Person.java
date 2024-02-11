package com.renomad.inmra.featurelogic.persons;

import com.renomad.minum.database.DbData;

import java.util.UUID;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

/**
 * This class represents a unique individual, but has
 * no information on them.  Information is stored elsewhere.
 * Specifically, it is written to disk in person_files.
 */
public class Person extends DbData<Person> {

    private final String name;
    private final Date birthday;
    private final Date deathday;
    private Long index;
    private final UUID id;

    public Person(Long index, UUID id, String name, Date birthday, Date deathday) {
        this.index = index;
        this.id = id;
        this.name = name;
        this.birthday = birthday;
        this.deathday = deathday;
    }

    public static final Person EMPTY = new Person(0L, new UUID( 0 , 0 ), "", Date.EMPTY, Date.EMPTY);

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
        return serializeHelper(index, id, name, birthday, deathday);
    }

    @Override
    public Person deserialize(String serializedText) {
        final var tokens = deserializeHelper(serializedText);

        return new Person(
                Long.parseLong(tokens.get(0)),
                UUID.fromString(tokens.get(1)),
                tokens.get(2),
                Date.fromString(tokens.get(3)),
                Date.fromString(tokens.get(4))
        );
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Date getBirthday() {
        return birthday;
    }

    public Date getDeathday() {
        return deathday;
    }

    @Override
    public String toString() {
        return "Person{" +
                "name='" + name + '\'' +
                ", birthday=" + birthday.getPrettyString() +
                ", deathday=" + deathday.getPrettyString() +
                ", index=" + index +
                ", id=" + id +
                '}';
    }

}
