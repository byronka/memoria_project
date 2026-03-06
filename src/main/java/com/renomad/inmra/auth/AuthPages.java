package com.renomad.inmra.auth;

import com.renomad.inmra.auth.services.BruteForceChecker;
import com.renomad.inmra.security.ISecurityUtils;
import com.renomad.inmra.utils.*;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.CryptoUtils;
import com.renomad.minum.utils.StringUtils;
import com.renomad.minum.web.IRequest;
import com.renomad.minum.web.IResponse;
import com.renomad.minum.web.Response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.renomad.inmra.auth.IAuthUtils.cookieKey;
import static com.renomad.inmra.auth.PrivacyCheck.PRIVACY_KEY;
import static com.renomad.inmra.auth.RegisterResultStatus.ALREADY_EXISTING_USER;
import static com.renomad.minum.utils.SearchUtils.findExactlyOne;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

public class AuthPages {

    public static final String PRIVACY_PASSWORD_SALT = "this_is_my_salt";
    private final ILogger logger;
    private final AbstractDb<User> userDb;
    private final AbstractDb<SessionId> sessionDiskData;
    private final TemplateProcessor loginPageTemplate;
    private final String logoutPageTemplate;
    private final TemplateProcessor registerPageTemplate;
    private final TemplateProcessor resetPasswordTemplate;
    private final TemplateProcessor privacyLoginTemplate;
    private final TemplateProcessor privacyLogoutTemplate;
    private final Constants constants;
    private final IAuthUtils authUtils;
    private final BruteForceChecker bruteForceChecker;
    private final MemoriaContext memoriaContext;
    private final Auditor auditor;
    private final NavigationHeader navigationHeader;


    public AuthPages(IAuthUtils authUtils,
                     AbstractDb<SessionId> sessionDiskData,
                     AbstractDb<User> userDb,
                     Context context,
                     MemoriaContext memoriaContext,
                     ISecurityUtils securityUtils,
                     NavigationHeader navigationHeader) {
        this.authUtils = authUtils;
        this.memoriaContext = memoriaContext;
        this.auditor = memoriaContext.getAuditor();
        IFileUtils fileUtils = memoriaContext.getFileUtils();
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

        loginPageTemplate = TemplateProcessor.buildProcessor(fileUtils.readTemplate("auth/login_page_template.html"));
        logoutPageTemplate = fileUtils.readTemplate("auth/logout_page_template.html");
        registerPageTemplate = TemplateProcessor.buildProcessor(fileUtils.readTemplate("auth/register_page_template.html"));
        resetPasswordTemplate = TemplateProcessor.buildProcessor(fileUtils.readTemplate("auth/reset_user_password_template.html"));
        privacyLoginTemplate = TemplateProcessor.buildProcessor(fileUtils.readTemplate("auth/privacy_login_page_template.html"));
        privacyLogoutTemplate = TemplateProcessor.buildProcessor(fileUtils.readTemplate("auth/privacy_logout_page_template.html"));
        this.bruteForceChecker = new BruteForceChecker(securityUtils, theBrig, logger);
        this.navigationHeader = navigationHeader;

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
    public IResponse loginUserPost(IRequest r) {
        boolean isBruteForcing = bruteForceChecker.check(r.getRemoteRequester());
        boolean hasFailedLoginTooManyTimes = bruteForceChecker.checkForFailedLogins(r.getRemoteRequester());
        if (isBruteForcing || hasFailedLoginTooManyTimes) return Response.buildLeanResponse(CODE_429_TOO_MANY_REQUESTS);

        // if already authenticated, send them to the index
        if (authUtils.processAuth(r).isAuthenticated()) {
            return Respond.redirectTo("/");
        }

        final var username = r.getBody().asString("username");
        final var password = r.getBody().asString("password");
        final LoginResult loginResult = findUser(username, password);

        return switch (loginResult.status()) {
            case SUCCESS -> {
                auditor.audit(() -> String.format("Successful user login for: %s, id: %s", loginResult.user().getUsername(), loginResult.user().getIndex()), loginResult.user());
                yield Response.buildLeanResponse(CODE_303_SEE_OTHER, Map.of(
                        "Location","index",
                        "Set-Cookie","%s=%s; Secure; HttpOnly; Domain=%s".formatted(cookieKey, loginResult.sessionId().getSessionCode(), constants.hostName)));
            }
            case DID_NOT_MATCH_PASSWORD -> {
                logger.logDebug(() -> "Failed login for user named: " + username);
                yield Respond.redirectTo("/login?error=true");
            }
            case NO_USER_FOUND -> {
                logger.logDebug(() -> "login attempted, but no user named: " + username);
                yield Respond.redirectTo("/login?error=true");
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
    public IResponse loginGet(IRequest request) {
        AuthResult authResult = authUtils.processAuth(request);
        if (authResult.isAuthenticated()) {
            return Respond.redirectTo("/");
        }
        String error = request.getRequestLine().queryString().get("error");
        Map<String, String> valuesMap = Map.of(
                "error",  error == null ? "" : "Invalid credentials");
        return Respond.htmlOk(loginPageTemplate.renderTemplate(valuesMap));
    }


    public IResponse registerUserPost(IRequest r) {
        final var authResult = authUtils.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return authUtils.htmlForbidden();
        }
        final var username = r.getBody().asString("username");
        final var password = r.getBody().asString("password");
        auditor.audit(() -> String.format(
                "%s, id: %d is registering a new user, %s",
                authResult.user().getUsername(),
                authResult.user().getIndex(),
                username), authResult.user());
        final var registrationResult = registerUserPost(username, password);

        if (registrationResult.status() == ALREADY_EXISTING_USER) {
            auditor.audit(() -> String.format("registration for %s failed - already registered", username), authResult.user());
            return Response.buildResponse(
                    CODE_401_UNAUTHORIZED,
                    Map.of("content-type","text/html"),
                    "<p>This user is already registered</p><p><a href=\"/\">Index</a></p>");
        }
        return Response.buildLeanResponse(CODE_303_SEE_OTHER, Map.of("Location","/auth/registered.html"));

    }

    public IResponse registerGet(IRequest request) {
        AuthResult authResult = authUtils.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return authUtils.htmlForbidden();
        }
        String renderedAuthHeader = navigationHeader.renderNavigationHeader(request, true, true, "");
        Map<String, String> templateValues = Map.of("navigation_header", renderedAuthHeader);
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
    public IResponse logoutPost(IRequest request) {
        final var authResult = authUtils.processAuth(request);
        if (authResult.isAuthenticated()) {
            User user = authResult.user();
            auditor.audit(() -> "User logged out - named: " + user.getUsername() + " id: " + user.getIndex(), user);
            logoutUser(user);
        }
        return Respond.redirectTo("loggedout");
    }

    public IResponse loggedoutGet(IRequest request) {
        return Respond.htmlOk(logoutPageTemplate);
    }

    public IResponse resetUserPasswordGet(IRequest request) {
        final var authResult = authUtils.processAuth(request);
        if (authResult.isAuthenticated()) {
            String newPassword = StringUtils.generateSecureRandomString(20);
            String myNavHeader = navigationHeader.renderNavigationHeader(request, true, true, "");
            Map<String, String> templateValues = Map.of(
                    "newpassword", newPassword,
                    "navigation_header", myNavHeader
            );
            String renderedTemplate = resetPasswordTemplate.renderTemplate(templateValues);
            return Respond.htmlOk(renderedTemplate);
        } else {
            return authUtils.htmlForbidden();
        }

    }

    public IResponse resetUserPasswordPost(IRequest request) {
        final var authResult = authUtils.processAuth(request);
        if (!authResult.isAuthenticated()) {
            return Respond.unauthorizedError();
        }

        String newPassword = request.getBody().asString("newpassword");
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

        auditor.audit(() -> String.format("user %s has reset their password", authResult.user().getUsername()), authResult.user());

        return Respond.redirectTo("/auth/passwordchanged.html");
}

    /**
     * Returns the page for entering a password, in order to view private content
     */
    public IResponse privacyLoginGet(IRequest request) {
        String backref = request.getRequestLine().queryString().get("backref");
        String error = request.getRequestLine().queryString().get("error");
        Map<String, String> valuesMap = Map.of(
                "backref", backref == null ? "" : StringUtils.safeAttr(backref),
                "error",  error == null ? "" : "Invalid password");
        String template = privacyLoginTemplate.renderTemplate(valuesMap);
        return Respond.htmlOk(template);
    }

    /**
     * Receives the password, providing a cookie for access to private data, and
     * returning the user to the referencing page.
     */
    public IResponse privacyLoginPost(IRequest request) {
        boolean isBruteForcing = bruteForceChecker.check(request.getRemoteRequester());
        boolean hasFailedLoginTooManyTimes = bruteForceChecker.checkForFailedLogins(request.getRemoteRequester());
        if (isBruteForcing || hasFailedLoginTooManyTimes) return Response.buildLeanResponse(CODE_429_TOO_MANY_REQUESTS);
        String backref = request.getBody().asString("backref");
        // convert to ascii because headers must be ascii (not UTF-8) and we're about to use this in the location header
        String asciiBackRef = Cleaners.utf8ToAscii(backref);
        String password = request.getBody().asString("password");
        String passwordHash = CryptoUtils.createPasswordHash(password, PRIVACY_PASSWORD_SALT);
        if (passwordHash.equals(memoriaContext.getHashedPrivacyPassword())) {
            logger.logAudit(() -> String.format("%s has entered the privacy password", request.getRemoteRequester()));
            return Response.buildLeanResponse(CODE_303_SEE_OTHER, Map.of(
                    "location", "/" + asciiBackRef,
                    "Set-Cookie","%s=%s; Secure; HttpOnly; Domain=%s; Max-Age=%d".formatted(PRIVACY_KEY, passwordHash, constants.hostName, memoriaContext.getConstants().PRIVACY_COOKIE_MAX_AGE)));
        } else {
            logger.logAudit(() -> String.format("%s has failed to enter the correct privacy password", request.getRemoteRequester()));
            return Respond.redirectTo("/privacylogin?backref=" + StringUtils.encode(asciiBackRef) + "&error=true");
        }
    }

    /**
     * Returns the page for clearing the privacy cookie and informing the user
     */
    public IResponse privacyLogoutGet(IRequest request) {
        String backref = request.getRequestLine().queryString().get("backref");
        String template = privacyLogoutTemplate.renderTemplate(Map.of("backref", backref == null ? "" : StringUtils.safeAttr(backref)));
        logger.logAudit(() -> String.format("%s has removed their privacy password", request.getRemoteRequester()));
        return Response.buildResponse(CODE_200_OK, Map.of("Content-Type", "text/html; charset=UTF-8", "Set-Cookie","%s=%s; Secure; HttpOnly; Domain=%s; Max-Age=%d".formatted(PRIVACY_KEY, "removing_this_cookie", constants.hostName, 0)), template);
    }
}
