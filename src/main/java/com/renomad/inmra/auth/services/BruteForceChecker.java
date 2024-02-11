package com.renomad.inmra.auth.services;

import com.renomad.inmra.security.ISecurityUtils;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.security.ITheBrig;

/**
 * This class is responsible for examining the "remote requester" (the ip address
 * of the client) for trying to login too quickly and repeatedly - indicating an attack.
 */
public class BruteForceChecker {

    private final ISecurityUtils ISecurityUtils;
    private final ITheBrig theBrig;
    private final ILogger logger;

    public BruteForceChecker(ISecurityUtils ISecurityUtils, ITheBrig theBrig, ILogger logger) {
        this.ISecurityUtils = ISecurityUtils;
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
        if (ISecurityUtils != null && theBrig != null) {
            boolean isBruteForcingLogin = ISecurityUtils.isScriptedLogin(remoteRequester);
            logger.logDebug(() -> "is " + remoteRequester + " brute-forcing login with scripts? " + isBruteForcingLogin);
            if (isBruteForcingLogin) {
                theBrig.sendToJail(remoteRequester + "_brute_forcing_login", 10 * 1000);
            }
            return theBrig.isInJail(remoteRequester + "_brute_forcing_login");
        } else {
            return false;
        }
    }
}
