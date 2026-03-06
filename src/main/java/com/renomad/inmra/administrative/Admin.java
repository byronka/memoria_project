package com.renomad.inmra.administrative;

import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.auth.SessionId;
import com.renomad.inmra.featurelogic.persons.Date;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.featurelogic.persons.PersonMetrics;
import com.renomad.inmra.featurelogic.photo.Photograph;
import com.renomad.inmra.featurelogic.photo.Video;
import com.renomad.inmra.utils.*;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.logging.LoggingLevel;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.security.Inmate;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
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
    private final AbstractDb<User> userDb;
    private final AbstractDb<SessionId> sessionDb;
    private final AbstractDb<Person> personDb;
    private final AbstractDb<Photograph> photoDb;
    private final AbstractDb<Video> videoDb;
    private final NavigationHeader navigationHeader;
    private final AbstractDb<PersonMetrics> personMetricsDb;

    public Admin(IAuthUtils authUtils,
                 AbstractDb<User> userDb,
                 AbstractDb<SessionId> sessionDb,
                 Context context,
                 MemoriaContext memoriaContext,
                 AbstractDb<Person> personDb,
                 AbstractDb<Photograph> photoDb,
                 AbstractDb<Video> videoDb,
                 NavigationHeader navigationHeader,
                 AbstractDb<PersonMetrics> personMetricsDb) {
        this.authUtils = authUtils;
        this.userDb = userDb;
        this.sessionDb = sessionDb;
        this.personDb = personDb;
        this.photoDb = photoDb;
        this.videoDb = videoDb;
        this.navigationHeader = navigationHeader;
        this.personMetricsDb = personMetricsDb;
        IFileUtils fileUtils = memoriaContext.getFileUtils();
        this.constants = context.getConstants();
        String template = fileUtils.readTemplate("admin/admin_page_template.html");
        this.adminPageProcessor = TemplateProcessor.buildProcessor(template);
        this.theBrig = context.getFullSystem().getTheBrig();
    }

    public IResponse get(IRequest request) {
        if (! authUtils.processAuth(request).isAuthenticated()) return Response.buildLeanResponse(StatusLine.StatusCode.CODE_403_FORBIDDEN);
        Map<String, String> adminPageValues = new HashMap<>();

        // get all the users
        adminPageValues.put(
                "users",
                userDb.values().stream().map(
                        x -> String.format("<div><b>index:</b> %d <b>name:</b> %s</div>",
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
        adminPageValues.put("live_sessions", "<div>" + StringUtils.safeHtml(sb.toString()) + "</div>");

        // get the inmates from the brig - client ip's that are considered to be attackers.

        String inmates = theBrig == null ? "" : theBrig
                .getInmates().stream()
                .sorted(Comparator.comparingLong(Inmate::getReleaseTime).reversed())
                .map(x -> "<div>" + x.getClientId() + " in jail until " + Instant.ofEpochMilli(x.getReleaseTime()).atZone(ZoneId.systemDefault()).toLocalDateTime() + "</div>")
                .collect(Collectors.joining("\n"));
        adminPageValues.put("inmates", inmates);

        List<LoggingLevel> logLevels = constants.logLevels;
        adminPageValues.put("log_settings", StringUtils.safeHtml(logLevels.toString()));

        String s;
        try {
            s = Files.readString(Path.of("code_status.txt"));
        } catch (IOException ex) {
            s = "Exception while reading code_status.txt. " + ex.getMessage();
        }

        adminPageValues.put("version_info", StringUtils.safeHtml(s));
        String navHeader = navigationHeader.renderNavigationHeader(request, true, true, "");
        adminPageValues.put("navigation_header", navHeader);

        // get the number of photographs
        int photoCount = photoDb.values().size();
        adminPageValues.put("photo_count", String.valueOf(photoCount));

        // get the number of videos
        int videoCount = videoDb.values().size();
        adminPageValues.put("video_count", String.valueOf(videoCount));

        // get the number of persons
        int personCount = personDb.values().size();
        adminPageValues.put("person_count", String.valueOf(personCount));

        // get the number of living people
        long livingPersonCount = personDb.values().stream().filter(x -> x.getDeathday().equals(Date.EMPTY)).count();
        adminPageValues.put("living_person_count", String.valueOf(livingPersonCount));

        // get the number of deceased people
        long deceasedPersonCount = personDb.values().stream().filter(x -> ! x.getDeathday().equals(Date.EMPTY)).count();
        adminPageValues.put("deceased_person_count", String.valueOf(deceasedPersonCount));

        // get the total number of bytes of all biographies in the system
        long totalBioBytesCount = personMetricsDb.values().stream().mapToInt(PersonMetrics::getBioCharCount).sum();
        adminPageValues.put("total_bio_bytes", String.valueOf(totalBioBytesCount));

        String responseBody = adminPageProcessor.renderTemplate(adminPageValues);
        return Respond.htmlOk(responseBody);
    }
}
