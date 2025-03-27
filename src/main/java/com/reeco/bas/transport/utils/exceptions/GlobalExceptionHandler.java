package com.reeco.bas.transport.utils.exceptions;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BeanCreationException.class)
    public ResponseEntity<String> handleBeanCreationException(BeanCreationException ex) {
        // Log the exception
        System.err.println("Bean creation exception: " + ex.getMessage());
        // Return a custom response
        return new ResponseEntity<>("Bean creation failed", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
