package com.renomad.inmra.featurelogic.photo;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class PhotoTests {

    @Test
    public void equalsTest() {
        EqualsVerifier.simple().forClass(Photograph.class).verify();
    }
}
