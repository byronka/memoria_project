package com.renomad.inmra.featurelogic.photo;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class VideoTests {

    @Test
    public void equalsTest() {
        EqualsVerifier.simple().forClass(Video.class).verify();
    }
}
