package com.renomad.inmra.utils;

import com.renomad.minum.web.Response;
import com.renomad.minum.web.StatusLine;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.renomad.minum.web.StatusLine.StatusCode.CODE_204_NO_CONTENT;

/**
 * These are helper methods so our system has some sane values
 * in all responses.
 */
public class Respond {

    private Respond() {
        // disallow construction
    }

    public static Response respond(StatusLine.StatusCode statusCode,
                            Map<String, String> extraHeaders,
                            byte[] body) {

        var headers = new HashMap<String, String>();

        // see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Strict-Transport-Security
        // this lets us tell the browser we want to always use secure when possible
        headers.put("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
        headers.putAll(extraHeaders);
        return Response.buildResponse(statusCode, headers, body);
    }

    /**
     * A helper to return an HTML message with a 200 ok status
     */
    public static Response htmlOk(String body) {
        return respond(StatusLine.StatusCode.CODE_200_OK, Map.of("Content-Type", "text/html; charset=UTF-8"), body.getBytes(StandardCharsets.UTF_8));
    }

    public static Response htmlOkNoContent() {
        return respond(CODE_204_NO_CONTENT, Map.of(), new byte[0]);
    }

    /**
     * Returns a 400 user bad request with no further information
     */
    public static Response userInputError() {
        return respond(StatusLine.StatusCode.CODE_400_BAD_REQUEST, Map.of(), new byte[0]);
    }

    /**
     * Returns a 401 unauthorized request with no further information
     */
    public static Response unauthorizedError() {
        return respond(StatusLine.StatusCode.CODE_401_UNAUTHORIZED, Map.of(), new byte[0]);
    }
}
