package com.renomad.inmra.featurelogic.persons;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static com.renomad.minum.testing.TestFramework.assertEquals;

public class PersonFileTests {

    PersonFile alice = new PersonFile(
            1L,
            UUID.fromString("8a4f05ec-1184-41ac-9de4-ffdc98a08081"),
            "a", "alice",Date.EMPTY,Date.EMPTY,"e","f",
            "g","h", "j","k","m",Gender.FEMALE, null, "");
    PersonFile bob = new PersonFile(
            2L,
            UUID.fromString("d41dfedd-8c0f-4cbe-80c5-2e65f11483b3"),
            "a", "bob",Date.EMPTY,Date.EMPTY,"e","f",
            "g","h", "j","k","m",Gender.MALE, null, "");

    /**
     * When we add a PersonFile as a key, it will hash the
     * object based on certain fields
     */
    @Test
    public void testHashingOfPersonFile() {
        HashMap<PersonFile, Integer> myMap = new HashMap<>();
        myMap.put(alice, 1);
        myMap.put(bob, 2);
        assertEquals(myMap.get(bob), 2);
    }

    @Test
    public void testGetData() {
        alice.setIndex(2L);
        assertEquals(alice.getIndex(), 2L);
    }

    /**
     * We store extra fields into a single string,
     * see {@link com.renomad.inmra.featurelogic.persons.PersonFile.ExtraFieldTriple}
     */
    @Test
    public void testDeserializationExtraField() {
        var extraField = new PersonFile.ExtraFieldTriple("foo key", "bar value", "text");
        String serialized = PersonFile.ExtraFieldTriple.serialize(List.of(extraField));
        PersonFile.ExtraFieldTriple deserialized = PersonFile.ExtraFieldTriple.deserialize(serialized);
        assertEquals(extraField, deserialized);
    }
}
