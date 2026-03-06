package com.renomad.inmra.featurelogic.version;

import com.renomad.inmra.auth.*;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.inmra.utils.NavigationHeader;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.web.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.assertEquals;

/**
 * Examine the behavior of the class for endpoints dealing with versioning
 */
public class VersioningTests {

    private Context context;
    private MemoriaContext memoriaContext;
    private NavigationHeader navigationHeader;

    @Before
    public void init() {
        this.context = TestFramework.buildTestingContext("versioningtests");
        memoriaContext = MemoriaContext.buildMemoriaContext(context);
        AuthHeader authHeader = new AuthHeader(memoriaContext);
        navigationHeader = new NavigationHeader(memoriaContext, authHeader);
    }

    @After
    public void cleanup() {
        TestFramework.shutdownTestingContext(context);
    }

    /**
     * If the user is unauthenticated, they will get a response
     * indicating they are forbidden
     */
    @Test
    public void testUnauthenticated() {
        IAuthUtils authUtils = mockAuthUtils(false, null);
        var versioning = new Versioning(authUtils, context, memoriaContext.getFileUtils(), navigationHeader);
        var request = makeRequest(Map.of());
        IResponse response = versioning.versionGet(request);
        assertEquals(response, authUtils.htmlForbidden());
    }

    /**
     * The endpoint needs an id to do anything valuable
     */
    @Test
    public void testMissingId() {
        IAuthUtils authUtils = mockAuthUtils(true, null);
        var versioning = new Versioning(authUtils, context, memoriaContext.getFileUtils(), navigationHeader);
        var request = makeRequest(Map.of());
        IResponse response = versioning.versionGet(request);
        assertEquals(response, Response.buildLeanResponse(StatusLine.StatusCode.CODE_400_BAD_REQUEST));
    }

    /**
     * If the provided id has no value, the program cannot do anything
     */
    @Test
    public void testEmptyId() {
        IAuthUtils authUtils = mockAuthUtils(true, null);
        var versioning = new Versioning(authUtils, context, memoriaContext.getFileUtils(), navigationHeader);
        var request = makeRequest(Map.of("id", ""));
        IResponse response = versioning.versionGet(request);
        assertEquals(response, Response.buildLeanResponse(StatusLine.StatusCode.CODE_400_BAD_REQUEST));
    }

    private static IRequest makeRequest(Map<String,String> queryStrings) {
        return new IRequest() {
            @Override
            public Headers getHeaders() {
                return null;
            }

            @Override
            public RequestLine getRequestLine() {
                return new RequestLine(RequestLine.Method.GET, new PathDetails("","", queryStrings), HttpVersion.ONE_DOT_ONE, "", null);
            }

            @Override
            public Body getBody() {
                return null;
            }

            @Override
            public String getRemoteRequester() {
                return null;
            }

            @Override
            public ISocketWrapper getSocketWrapper() {
                return null;
            }

            @Override
            public Iterable<UrlEncodedKeyValue> getUrlEncodedIterable() {
                return null;
            }

            @Override
            public Iterable<StreamingMultipartPartition> getMultipartIterable() {
                return null;
            }

            @Override
            public boolean hasAccessedBody() {
                return false;
            }
        };
    }

    private static IAuthUtils mockAuthUtils(boolean isAuthenticated, User user) {
        return new IAuthUtils() {
            @Override
            public AuthResult processAuth(IRequest request) {
                return new AuthResult(isAuthenticated, Instant.MIN, user);
            }

            @Override
            public String getForbiddenPage() {
                return null;
            }

            @Override
            public IResponse htmlForbidden() {
                return Response.buildLeanResponse(StatusLine.StatusCode.CODE_403_FORBIDDEN);
            }

            @Override
            public PrivacyCheckStatus canShowPrivateInformation(IRequest request) {
                return null;
            }
        };
    }
}
