package com.renomad.inmra.administrative;

import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.auth.SessionId;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.inmra.utils.Respond;
import com.renomad.minum.Constants;
import com.renomad.minum.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.LoggingLevel;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.StringUtils;
import com.renomad.minum.web.*;
import com.renomad.inmra.auth.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.renomad.minum.utils.SearchUtils.findExactlyOne;

public class Admin {
    private final TemplateProcessor adminPageProcessor;
    private final ITheBrig theBrig;
    private final Constants constants;
    private final IAuthUtils authUtils;
    private final Db<User> userDb;
    private final Db<SessionId> sessionDb;
    private final TemplateProcessor authHeader;

    public Admin(IAuthUtils authUtils, Db<User> userDb, Db<SessionId> sessionDb, Context context, MemoriaContext memoriaContext) {
        this.authUtils = authUtils;
        this.userDb = userDb;
        this.sessionDb = sessionDb;
        IFileUtils fileUtils = memoriaContext.fileUtils();
        this.constants = context.getConstants();
        String template = fileUtils.readTemplate("admin/admin_page_template.html");
        authHeader = TemplateProcessor.buildProcessor(fileUtils.readTemplate("general/auth_header.html"));
        this.adminPageProcessor = TemplateProcessor.buildProcessor(template);
        this.theBrig = context.getFullSystem().getTheBrig();
    }

    public Response get(Request request) {
        if (! authUtils.processAuth(request).isAuthenticated()) return new Response(StatusLine.StatusCode._403_FORBIDDEN);
        Map<String, String> adminPageValues = new HashMap<>();

        // get all the users
        adminPageValues.put(
                "users",
                userDb.values().stream().map(
                        x -> String.format("index: %d name: %s",
                                x.getIndex(),
                                x.getUsername())).collect(Collectors.joining("\n")));

        // get the users with live sessions
        Map<Long, Long> usersWithCountOfSessions = sessionDb.values().stream()
                .collect(Collectors.groupingBy(SessionId::getUserId, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Long, Long> e : usersWithCountOfSessions.entrySet()) {
            var user = findExactlyOne(userDb.values().stream(), u -> u.getIndex() == e.getKey(), () -> User.EMPTY);
            if (user != User.EMPTY) {
                sb.append(user.getUsername()).append(" sessions: ").append(e.getValue()).append("\n");
            }
        }
        adminPageValues.put("live_sessions", StringUtils.safeHtml(sb.toString()));

        // get the inmates from the brig - client ip's that are considered to be attackers.
        String inmates = theBrig
                .getInmates().stream()
                .sorted((Map.Entry.comparingByValue(Comparator.reverseOrder())))
                .map(x -> x.getKey() + " in jail until " + Instant.ofEpochMilli(x.getValue()).atZone(ZoneId.systemDefault()).toLocalDateTime())
                .collect(Collectors.joining("\n"));
        adminPageValues.put("inmates", StringUtils.safeHtml(inmates));

        List<LoggingLevel> logLevels = constants.LOG_LEVELS;
        adminPageValues.put("log_settings", StringUtils.safeHtml(logLevels.toString()));

        String s;
        try {
            s = Files.readString(Path.of("code_status.txt"));
        } catch (IOException ex) {
            s = "Exception while reading code_status.txt. " + ex.getMessage();
        }
        adminPageValues.put("version_info", StringUtils.safeHtml(s));
        adminPageValues.put("auth_header", authHeader.renderTemplate(Map.of("edit_this_person", "")));

        String responseBody = adminPageProcessor.renderTemplate(adminPageValues);
        return Respond.htmlOk(responseBody);
    }
}
