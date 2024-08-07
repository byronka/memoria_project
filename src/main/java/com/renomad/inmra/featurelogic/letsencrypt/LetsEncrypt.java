package com.renomad.inmra.featurelogic.letsencrypt;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.web.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * This class handles part of the work of dealing with the
 * LetsEncrypt certification renewal process.  It's a bit tricky,
 * and you can read about it here: https://letsencrypt.org/docs/challenge-types/#http-01-challenge
 * <p>
 *     In essence, a program on our production system will contact LetsEncrypt,
 *     telling them about our server.  In turn, their computer will make a
 *     request to us at a well-known location.
 * </p>
 * <p>
 *     Specifically, they will contact us at <pre>GET /.well-known/acme-challenge/SOME_TOKEN_HERE</pre>
 * </p>
 * <p>
 *     The certbot program on our server will put a file at a spot we determine, and when
 *     LetsEncrypt calls in, they will want to read the contents of that file, in a file with a random
 *     name, but specified in the path (as represented by SOME_TOKEN_HERE).  It's a bit
 *     Rube Goldberg, but we'll tell certbot to put the file in /tmp, under .well-known/acme-challenge.
 * </p>
 */
public class LetsEncrypt {

    private static final Pattern badFilePathPatterns = Pattern.compile("//|\\.\\.|:");

    private static final Pattern requestRegex = Pattern.compile(".well-known/acme-challenge/(?<challengeValue>.*$)");
    private final ILogger logger;

    public LetsEncrypt(Context context) {
        this.logger = context.getLogger();
    }

    /**
     * See details at {@link LetsEncrypt}
     */
    public IResponse challengeResponse(IRequest request) {
        logger.logDebug(() -> "Incoming certbot request: " + request);
        // extract session identifiers from the cookies
        final var challengeMatcher = requestRegex.matcher(request.getRequestLine().getPathDetails().getIsolatedPath());
        // When the find command is run, it changes state so we can search by matching group
        if (! challengeMatcher.find()) {
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }
        String tokenFileName = challengeMatcher.group("challengeValue");
        logger.logAudit(() -> "Received acme challenge request for this token: " + tokenFileName);

        if (badFilePathPatterns.matcher(tokenFileName).find()) {
            logger.logDebug(() -> "Received a potentially dangerous token: " + tokenFileName);
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
        }

        Path path = Path.of("");
        try {
            path = new File("/tmp/.well-known/acme-challenge/" + tokenFileName).toPath();
            Path finalPath1 = path;
            logger.logAudit(() -> "Reading a file at " + finalPath1);
            byte[] body = Files.readAllBytes(path);
            logger.logAudit(() -> "Successfully read " + body.length + " bytes from file at " + finalPath1);
            return Response.buildResponse(StatusLine.StatusCode.CODE_200_OK, Map.of("Content-Type", "application/octet-stream"), body);
        } catch (IOException e) {
            Path finalPath = path;
            logger.logDebug(() -> "Failed to read file at " + finalPath);
            return Response.buildLeanResponse(StatusLine.StatusCode.CODE_500_INTERNAL_SERVER_ERROR);
        }
    }
}
