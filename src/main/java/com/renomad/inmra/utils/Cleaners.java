package com.renomad.inmra.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class holds utility methods for securely replacing characters.
 * <br>
 * To explain better: a browser will interpret certain characters
 * in special ways - a "less-than" symbol starts an element.  So,
 * we may want to replace that character with something else.
 */
public class Cleaners {

    private static final Pattern scriptPattern = Pattern.compile("< *script.*?>");

    /**
     * This is a utility just to defuse the use of any script elements.
     * <br>
     * It is not pretty.  But this is built to prevent any use of
     * script elements inside the targeted text.  It will only replace
     * the starting tag - the closing tag will remain, but will be unable
     * to have a running script.
     */
    public static String cleanScript(String input) {
        Matcher matcher = scriptPattern.matcher(input);
        return matcher.replaceAll("SCRIPT_STARTING_TAG");
    }
}
