package com.renomad.inmra.auth;

import com.renomad.minum.web.Request;
import com.renomad.minum.web.Response;

import java.util.regex.Pattern;

public interface IAuthUtils {
    String cookieKey = "sessionid";
    /**
     * Used to extract cookies from the Cookie header
     */
    Pattern sessionIdCookieRegex = Pattern.compile(cookieKey + "=(?<sessionIdValue>\\w+)");

    AuthResult processAuth(Request request);

    String getForbiddenPage();

    Response htmlForbidden();
}
