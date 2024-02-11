package com.renomad.inmra.security;

public interface ISecurityUtils {
    // Regarding the BusyWait - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    ISecurityUtils initialize();

    /**
     * Kills the infinite loop running inside this class.
     */
    void stop();

    /**
     * Check if the client seems to be trying to login too quickly,
     * suggesting a scripted brute-force attack.
     */
    boolean isScriptedLogin(String clientAddress);
}
