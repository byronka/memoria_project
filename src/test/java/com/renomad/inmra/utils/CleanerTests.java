package com.renomad.inmra.utils;

import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.assertEquals;

public class CleanerTests {

    /**
     * If we encounter a script element, replace it with
     * characters so that it will not be interpreted as
     * an element by the browser.
     */
    @Test
    public void testCleanScript_HappyPath() {
        String input = "foo <script> bar </script>";
        String output = "foo SCRIPT_STARTING_TAG bar </script>";

        String result = Cleaners.cleanScript(input);

        assertEquals(result, output);
    }

    /**
     * It is not enough to clean the canonical starting
     * tag - we also need to clean it when there are extra
     * spaces and whatnot.
     */
    @Test
    public void testCleanScriptExtraSpaces() {
        assertEquals(Cleaners.cleanScript("< script> this is a foo"), "SCRIPT_STARTING_TAG this is a foo");
        assertEquals(Cleaners.cleanScript("<  script></script>"), "SCRIPT_STARTING_TAG</script>");
        assertEquals(Cleaners.cleanScript("<script  > foo foo </ script>"), "SCRIPT_STARTING_TAG foo foo </ script>");
        assertEquals(Cleaners.cleanScript("<script  id=\"biz\" class=bar type=foofoo> foo foo </ script>"), "SCRIPT_STARTING_TAG foo foo </ script>");
    }
}
