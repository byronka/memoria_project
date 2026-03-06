package com.renomad.inmra.auth;

import com.renomad.minum.web.IRequest;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class holds static helper methods for checking the request for
 * a "privacy" cookie - which is used solely to allow users to see
 * information on living people.
 */
public class PrivacyCheck {

    public final static String PRIVACY_KEY = "privacy-key";
    final static Pattern privacyKeyCookieRegex = Pattern.compile(PRIVACY_KEY + "=(?<privacyKeyValue>\\w+)");
    private final AuthUtils authUtils;
    private final String hashedPrivacyPassword;

    public PrivacyCheck(String hashedPrivacyPassword, AuthUtils authUtils) {
        this.hashedPrivacyPassword = hashedPrivacyPassword;
        this.authUtils = authUtils;
    }

    public static boolean hasValidPrivacyCookie(IRequest request, String hashedPrivacyPassword) {
        // grab the headers from the request.
        final var headers = request.getHeaders().getHeaderStrings();

        // get all the headers that start with "cookie", case-insensitive
        final var cookieHeaders = headers.stream()
                .map(String::toLowerCase)
                .filter(x -> x.startsWith("cookie"))
                .collect(Collectors.joining("; "));

        // extract session identifiers from the cookies
        final var cookieMatcher = privacyKeyCookieRegex.matcher(cookieHeaders);
        if (cookieMatcher.find()) {
            final var privacyKeyValue = cookieMatcher.group("privacyKeyValue");
            // here is where it may return true
            return privacyKeyValue.equals(hashedPrivacyPassword);
        }
        return false;
    }

    public PrivacyCheckStatus canShowPrivateInformation(IRequest request) {
        boolean isPrivacyAuthenticated = hasValidPrivacyCookie(request, this.hashedPrivacyPassword);

        AuthResult authResult = this.authUtils.processAuth(request);
        boolean isAdminAuthenticated = authResult.isAuthenticated();
        boolean canShowPrivateInfo = isAdminAuthenticated || isPrivacyAuthenticated;
        return new PrivacyCheckStatus(
                isAdminAuthenticated, isPrivacyAuthenticated, canShowPrivateInfo, authResult
        );
    }
}
