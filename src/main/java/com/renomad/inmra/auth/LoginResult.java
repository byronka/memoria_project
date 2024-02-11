package com.renomad.inmra.auth;

public record LoginResult(LoginResultStatus status, SessionId sessionId, User user) { }