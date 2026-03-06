package com.renomad.inmra.utils;

import com.renomad.inmra.auth.AuthHeader;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.web.IRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Class responsible for generating a proper navigation header on pages
 */
public class NavigationHeader {
    private final TemplateProcessor navigationHeaderWithSearch;
    private final TemplateProcessor navigationHeader;
    private final AuthHeader authHeader;

    public NavigationHeader(MemoriaContext memoriaContext, AuthHeader authHeader) {
        IFileUtils fileUtils = memoriaContext.getFileUtils();
        this.authHeader = authHeader;
        navigationHeaderWithSearch = TemplateProcessor.buildProcessor(fileUtils.readTemplate("general/navigation_header_with_search.html"));
        navigationHeader = TemplateProcessor.buildProcessor(fileUtils.readTemplate("general/navigation_header.html"));
    }

    /**
     * Generate a navigation header for the top of the page
     * @param isAdminAuthenticated whether the person is currently authenticated
     *                             as an administrator
     * @param isAuthenticatedToSeeLivingPeople whether this person is authenticated
     *                                         to the privacy password
     * @param currentPersonId the current person being viewed, or empty string if no one
     */
    public String renderNavigationHeader(IRequest r, boolean isAdminAuthenticated, boolean isAuthenticatedToSeeLivingPeople, String currentPersonId) {
        return renderNavigationHeader(r, isAdminAuthenticated, isAuthenticatedToSeeLivingPeople, currentPersonId, false, null);
    }

    /**
     * Generate a navigation header which allows adding a search input and "other person identifier"
     * @param isAdminAuthenticated whether the person is currently authenticated
     *                             as an administrator
     * @param isAuthenticatedToSeeLivingPeople whether this person is authenticated
     *                                         to the privacy password
     * @param currentPersonId the current person being viewed, or empty string if no one
     * @param includeSearch whether to include a form for searching persons
     * @param otherPersonId the id of a person for finding connections between people
     */
    public String renderNavigationHeader(
            IRequest r,
            boolean isAdminAuthenticated,
            boolean isAuthenticatedToSeeLivingPeople,
            String currentPersonId,
            boolean includeSearch,
            UUID otherPersonId) {
        String helpLink = String.format("""
                <a id="help" href="/general/%s">ⓘ</a>""", isAdminAuthenticated ? "adminhelp" : "help.html");

        boolean shouldShowPrivateInformation = isAdminAuthenticated || isAuthenticatedToSeeLivingPeople;
        // if the user is authenticated as admin, we won't show the login/logout links
        // since it could be confusing.
        String privacyAuthControl = "";
        if (! isAdminAuthenticated) {
            String isolatedPath = URLEncoder.encode(r.getRequestLine().getPathDetails().getIsolatedPath(), StandardCharsets.UTF_8);
            String queryString = r.getRequestLine().getPathDetails().getRawQueryString();
            String queryStringEncoded = "";
            if (queryString != null && ! queryString.isBlank()) {
                queryStringEncoded = "?" + URLEncoder.encode(queryString, StandardCharsets.UTF_8);
            }
            // the reference back to where we started
            String backref = isolatedPath + queryStringEncoded;
            privacyAuthControl = shouldShowPrivateInformation ?
                    "<a class=\"privacy-auth-control\" id=\"privacy-logout\" href=\"/privacylogout?backref="+ backref +"\">Logout</a>" :
                    "<a class=\"privacy-auth-control\" id=\"privacy-login\" href=\"/privacylogin?backref="+backref+"\">Login</a>";
        }

        String renderedAuthHeader = authHeader.getRenderedAuthHeader(isAdminAuthenticated, currentPersonId);

        Map<String, String> valuesMap = new HashMap<>();
        valuesMap.put("navigation_menu", renderedAuthHeader);
        valuesMap.put("privacy_auth_control", privacyAuthControl);
        valuesMap.put("help_link", helpLink);

        // we include search only above persons' pages, so we can place some links
        // there to pages about a person
        if (includeSearch) {
            valuesMap.put("oid", otherPersonId != null ? otherPersonId.toString() : "");
            if (! shouldShowPrivateInformation) {
                valuesMap.put("link_page_for_printing", "");
            } else {
                valuesMap.put("link_page_for_printing", "<a class=\"print-icon\" href=\"/personprint?id="+currentPersonId+"\"><img width=\"24\" src=\"/person/printer.svg\" title=\"Page for printing\" alt=\"Page for printing\"></a>");
            }

            return navigationHeaderWithSearch.renderTemplate(valuesMap);
        } else {
            return navigationHeader.renderTemplate(valuesMap);
        }

    }
}
