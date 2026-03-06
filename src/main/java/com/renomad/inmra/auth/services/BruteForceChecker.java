package com.renomad.inmra.auth.services;

import com.renomad.inmra.security.ISecurityUtils;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.security.ITheBrig;

/**
 * This class is responsible for examining the "remote requester" (the ip address
 * of the client) for trying to login too quickly and repeatedly - indicating an attack.
 */
public class BruteForceChecker {

    private final ISecurityUtils securityUtils;
    private final ITheBrig theBrig;
    private final ILogger logger;

    public BruteForceChecker(ISecurityUtils securityUtils, ITheBrig theBrig, ILogger logger) {
        this.securityUtils = securityUtils;
        this.theBrig = theBrig;
        this.logger = logger;
    }


    /**
     * Determine if this remote requester is trying to attack us through some
     * automated brute-force attempt to log in.  If so, put them in "jail" (see {@link com.renomad.minum.security.TheBrig}
     * and return true.
     * @param remoteRequester a string of a client's ip address, such as "123.123.123.123"
     */
    public boolean check(String remoteRequester) {
        if (securityUtils != null && theBrig != null) {
            if (theBrig.isInJail(remoteRequester + "_brute_forcing_login")) {
                theBrig.sendToJail(remoteRequester + "_brute_forcing_login", 360 * 1000L);
            }
            boolean isBruteForcingLogin = securityUtils.isScriptedLogin(remoteRequester);
            logger.logDebug(() -> "is " + remoteRequester + " brute-forcing login with scripts? " + isBruteForcingLogin);
            if (isBruteForcingLogin) {
                theBrig.sendToJail(remoteRequester + "_brute_forcing_login", 10 * 1000L);
            }
            return theBrig.isInJail(remoteRequester + "_brute_forcing_login");
        } else {
            return false;
        }
    }

    /**
     * If this remote requester has failed to log in too many times within a time window,
     * we'll consider them a maybe-attacker and apply a small delay to their actions, for
     * a short time.
     * <br>
     * We don't absolutely know that the user has failed to login - we just know they
     * are continuing to try logging in over and over.  We add the client ip address to
     * a database and count how many times they have tried logging in within a certain
     * time window.
     * <br>
     * The difference between this and {@link #check(String)} is that the other method is
     * primarily looking at how *quickly* the client is making requests.  This one is looking
     * at whether they have failed too often in a time window.  It's very plausible that
     * a real user could fail to enter the password several times.  But in that other
     * method, the timing is very suspicious - if they enter passwords too fast, maybe
     * there's a script involved.
     * @param remoteRequester the string version of the client's ip address
     */
    public boolean checkForFailedLogins(String remoteRequester) {
        if (securityUtils != null && theBrig != null) {
            if (theBrig.isInJail(remoteRequester + "_failed_login_too_many_times")) {
                // if they were already in jail, and are requesting again, tack on more time
                // and return that yes, they are in jail
                theBrig.sendToJail(remoteRequester + "_failed_login_too_many_times", 10 * 1000L);
                return true;
            }
            // check if the user has exceeded some limit of allowable failures in a given time window
            boolean hasFailedLoginTooOften = securityUtils.hasExceededAllowedFailuresInTimeWindow(remoteRequester);

            logger.logDebug(() -> "is " + remoteRequester + " failing to login too often? " + hasFailedLoginTooOften);
            if (hasFailedLoginTooOften) {
                // if they have exceeded a reasonable limit of failures in a time window, send them to jail
                theBrig.sendToJail(remoteRequester + "_failed_login_too_many_times", 10 * 1000L);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}
