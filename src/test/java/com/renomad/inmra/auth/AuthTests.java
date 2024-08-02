package com.renomad.inmra.auth;

import com.renomad.inmra.migrations.DatabaseMigration;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.state.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.web.*;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.assertTrue;
import static com.renomad.minum.testing.TestFramework.buildTestingContext;

public class AuthTests {


    private static Context context;
    private static MemoriaContext memoriaContext;

    @BeforeClass
    public static void init() throws IOException {
        context = buildTestingContext("unit_tests");
        memoriaContext = MemoriaContext.buildMemoriaContext(context);
        new DatabaseMigration(context, memoriaContext).migrate();
    }

    /*
    processAuth is the primary way we analyze the request for
    authentication.  If a user is logged in on, say, their browser
    and their iphone, then the one user will have an association
    with two sessions.
     */
    @Test
    public void test_userAuth_DifferentDevices() {
        Db<SessionId> sessionDb = context.getDb("sessions", SessionId.EMPTY);
        Db<User> userDb = context.getDb("users", User.EMPTY);
        var authUtils = new AuthUtils(sessionDb, userDb, context, memoriaContext);
        var authHeader = new AuthHeader(authUtils, memoriaContext);
        var authPages = new AuthPages(authUtils, authHeader, sessionDb, userDb, context, memoriaContext, null);

        authPages.registerUserPost("bob", "1234").newUser();
        String sessionCode1 = authPages.findUser("bob", "1234").sessionId().getSessionCode();
        String sessionCode2 = authPages.findUser("bob", "1234").sessionId().getSessionCode();

        var request1 = makeRequestWithCookie(sessionCode1);
        var request2 = makeRequestWithCookie(sessionCode2);
        assertTrue(authUtils.processAuth(request1).isAuthenticated());
        assertTrue(authUtils.processAuth(request2).isAuthenticated());
    }

    /**
     * Make a generic {@link Request} with a particular sessionIdValue
     */
    private Request makeRequestWithCookie(String sessionIdValue) {
        Headers headers = new Headers(List.of("Cookie: sessionid=" + sessionIdValue));
        RequestLine requestLine = new RequestLine(RequestLine.Method.GET, new PathDetails("", null, Map.of()), HttpVersion.ONE_DOT_ONE, "GET / HTTP/1.1", context.getLogger());
        return new Request(
                headers,
                requestLine,
                "",
                new FakeSocketWrapper(),
                new FakeBodyProcessor());
    }
}
