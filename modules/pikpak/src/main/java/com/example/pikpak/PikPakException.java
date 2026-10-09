package com.example.pikpak;

/** Stable module errors; never include remote response bodies or secret URLs. */
public final class PikPakException extends RuntimeException {
    public enum Code { INVALID_INPUT, NOT_FOUND, CONFLICT, PASSWORD_REQUIRED, WRONG_PASSWORD,
        SHARE_UNAVAILABLE, FILE_UNAVAILABLE, MEDIA_UNAVAILABLE, UPSTREAM_UNAVAILABLE,
        UPSTREAM_AUTH_REQUIRED, UPSTREAM_CHALLENGE, UPSTREAM_RATE_LIMITED, INVALID_CURSOR }
    private final Code code;
    public PikPakException(Code code, String message) { super(message); this.code = code; }
    public Code code() { return code; }
}
