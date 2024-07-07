package com.renomad.inmra.auth;

import com.renomad.inmra.auth.services.BruteForceChecker;
import com.renomad.inmra.security.ISecurityUtils;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.inmra.utils.Respond;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.CryptoUtils;
import com.renomad.minum.utils.StringUtils;
import com.renomad.minum.web.Request;
import com.renomad.minum.web.Response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.renomad.inmra.auth.IAuthUtils.cookieKey;
import static com.renomad.inmra.auth.RegisterResultStatus.ALREADY_EXISTING_USER;
import static com.renomad.minum.utils.SearchUtils.findExactlyOne;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

public class AuthPages {

    private final ILogger logger;
    private final Db<User> userDb;
    private final Db<SessionId> sessionDiskData;
    private final String loginPageTemplate;
    private final String logoutPageTemplate;
    private final TemplateProcessor registerPageTemplate;
    private final TemplateProcessor resetPasswordTemplate;
    private final Constants constants;
    private final IAuthUtils authUtils;
    private final AuthHeader authHeader;
    private final BruteForceChecker bruteForceChecker;


    public AuthPages(IAuthUtils authUtils,
                     AuthHeader authHeader,
                     Db<SessionId> sessionDiskData,
                     Db<User> userDb,
                     Context context,
                     MemoriaContext memoriaContext,
                     ISecurityUtils securityUtils) {
        this.authUtils = authUtils;
        this.authHeader = authHeader;
        IFileUtils fileUtils = memoriaContext.fileUtils();
        this.constants = context.getConstants();
        this.userDb = userDb;
        this.sessionDiskData = sessionDiskData;
        this.logger = context.getLogger();

        ITheBrig theBrig;
        if (context.getFullSystem() != null) {
            theBrig = context.getFullSystem().getTheBrig();
        } else {
            theBrig = null;
        }

        loginPageTemplate = fileUtils.readTemplate("auth/login_page_template.html");
        logoutPageTemplate = fileUtils.readTemplate("auth/logout_page_template.html");
        registerPageTemplate = TemplateProcessor.buildProcessor(fileUtils.readTemplate("auth/register_page_template.html"));
        resetPasswordTemplate = TemplateProcessor.buildProcessor(fileUtils.readTemplate("auth/reset_user_password_template.html"));
        this.bruteForceChecker = new BruteForceChecker(securityUtils, theBrig, logger);

    }

    public List<SessionId> getSessions() {
        return sessionDiskData.values().stream().toList();
    }

    public void deleteSession(SessionId s) {
        sessionDiskData.delete(s);
    }

    /**
     * Given a new username and password, create a new user
     */
    public RegisterResult registerUserPost(String newUsername, String newPassword) {
        if (userDb.values().stream().anyMatch(x -> x.getUsername().equals(newUsername))) {
            return new RegisterResult(ALREADY_EXISTING_USER, User.EMPTY);
        }
        final var newSalt = StringUtils.generateSecureRandomString(10);
        final var hashedPassword = CryptoUtils.createPasswordHash(newPassword, newSalt);
        final var newUser = new User(0L, newUsername, hashedPassword, newSalt);
        userDb.write(newUser);
        return new RegisterResult(RegisterResultStatus.SUCCESS, newUser);
    }

    /**
     * Look for a user by their name.  If we find the user, call to
     * {@link #passwordCheck(User, String)} with the password. If
     * we succeed there, we'll build a valid {@link LoginResult}
     * with a new session.
     * <p>
     *     If we don't find a user, return NO_USER_FOUND.
     *     If we find more than one user, something is broken.
     * </p>
     */
    LoginResult findUser(String username, String password) {
        User user = findExactlyOne(userDb.values().stream(), x -> x.getUsername().equals(username));
        if (user == null) {
            return new LoginResult(LoginResultStatus.NO_USER_FOUND, SessionId.EMPTY, user);
        } else {
            return passwordCheck(user, password);
        }
    }

    /**
     * Given a user and password, check it's a valid password for that user.
     */
    private LoginResult passwordCheck(User user, String password) {
        final var hash = CryptoUtils.createPasswordHash(password, user.getSalt());
        if (user.getHashedPassword().equals(hash)) {
            SessionId newSession = SessionId.createNewSession(0, user.getIndex(), Instant.now());
            sessionDiskData.write(newSession);
            return new LoginResult(LoginResultStatus.SUCCESS, newSession, user);
        } else {
            return new LoginResult(LoginResultStatus.DID_NOT_MATCH_PASSWORD, SessionId.EMPTY, User.EMPTY);
        }
    }

    /**
     * removes the given user's session from the list. Updates
     * the user to have a null session value.
     */
    private void logoutUser(User user) {
        final List<SessionId> userSessions = sessionDiskData.values().stream().filter(x -> x.getUserId() == user.getIndex()).toList();

        for (SessionId s : userSessions) {
            sessionDiskData.delete(s);
        }
    }


    /**
     * Handles a POST request where a user is trying to
     * login to the system.  If they're doing this, it's
     * presumed they don't have current access to a cookie
     * connected to a SessionID, even if they are logged in
     * elsewhere.  That's ok, we have a tool, {@link LoopingSessionReviewing},
     * that will clear out stale sessions.
     */
    public Response loginUserPost(Request r) {
        boolean isBruteForcing = bruteForceChecker.check(r.remoteRequester());
        if (isBruteForcing) return Response.buildLeanResponse(CODE_429_TOO_MANY_REQUESTS);

        // if already authenticated, send them to the index
        if (authUtils.processAuth(r).isAuthenticated()) {
            return Response.redirectTo("/");
        }

        final var username = r.body().asString("username");
        final var password = r.body().asString("password");
        final LoginResult loginResult = findUser(username, password);

        return switch (loginResult.status()) {
            case SUCCESS -> {
                logger.logAudit(() -> String.format("Successful user login for: %s, id: %s", loginResult.user().getUsername(), loginResult.user().getIndex()));
                yield Response.buildLeanResponse(CODE_303_SEE_OTHER, Map.of(
                        "Location","index",
                        "Set-Cookie","%s=%s; Secure; HttpOnly; Domain=%s".formatted(cookieKey, loginResult.sessionId().getSessionCode(), constants.hostName)));
            }
            case DID_NOT_MATCH_PASSWORD -> {
                logger.logDebug(() -> "Failed login for user named: " + username);
                yield Response.buildResponse(CODE_403_FORBIDDEN, Map.of("Content-Type","text/plain"), "Invalid account credentials");
            }
            case NO_USER_FOUND -> {
                logger.logDebug(() -> "login attempted, but no user named: " + username);
                yield Response.buildResponse(CODE_403_FORBIDDEN, Map.of("Content-Type","text/plain"), "Invalid account credentials");
            }
        };
    }


    /**
     * Returns the login page.
     * <p>
     *     If the user is already authenticated, redirect them to
     *     the root.
     * </p>
     */
    public Response loginGet(Request request) {
        AuthResult authResult = authUtils.processAuth(request);
        if (authResult.isAuthenticated()) {
            return Response.redirectTo("/");
        }
        return Respond.htmlOk(loginPageTemplate);
    }


    public Response registerUserPost(Request r) {
        final var authResult = authUtils.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return authUtils.htmlForbidden();
        }
        final var username = r.body().asString("username");
        final var password = r.body().asString("password");
        logger.logAudit(() -> String.format(
                "%s, id: %d is registering a new user, %s",
                authResult.user().getUsername(),
                authResult.user().getIndex(),
                username));
        final var registrationResult = registerUserPost(username, password);

        if (registrationResult.status() == ALREADY_EXISTING_USER) {
            logger.logAudit(() -> String.format("registration for %s failed - already registered", username));
            return Response.buildResponse(
                    CODE_401_UNAUTHORIZED,
                    Map.of("content-type","text/html"),
                    "<p>This user is already registered</p><p><a href=\"/\">Index</a></p>");
        }
        return Response.buildLeanResponse(CODE_303_SEE_OTHER, Map.of("Location","/auth/registered.html"));

    }

    public Response registerGet(Request request) {
        AuthResult authResult = authUtils.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return authUtils.htmlForbidden();
        }
        String renderedAuthHeader = authHeader.getRenderedAuthHeader(request);
        Map<String, String> templateValues = Map.of("header", renderedAuthHeader);
        String renderedTemplate = registerPageTemplate.renderTemplate(templateValues);
        return Response.htmlOk(renderedTemplate);
    }

    /**
     * Logout the user and redirect them.
     * <p>
     *     If the user is not authenticated, do nothing
     *     If they are authenticated, log them out and redirect them.
     *     Both redirects go to the same place.
     * </p>
     */
    public Response logoutPost(Request request) {
        final var authResult = authUtils.processAuth(request);
        if (authResult.isAuthenticated()) {
            User user = authResult.user();
            logger.logAudit(() -> "User logged out - named: " + user.getUsername() + " id: " + user.getIndex());
            logoutUser(user);
        }
        return Response.redirectTo("loggedout");
    }

    public Response loggedoutGet(Request request) {
        return Respond.htmlOk(logoutPageTemplate);
    }

    public Response resetUserPasswordGet(Request request) {
        final var authResult = authUtils.processAuth(request);
        if (authResult.isAuthenticated()) {
            String newPassword = StringUtils.generateSecureRandomString(20);
            Map<String, String> templateValues = Map.of(
                    "newpassword", newPassword,
                    "header", authHeader.getRenderedAuthHeader(request)
            );
            String renderedTemplate = resetPasswordTemplate.renderTemplate(templateValues);
            return Respond.htmlOk(renderedTemplate);
        } else {
            return authUtils.htmlForbidden();
        }

    }

    public Response resetUserPasswordPost(Request request) {
        final var authResult = authUtils.processAuth(request);
        if (!authResult.isAuthenticated()) {
            return Respond.unauthorizedError();
        }

        String newPassword = request.body().asString("newpassword");
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 12) {
            logger.logDebug(() ->
                    String.format("newpassword did not meet requirements. user: %s userid: %d value: %s",
                            authResult.user().getUsername(),
                            authResult.user().getIndex(),
                            newPassword));
            return Respond.userInputError();
        }
        // salting and hashing that delicious password
        final var newSalt = StringUtils.generateSecureRandomString(10);
        final var hashedPassword = CryptoUtils.createPasswordHash(newPassword, newSalt);

        // write the updated salted password to the database
        final var updatedUser = new User(
                authResult.user().getIndex(),
                authResult.user().getUsername(),
                hashedPassword,
                newSalt);
        userDb.write(updatedUser);

        return Response.redirectTo("/auth/passwordchanged.html");
    }
}
