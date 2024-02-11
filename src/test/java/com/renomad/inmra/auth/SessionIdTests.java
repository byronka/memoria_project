package com.renomad.inmra.auth;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static com.renomad.inmra.auth.SessionId.createNewSession;
import static com.renomad.minum.testing.TestFramework.assertEquals;

public class SessionIdTests {

    LocalDateTime ldt = LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 1, 1);
    Instant instant1 = ldt.toInstant(ZoneOffset.UTC);
    Instant instant2 = instant1.minus(5, ChronoUnit.MINUTES);

    /**
     * A new session id will have its deletion deadline set.
     */
    @Test
    public void testSessionIdHappyPath() {
        SessionId newSession = createNewSession(1L, 1L, instant1);
        long hoursBetween = ChronoUnit.HOURS.between(newSession.getCreationDateTime(), newSession.getKillDateTime());
        assertEquals(hoursBetween, 36L);
    }

    /**
     * Each time someone carries out an authenticated action, it will extend the deadline
     * for when their session gets deleted.  The deletion time is always one hour past their
     * last authenticated action.
     */
    @Test
    public void testSessionIdKillTimeAdjustment() {
        SessionId newSession = createNewSession(1L, 1L, instant1);
        SessionId updatedSession = newSession.updateSessionDeadline(instant2);
        long hoursBetween = ChronoUnit.MINUTES.between(updatedSession.getKillDateTime(), newSession.getKillDateTime());
        assertEquals(hoursBetween, 5L);
    }

}
