package com.renomad.inmra.auth.services;

import com.renomad.inmra.security.ISecurityUtils;
import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.security.ITheBrig;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;

public class BruteForceCheckerTests {


    private TestLogger logger;
    private boolean isScriptedLogin;
    private boolean isInJail;
    private BruteForceChecker bruteForceChecker;
    private MyFakeSecurityUtils myFakeSecurityUtils;
    private MyFakeBrig myFakeBrig;

    @Before
    public void init() throws IOException {
        Context context = buildTestingContext("person_auditor_tests");
        logger = (TestLogger) context.getLogger();
        myFakeSecurityUtils = new MyFakeSecurityUtils();
        myFakeBrig = new MyFakeBrig();
        bruteForceChecker = new BruteForceChecker(myFakeSecurityUtils, myFakeBrig, logger);

    }


    @Test
    public void testBruteForceChecker_GettingHacked() {
        isScriptedLogin = true;
        isInJail = true;

        boolean isHackingUs = bruteForceChecker.check("123.123.123.123");

        assertTrue(isHackingUs);
    }

    @Test
    public void testBruteForceChecker_NotGettingHacked() {
        isScriptedLogin = false;

        boolean isHackingUs = bruteForceChecker.check("123.123.123.123");

        assertFalse(isHackingUs);
    }

    /**
     * If either SecurityUtils or TheBrig is null, our method
     * will return false.  This is because those classes should
     * only be null in unusual circumstances, like when we
     * are testing something and have not built all the necessary classes.
     */
    @Test
    public void testBruteForceChecker_SecurityUtilsNull() {
        var halfCookedBruteForceChecker = new BruteForceChecker(null, myFakeBrig, logger);

        boolean isHackingUs = halfCookedBruteForceChecker.check("123.123.123.123");

        assertFalse(isHackingUs);
    }

    @Test
    public void testBruteForceCheckerTheBrigNull() {
        var halfCookedBruteForceChecker = new BruteForceChecker(myFakeSecurityUtils, null, logger);

        boolean isHackingUs = halfCookedBruteForceChecker.check("123.123.123.123");

        assertFalse(isHackingUs);
    }

    /**
     * If both the brig and security utils are null, return false
     */
    @Test
    public void testBruteForceCheckerBothNullNull() {
        var halfCookedBruteForceChecker = new BruteForceChecker(null, null, logger);

        boolean isHackingUs = halfCookedBruteForceChecker.check("123.123.123.123");

        assertFalse(isHackingUs);
    }

    private class MyFakeSecurityUtils implements ISecurityUtils {
        @Override public ISecurityUtils initialize() {return null;}
        @Override public void stop() {}

        @Override
        public boolean isScriptedLogin(String clientAddress) {
            return isScriptedLogin;
        }
    }

    private class MyFakeBrig implements ITheBrig {
        @Override public ITheBrig initialize() {return null;}
        @Override public void stop() {}
        @Override public void sendToJail(String clientIdentifier, long sentenceDuration) {}
        @Override public boolean isInJail(String clientIdentifier) {return isInJail;}
        @Override public List<Map.Entry<String, Long>> getInmates() {return null;}
    }
}


