package com.project.smart_urban.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class KineticExceptionHandler {

    @ExceptionHandler(KineticSectorNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(KineticSectorNotFoundException exception) {
        return new ErrorResponse(Instant.now(), 404, exception.getMessage());
    }

    @ExceptionHandler(InvalidKineticRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidRequest(InvalidKineticRequestException exception) {
        return new ErrorResponse(Instant.now(), 400, exception.getMessage());
    }

    public record ErrorResponse(Instant timestamp, int status, String message) {
    }
}