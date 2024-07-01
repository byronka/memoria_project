package com.renomad.inmra.security;

import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.assertTrue;

public class KeyProcessorTests {

    /**
     * A basic happy path.  We will give it ordinary
     * parameters and make sure it behaves.
     */
    @Test
    public void testKeyProcessor() {
        Context context = TestFramework.buildTestingContext("keyprocessor");
        KeyProcessor keyProcessor = new KeyProcessor(context.getLogger());
        Map<String, Long> keys = new HashMap<>();
        keys.put("abc", (long) 2 );
        keys.put("def", (long) 3 );
        int investigationLifespan = 4;
        Map<String, List<Long>> clientsAndTimes = new HashMap<>();
        clientsAndTimes.put("abc", List.of(1L,2L,3L));
        long now = 5;

        keyProcessor.processKeysUnderConsideration(keys, investigationLifespan, clientsAndTimes, now);

        assertEquals(clientsAndTimes.get("abc").size(), 3);
    }

    /**
     * If the keys under consideration are very recent, then not
     * enough time will have passed to cease inspection of them.
     */
    @Test
    public void testKeyProcessor_EverythingYoung() {
        Context context = TestFramework.buildTestingContext("keyprocessor");
        KeyProcessor keyProcessor = new KeyProcessor(context.getLogger());
        // prepare some entries in the keys map
        Map<String, Long> keys = new HashMap<>();
        keys.put("abc", (long) 2 ); // at 2 o'clock, abc got entered for inspection
        keys.put("def", (long) 3 ); // at 3 o'clock ... (just pretending, bear with me)
        int investigationLifespan = 4;  // the investigation window is 4 hours
        // prepare some pretend previous entries in clientsAndTimes
        Map<String, List<Long>> clientsAndTimes = new HashMap<>();
        clientsAndTimes.put("abc", List.of(1L,2L));
        long now = 10000;

        keyProcessor.processKeysUnderConsideration(keys, investigationLifespan, clientsAndTimes, now);


        assertTrue(clientsAndTimes.get("abc") == null);
    }
}
