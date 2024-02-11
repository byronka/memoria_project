package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.Gender;
import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.assertEquals;

public class RelationshipTextServiceTests {

    @Test
    public void testGetGenderedRelationshipString_Husband() {
        String result = RelationshipTextService.getGenderedRelationshipString("spouse", Gender.MALE);
        assertEquals(result, "husband");
    }

    @Test
    public void testGetGenderedRelationshipString_Sister() {
        String result = RelationshipTextService.getGenderedRelationshipString("sibling", Gender.FEMALE);
        assertEquals(result, "sister");
    }

    @Test
    public void testGetGenderedRelationshipString_Ungendered() {
        String result = RelationshipTextService.getGenderedRelationshipString("sibling", Gender.UNKNOWN);
        assertEquals(result, "sibling");
    }
}
