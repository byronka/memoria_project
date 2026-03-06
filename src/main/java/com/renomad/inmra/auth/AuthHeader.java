package com.renomad.inmra.auth;

import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.templating.TemplateProcessor;

import java.util.Map;

/**
 * This class is responsible for generating the navigation menu seen at the top of the
 * page when the user is authenticated.
 */
public class AuthHeader {

    private final TemplateProcessor authHeader;

    public AuthHeader(MemoriaContext memoriaContext) {
        IFileUtils fileUtils = memoriaContext.getFileUtils();
        authHeader = TemplateProcessor.buildProcessor(fileUtils.readTemplate("general/auth_header.html"));
    }

    /**
     * handles the processing of the auth header (a navigation menu) - if they are not
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
    public String getRenderedAuthHeader(boolean isAuthenticatedAsAdmin, String personId) {
        String authHeaderRendered;
        if (isAuthenticatedAsAdmin) {
            if (personId != null && ! personId.isBlank()) {
                String extraLink = String.format("""
                        <a id="edit_navigation_link" class="nav-icon" href="/editperson?id=%s"><img src="/general/edit_icon.svg" width="24" alt="Edit" title="Edit"></a>
                        &nbsp;
                        <a id="photo_navigation_link" class="nav-icon" href="/photos?personid=%s"><img src="/general/upload.svg" width="24" alt="Photos" title="Photos"></a>
                        """,
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
