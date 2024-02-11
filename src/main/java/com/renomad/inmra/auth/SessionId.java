package com.renomad.inmra.auth;

import com.renomad.minum.database.DbData;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Objects;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;
import static com.renomad.minum.utils.StringUtils.generateSecureRandomString;

/**
 * A simple record for holding information related to a session. Typically, creation of
 * this should be handled by {@link #createNewSession(long, long, Instant)}
 */
public class SessionId extends DbData<SessionId> {

    private long index;
    private final String sessionCode;
    private final Instant creationDateTime;
    private final Instant killDateTime;
    private final long userId;
    private static final TemporalAmount sessionExtensionTime = Duration.ofHours(36L);

    /**
     * @param index            a simple numeric identifier that lets us distinguish one record from another
     * @param sessionCode      the sessionCode is a randomly-generated string that will be used
     *                         in a cookie value so requests can authenticate.
     * @param creationDateTime the zoned date and time at which this session was created
     * @param killDateTime     the zoned date and time at which this session is slated to be deleted
     */
    public SessionId(long index, String sessionCode, Instant creationDateTime, Instant killDateTime, long userId) {
        this.index = index;
        this.sessionCode = sessionCode;
        this.creationDateTime = creationDateTime;
        this.userId = userId;
        this.killDateTime = killDateTime;
    }

    public static final SessionId EMPTY = new SessionId(
            0,
            "",
            null,
            null,
            0);

    /**
     * Builds a proper session, with a randomly-generated sessionCode and a creation time.  Just provide the index.
     * <br><br>
     * You might be wondering why it is necessary to provide the index.  The reason is that
     * the index is an aspect of this sessionId in the context of being one in a collection.
     * An individual SessionId only knows what its own index is, it doesn't know about its
     * siblings.  For that reason, providing a proper index is the responsibility of the
     * class which manages the whole collection.
     */
    public static SessionId createNewSession(long index, long userId, Instant now) {
        // they get to use this for 1 hour beyond their last authenticated usage
        Instant killTime = now.plus(sessionExtensionTime);
        return new SessionId(
                index,
                generateSecureRandomString(20),
                now,
                killTime,
                userId);
    }


    /**
     * Update the sessionId for a recent usage
     * @param lastUsage the {@link Instant} of the last activity on this session
     * @return a new {@link SessionId} with updated values, meant to replace the old {@link SessionId}
     */
    public SessionId updateSessionDeadline(Instant lastUsage) {
        return new SessionId(
                this.getIndex(),
                this.getSessionCode(),
                this.getCreationDateTime(),
                lastUsage.plus(sessionExtensionTime),
                this.getUserId()
        );
    }

    @Override
    public long getIndex() {
        return index;
    }

    @Override
    protected void setIndex(long index) {
        this.index = index;
    }

    @Override
    public String serialize() {
        return serializeHelper(
                index,
                sessionCode,
                creationDateTime,
                killDateTime,
                userId);
    }

    @Override
    public SessionId deserialize(String serializedText) {
        final var tokens = deserializeHelper(serializedText);

        return new SessionId(
                Long.parseLong(tokens.get(0)),
                tokens.get(1),
                Instant.parse(Objects.requireNonNull(tokens.get(2))),
                Instant.parse(Objects.requireNonNull(tokens.get(3))),
                Long.parseLong(tokens.get(4))
        );
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public Instant getCreationDateTime() {
        return creationDateTime;
    }

    public long getUserId() {
        return userId;
    }

    public Instant getKillDateTime() {
        return killDateTime;
    }
}
