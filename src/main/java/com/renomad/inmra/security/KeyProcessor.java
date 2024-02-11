package com.renomad.inmra.security;

import com.renomad.minum.logging.ILogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KeyProcessor {

    private final ILogger logger;

    public KeyProcessor(ILogger logger) {

        this.logger = logger;
    }

    /**
     * Review the keys (ip addresses) to
     * see if any have expired (no longer under consideration of being an attack)
     * see {@link SecurityUtils#isScriptedLogin(String)} for an example of code
     * that uses the data from this method.
     *
     * @param keys                  the list of ip addresses under review
     * @param investigationLifespan the length of time, in milliseconds, we'll hold
     *                              onto a key to determine if there is anything nefarious happening.
     * @param clientsAndTimes       a map of keys to a list of system times (in millis), to aid with analysis.
     * @param now The current system time, in milliseconds
     */
    public void processKeysUnderConsideration(
            Map<String, Long> keys,
            int investigationLifespan,
            Map<String, List<Long>> clientsAndTimes,
            long now) {
        int size = keys.size();
        if (size > 0) {
            logger.logTrace(() -> "SecurityUtils reviewing current investigations. Count: " + size);
        }

        List<String> keysToRemove = new ArrayList<>();
        // figure out which keys are old (and thus should be removed)
        for (var e : keys.entrySet()) {
            // if the key's clock time plus the length of time we'll keep a key
            // under investigation is a total that is less than the current clock time,
            // we can remove that key.  That is, the time to release them from investigation
            // is now in the past.
            if (e.getValue() + investigationLifespan < now) {
                logger.logTrace(() -> "SecurityUtils: " + e.getKey() + " is no longer under investigation");
                keysToRemove.add(e.getKey());
            }
        }
        // it is necessary to delete the keys as a second step to avoid
        // concurrent modification - we don't want to modify the very list
        // we are looping through.
        for (var k : keysToRemove) {
            logger.logTrace(() -> "SecurityUtils: removing " + k + " from investigation");
            keys.remove(k);
            clientsAndTimes.remove(k);
        }
    }
}
