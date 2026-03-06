package com.renomad.inmra.auth;

import com.renomad.minum.web.IRequest;
import com.renomad.minum.web.IResponse;

import java.util.regex.Pattern;

public interface IAuthUtils {
    String cookieKey = "sessionid";
    /**
     * Used to extract cookies from the Cookie header
     */
    Pattern sessionIdCookieRegex = Pattern.compile(cookieKey + "=(?<sessionIdValue>\\w+)");

    /**
     * Processes the request and returns a {@link AuthResult} object.
     * <br><br>
     * More concretely, searches the cookie header in the list of headers
     * of the request and sees if that corresponds to a valid session
     * in our database.  The object returned (the {@link AuthResult} object) should
     * have all necessary information for use by domain code:
     * <ol>
     * <li>do we know this user? (Authentication)</li>
     * <li>Are they permitted to access this specific data? (Authorization)</li>
     * <li>etc...</li>
     * </ol>
     */
    AuthResult processAuth(IRequest request);

    /**
     * Returns HTML to show the user they are forbidden from
     * a particular action
     */
    String getForbiddenPage();

    /**
     * Returns a message to the user that their action is forbidden
     */
    IResponse htmlForbidden();

    /**
     * Returns whether this request is allowed to see the information of
     * living people, which is private and only available to authenticated users.
     * Returns a record holding pertinent information.
     */
    PrivacyCheckStatus canShowPrivateInformation(IRequest request);
}
