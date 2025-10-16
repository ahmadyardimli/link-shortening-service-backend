package com.urlshortener.link_shortening_service.exception;

public class InvalidUrlException extends RuntimeException {
    public InvalidUrlException(String message)
    {
        super(message);
    }
    public InvalidUrlException(String message, Throwable cause) {
        super(message, cause);
    }
}