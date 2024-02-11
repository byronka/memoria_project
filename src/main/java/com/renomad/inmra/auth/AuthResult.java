package com.renomad.inmra.auth;

import java.time.Instant;

/**
 * This data structure contains important information about
 * a particular person's authentication.  Like, are they
 * currently authenticated?
 */
public record AuthResult(Boolean isAuthenticated, Instant creationDate, User user) {
}
