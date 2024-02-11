package com.renomad.inmra.auth;

import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.web.Request;

import java.util.Map;

/**
 * This class is responsible for generating the header seen at the top of the
 * page when the user is authenticated.
 */
public class AuthHeader {

    private final TemplateProcessor authHeader;
    private final IAuthUtils auth;

    public AuthHeader(IAuthUtils auth, MemoriaContext memoriaContext) {
        this.auth = auth;
        IFileUtils fileUtils = memoriaContext.fileUtils();
        authHeader = TemplateProcessor.buildProcessor(fileUtils.readTemplate("general/auth_header.html"));
    }

    public String getRenderedAuthHeader(Request r) {
        return getRenderedAuthHeader(r, null);
    }

    /**
     * handles the processing of the auth header - if they are not
     * authenticated, the header is just an empty string. If they
     * are auth'd, show a navigation bar.
     * <br>
     * Also, if they are looking at a person's page, if you set
     * the personId value, it will create another navigation element
     * to edit that person.
     * @param personId the UUID string of a person.  If this is blank
     *               or null, we'll simply not include the "edit" link.
     *               Otherwise, we'll create an appropriate link.
     */
    public String getRenderedAuthHeader(Request r, String personId) {
        AuthResult authResult = this.auth.processAuth(r);
        String authHeaderRendered;
        if (authResult.isAuthenticated()) {
            if (personId != null && ! personId.isBlank()) {
                String extraLink = String.format("""
                        <li>
                            <a href="/editperson?id=%s">Edit Person</a>
                        </li>
                        <li>
                            <a href="/photos?personid=%s">Edit Photos</a>
                        </li>
                        <li>
                            <a href="/editpersons?id=%s">In list</a>
                        </li>
                        """,
                        personId,
                        personId,
                        personId
                );
                authHeaderRendered = authHeader.renderTemplate(Map.of("edit_this_person", extraLink));
            } else {
                authHeaderRendered = authHeader.renderTemplate(Map.of("edit_this_person", ""));
            }
        } else {
            authHeaderRendered = "";
        }
        return authHeaderRendered;
    }
}
