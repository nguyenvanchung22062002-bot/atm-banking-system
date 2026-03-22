package com.atm.util;

public class AppException extends RuntimeException {
    private final int statusCode;

    public AppException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }

    public static class NotFoundException extends AppException {
        public NotFoundException(String msg) { super(404, msg); }
    }

    public static class UnauthorizedException extends AppException {
        public UnauthorizedException(String msg) { super(401, msg); }
    }

    public static class ForbiddenException extends AppException {
        public ForbiddenException(String msg) { super(403, msg); }
    }

    public static class BadRequestException extends AppException {
        public BadRequestException(String msg) { super(400, msg); }
    }

    public static class ConflictException extends AppException {
        public ConflictException(String msg) { super(409, msg); }
    }

    public static class InternalException extends AppException {
        public InternalException(String msg) { super(500, msg); }
    }
}
