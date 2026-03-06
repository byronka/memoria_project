package com.renomad.inmra.auth;

import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.web.IRequest;
import com.renomad.minum.web.IResponse;
import com.renomad.minum.web.Response;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.renomad.minum.utils.SearchUtils.findExactlyOne;
import static com.renomad.minum.web.StatusLine.StatusCode.CODE_403_FORBIDDEN;


/**
 * This class provides services for stateful authentication and
 * authorization.
 * <br><br>
 * Interestingly, it uses the underlying web testing similarly
 * to any other domain.  It doesn't really require any special
 * deeper magic.
 */
public class AuthUtils implements IAuthUtils {

    private final ILogger logger;
    private final AbstractDb<User> userDb;
    private final AbstractDb<SessionId> sessionDiskData;
    private final String forbiddenPage;
    private final PrivacyCheck privacyCheck;

    public AuthUtils(AbstractDb<SessionId> sessionDiskData,
                     AbstractDb<User> userDb,
                     Context context,
                     MemoriaContext memoriaContext) {
        IFileUtils fileUtils = memoriaContext.getFileUtils();
        this.userDb = userDb;
        this.sessionDiskData = sessionDiskData;
        this.logger = context.getLogger();
        this.forbiddenPage = fileUtils.readTemplate("forbidden_page.html");
        this.privacyCheck = new PrivacyCheck(memoriaContext.getHashedPrivacyPassword(), this);
    }

    @Override
    public AuthResult processAuth(IRequest request) {
        // grab the headers from the request.
        final var headers = request.getHeaders().getHeaderStrings();

        // get all the headers that start with "cookie", case-insensitive
        final var cookieHeaders = headers.stream()
                .map(String::toLowerCase)
                .filter(x -> x.startsWith("cookie"))
                .collect(Collectors.joining("; "));

        // extract session identifiers from the cookies
        final var cookieMatcher = IAuthUtils.sessionIdCookieRegex.matcher(cookieHeaders);
        String sessionIdValue;
        if (cookieMatcher.find()) {
            sessionIdValue = cookieMatcher.group("sessionIdValue");
        } else {
            sessionIdValue = null;
        }
        if (cookieMatcher.find()) {
            logger.logDebug(() -> "there must be either zero or one session id found " +
                    "in the cookie headers.  Anything more is invalid");
            return new AuthResult(false, null, User.EMPTY);
        }

        // examine whether there is a session identifier
        final var hasSessionId = sessionIdValue != null;

        // if we don't find any sessions in the request, they are not authenticated.
        if (! hasSessionId) {
            return new AuthResult(false, null, User.EMPTY);
        }

        // Did we find that session identifier in the database?
        final SessionId sessionFoundInDatabase = findExactlyOne(
                sessionDiskData.values().stream(),
                x -> Objects.equals(x.getSessionCode().toLowerCase(), sessionIdValue.toLowerCase()),
                () -> SessionId.EMPTY);

        // they are authenticated if we find their session id in the database
        final var isAuthenticated = sessionFoundInDatabase != SessionId.EMPTY;

        if (! isAuthenticated) {
            return new AuthResult(false, Instant.now(), User.EMPTY);
        }

        // find the user
        User authenticatedUser = findExactlyOne(userDb.values().stream(), x -> x.getIndex() == sessionFoundInDatabase.getUserId());

        // update the time we will kill this session
        SessionId updatedSessionId = sessionFoundInDatabase.updateSessionDeadline(Instant.now());
        sessionDiskData.write(updatedSessionId);

        return new AuthResult(true, sessionFoundInDatabase.getCreationDateTime(), authenticatedUser);
    }


    @Override
    public String getForbiddenPage() {
        return forbiddenPage;
    }


    @Override
    public IResponse htmlForbidden() {
        return Response.buildResponse(CODE_403_FORBIDDEN, Map.of("content-type","text/html"), getForbiddenPage());
    }

    @Override
    public PrivacyCheckStatus canShowPrivateInformation(IRequest request) {
        return privacyCheck.canShowPrivateInformation(request);
    }
}
