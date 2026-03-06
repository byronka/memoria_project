package com.renomad.inmra.security;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.utils.MyThread;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.ExecutorService;

import static com.renomad.minum.testing.TestFramework.*;

public class SecurityUtilsTests {

    private static TestLogger logger;
    private static ExecutorService executorService;

    @BeforeClass
    public static void init() {
        var context = buildTestingContext("unit_tests");
        logger = (TestLogger) context.getLogger();
        executorService = context.getExecutorService();
    }

    /*
    If a client is going through the login process multiple times too quickly,
    it is a sign they are a bad actor.  It's one thing to mess up a login at human speed,
    it's another to mess up at scripted speed.  In that case, we assume they are trying to brute
    force passwords and lock them out.
     */
    @Test
    public void test_Login_EdgeCase_TooOften() {
        var securityUtils = new SecurityUtils(executorService, logger).initialize();
        assertFalse(securityUtils.isScriptedLogin("1.2.3.4"));
        assertTrue(securityUtils.isScriptedLogin("1.2.3.4"));
        securityUtils.stop();
    }
}
