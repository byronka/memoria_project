package com.renomad.inmra.auth;

/**
 * A record for some fields related to checking authentication and privacy
 * @param isAdminAuthenticated whether this user has a cookie which indicates he has
 *                             successfully authenticated as an administrator.
 * @param isPrivacyAuthenticated whether this user has a cookie which indicates he has
 *                               successfully entered the privacy password, which is shared
 *                               among the family. See {@link com.renomad.inmra.utils.Constants#PRIVACY_PASSWORD}
 * @param canShowPrivateInformation whether this user can see the information of living people, really is
 *                                  simply whether this user is privacy auth'd OR admin auth'd.
 * @param authResult a helper for a couple situations where we already have this data handy, so we
 *                   can easily return the {@link AuthResult} for the use of authenticated navigation headers
 */
public record PrivacyCheckStatus(
        boolean isAdminAuthenticated,
        boolean isPrivacyAuthenticated,
        boolean canShowPrivateInformation,
        AuthResult authResult
) { }
