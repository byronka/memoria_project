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
        String input = "foo <script> bar SCRIPT_ENDING_TAG";
        String output = "foo SCRIPT_STARTING_TAG bar SCRIPT_ENDING_TAG";

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
        assertEquals(Cleaners.cleanScript("<  script></script>"), "SCRIPT_STARTING_TAGSCRIPT_ENDING_TAG");
        assertEquals(Cleaners.cleanScript("<script  > foo foo </ script>"), "SCRIPT_STARTING_TAG foo foo SCRIPT_ENDING_TAG");
        assertEquals(Cleaners.cleanScript("<script  id=\"biz\" class=bar type=foofoo> foo foo </ script>"), "SCRIPT_STARTING_TAG foo foo SCRIPT_ENDING_TAG");
    }

    @Test
    public void testUtf8ToAscii() {
        assertEquals(Cleaners.utf8ToAscii("100ÂµF"), "100F");
        assertEquals(Cleaners.utf8ToAscii("person?id=38b8b83e-a04f-4050-b9fd-c42f2a872b6f"), "person?id=38b8b83e-a04f-4050-b9fd-c42f2a872b6f");
    }
}
