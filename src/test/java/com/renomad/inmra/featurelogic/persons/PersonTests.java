package com.renomad.inmra.featurelogic.persons;

import com.renomad.inmra.featurelogic.persons.PersonFile.ExtraFieldTriple;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static com.renomad.minum.testing.TestFramework.assertEquals;

/**
 * This is a test laboratory for considering aspects of Persons
 */
public class PersonTests {

    /**
     * Basic happy-path test of extra fields
     * <p>
     * For our persons, it would be nice to store certain data as
     * key-value-type tuples, for this reason:
     * </p>
     * <p>
     * Imagine some of our persons have anniversaries.  Or a
     * birth-location.  Or a preferred stepmother.  Or whatever
     * else, because when it comes to humans, it's messy and
     * complex.  But, we would like to track some of that stuff
     * in an organized way so we can provide functionality based
     * on it, like searches, graphs, etc.
     * </p>
     * <p>
     * How to do this?  key-value-type tuples.
     * We can pretty easily serialize/deserialize.
     * </p>
     * <p>
     * All the other aspects of the Person are fairly intrinsic.
     * Well, at least moderately common.  Having siblings.  Having
     * children.  Spouses.  But for all other stuff, we can have
     * flexibility.
     * </p>
     * <p>
     * To be clear, the UI ought to have some guardrails for this.
     * We won't want things going completely bonkers.  So we will
     * suggest a variety of options for keys in the UI, along with
     * an "other" option for those rare situations where the
     * suggestions can't handle it - and in that case, it might make
     * more sense just to store the data in the biography field.
     * </p>
     */
    @Test
    public void testOptionalKeyValueTypeTriples() {
        // the expectation is that the client has some javascript
        // running and takes the values from multiple inputs
        // to build the data.
        var myTriples = new ArrayList<ExtraFieldTriple>();
        myTriples.add(new ExtraFieldTriple("key1", "value1", "type1"));
        myTriples.add(new ExtraFieldTriple("key2", "value2", "type2"));

        // serialize the triples
        String collectedFields = ExtraFieldTriple.serialize(myTriples);

        var myPerson = new PersonFile(
                1L,
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                "",
                "",
                Date.EMPTY,
                Date.EMPTY,
                "",
                "",
                "",
                "",
                "",
                "",
                collectedFields,
                Gender.UNKNOWN,
                LocalDate.of(2023, 12, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
                ""
                );

        // make sure it serializes ok
        var deserializedPerson = myPerson.deserialize(myPerson.serialize());
        assertEquals(myPerson, deserializedPerson);

        // make sure we can comprehend it as a list of triples
        var extraFieldData = deserializedPerson.getExtraFieldsAsList();

        assertEquals(extraFieldData.get(0).value(), "value1");
    }

}
