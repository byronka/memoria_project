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

    AuthResult processAuth(IRequest request);

    String getForbiddenPage();

    IResponse htmlForbidden();
}
