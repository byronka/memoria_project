package com.renomad.inmra.auth;

import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.logging.LoggingLevel;
import com.renomad.minum.utils.TimeUtils;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * This class starts an infinite loop when the application begins,
 * reviewing the users and sessions.
 * <br>
 * Each user may have an optional session, recorded in their data.
 * We grab all those values, and compare that to what is in the total list
 * of sessions.  Sessions that aren't bound to a user will get deleted.
 */
public class LoopingSessionReviewing {

    private final ExecutorService es;
    private final ILogger logger;
    private final int sleepTime;
    private final AuthPages au;
    private final Constants constants;

    public LoopingSessionReviewing(Context context, AuthPages au) {
        this.es = context.getExecutorService();
        this.logger = context.getLogger();
        this.constants = context.getConstants();
        this.au = au;
        // wake up once an hour
        this.sleepTime = 60 * 60 * 1000;
    }

    /**
     * This kicks off the infinite loop examining the session table
     */
    // Regarding the BusyWait - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    @SuppressWarnings({"BusyWait"})
    public void initialize() {
        logger.logDebug(() -> "Initializing LoopingSessionReviewing main loop");
        Callable<Object> innerLoopThread = () -> {
            Thread.currentThread().setName("LoopingSessionReviewing");
            while (true) {
                try {
                    List<SessionId> sessions = au.getSessions();
                    var sessionsToKill = determineSessionsToKill(sessions);
                    for (SessionId s : sessionsToKill) {
                        logger.logDebug(() -> String.format("Session %d for user %d has expired, deleting", s.getIndex(), s.getUserId()));
                        au.deleteSession(s);
                    }
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ex) {

                    /*
                    this is what we expect to happen.
                    once this happens, we just continue on.
                    this only gets called when we are trying to shut everything
                    down cleanly
                     */

                    if (constants.logLevels.contains(LoggingLevel.DEBUG)) System.out.printf(TimeUtils.getTimestampIsoInstant() + " LoopingSessionReviewing is stopped.%n");
                    return null;
                } catch (Exception ex) {
                    System.out.printf(TimeUtils.getTimestampIsoInstant() + " ERROR: LoopingSessionReviewing has stopped unexpectedly. error: %s%n", ex);
                    throw ex;
                }
            }
        };
        es.submit(innerLoopThread);
    }

    /**
     * Determine which old sessions to kill based on what the users have
     * as their live sessions
     * <br>
     * See <a href="https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html">Session Cheat Sheet</a>
     * <br>
     * See {@link SessionId#updateSessionDeadline(Instant)}
     */
    public static List<SessionId> determineSessionsToKill(List<SessionId> sessions) {
        return sessions.stream().filter(x -> x.getKillDateTime().isBefore(Instant.now())).toList();
    }

}
