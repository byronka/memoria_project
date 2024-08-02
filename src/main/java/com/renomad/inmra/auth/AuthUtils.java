package com.renomad.inmra.auth;

import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.web.IRequest;
import com.renomad.minum.web.IResponse;
import com.renomad.minum.web.Response;

import java.time.Instant;
import java.util.ArrayList;
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
    private final Db<User> userDb;
    private final Db<SessionId> sessionDiskData;
    private final Constants constants;
    private final String forbiddenPage;

    public AuthUtils(Db<SessionId> sessionDiskData,
                     Db<User> userDb,
                     Context context,
                     MemoriaContext memoriaContext) {
        this.constants = context.getConstants();
        IFileUtils fileUtils = memoriaContext.fileUtils();
        this.userDb = userDb;
        this.sessionDiskData = sessionDiskData;
        this.logger = context.getLogger();
        this.forbiddenPage = fileUtils.readTemplate("forbidden_page.html");
    }

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
        final var listOfSessionIds = new ArrayList<String>();
        for(int i = 0; cookieMatcher.find() && i < 5; i++) {
            final var sessionIdValue = cookieMatcher.group("sessionIdValue");
            listOfSessionIds.add(sessionIdValue);
        }
        if (listOfSessionIds.size() >= 2) {
            logger.logDebug(() -> "there must be either zero or one session id found " +
                    "in the request headers.  Anything more is invalid");
            return new AuthResult(false, null, User.EMPTY);
        }

        // examine whether there is just one session identifier
        final var isExactlyOneSessionInRequest = listOfSessionIds.size() == 1;

        // if we don't find any sessions in the request, they are not authenticated.
        if (! isExactlyOneSessionInRequest) {
            return new AuthResult(false, null, User.EMPTY);
        }

        // Did we find that session identifier in the database?
        final SessionId sessionFoundInDatabase = findExactlyOne(
                sessionDiskData.values().stream(),
                x -> Objects.equals(x.getSessionCode().toLowerCase(), listOfSessionIds.get(0).toLowerCase()),
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
}
