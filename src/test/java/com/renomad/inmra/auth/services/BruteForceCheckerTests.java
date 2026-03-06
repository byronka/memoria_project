package com.renomad.inmra.auth.services;

import com.renomad.inmra.security.ISecurityUtils;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.security.Inmate;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static com.renomad.minum.testing.TestFramework.*;

public class BruteForceCheckerTests {


    private TestLogger logger;
    private boolean isScriptedLogin;
    private boolean hasExceededAllowedFailures;
    private boolean isInJail;
    private BruteForceChecker bruteForceChecker;
    private MyFakeSecurityUtils myFakeSecurityUtils;
    private MyFakeBrig myFakeBrig;
    private Context context;

    @Before
    public void init() {
        context = buildTestingContext("person_auditor_tests");
        logger = (TestLogger) context.getLogger();
        myFakeSecurityUtils = new MyFakeSecurityUtils();
        myFakeBrig = new MyFakeBrig();
        bruteForceChecker = new BruteForceChecker(myFakeSecurityUtils, myFakeBrig, logger);
    }

    @After
    public void cleanup() {
        TestFramework.shutdownTestingContext(context);
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

        @Override
        public boolean hasExceededAllowedFailuresInTimeWindow(String remoteRequester) {
            return hasExceededAllowedFailures;
        }
    }

    private class MyFakeBrig implements ITheBrig {
        @Override public ITheBrig initialize() {return null;}
        @Override public void stop() {}
        @Override public boolean sendToJail(String clientIdentifier, long sentenceDuration) {
            return false;
        }
        @Override public boolean isInJail(String clientIdentifier) {return isInJail;}
        @Override public List<Inmate> getInmates() {return null;}
    }

    /**
     * In this case, the ip address was already in jail, so
     * we tack on more time
     */
    @Test
    public void testCheckForFailedLogins_AlreadyInJail() {
        isInJail = true;
        hasExceededAllowedFailures = false;

        boolean isHackingUs = bruteForceChecker.checkForFailedLogins("123.123.123.123");

        assertTrue(isHackingUs);
    }

    /**
     * In this case, the ip address was not yet in jail but they exceeded a
     * reasonable number of failed login attempts in a time window
     */
    @Test
    public void testCheckForFailedLogins_NotInJailButExceededAllowedFailures() {
        isInJail = false;
        hasExceededAllowedFailures = true;

        boolean isHackingUs = bruteForceChecker.checkForFailedLogins("123.123.123.123");

        assertTrue(isHackingUs);
    }

    /**
     * In this case, the ip address was not in jail and they did not exceed a
     * reasonable number of failed login attempts in a time window
     */
    @Test
    public void testCheckForFailedLogins_StillWithinFailureLimit() {
        isInJail = false;
        hasExceededAllowedFailures = false;

        boolean isHackingUs = bruteForceChecker.checkForFailedLogins("123.123.123.123");

        assertFalse(isHackingUs);
    }

    /**
     * In this case, the brig is null so we skip all the behavior and return false.
     */
    @Test
    public void testCheckForFailedLogins_noBrig() {
        var halfCookedBruteForceChecker = new BruteForceChecker(myFakeSecurityUtils, null, logger);

        boolean isHackingUs = halfCookedBruteForceChecker.checkForFailedLogins("123.123.123.123");

        assertFalse(isHackingUs);
    }

    /**
     * In this case, the securityUtils is null so we skip all the behavior and return false.
     */
    @Test
    public void testCheckForFailedLogins_noSecurityUtils() {
        var halfCookedBruteForceChecker = new BruteForceChecker(null, myFakeBrig, logger);

        boolean isHackingUs = halfCookedBruteForceChecker.checkForFailedLogins("123.123.123.123");

        assertFalse(isHackingUs);
    }
}


