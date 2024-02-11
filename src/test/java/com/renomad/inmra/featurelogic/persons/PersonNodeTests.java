package com.renomad.inmra.featurelogic.persons;

import org.junit.Test;

import java.util.UUID;

import static com.renomad.minum.testing.TestFramework.assertFalse;
import static org.junit.Assert.assertEquals;

public class PersonNodeTests {

    @Test
    public void testPersonEquals() {
        PersonNode alice = new PersonNode(
                UUID.fromString("b0d61180-8eb8-41ca-9276-0501701c4df3"),
                "alice", Gender.FEMALE);
        PersonNode alice2 = new PersonNode(
                UUID.fromString("b0d61180-8eb8-41ca-9276-0501701c4df3"),
                "alice", Gender.FEMALE);
        PersonNode bob = new PersonNode(
                UUID.fromString("d03aed60-1ee1-412c-84b2-dee7010f03fa"),
                "bob", Gender.MALE);

        assertEquals(alice, alice2);
        assertFalse(alice.equals(bob));
    }
}
