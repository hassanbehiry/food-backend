package com.food.foodapp.common.exception;

/** Thrown when a request/query parameter is malformed (bad enum value, out-of-range page/size, etc). */
public class InvalidRequestParameterException extends RuntimeException {

    public InvalidRequestParameterException(String message) {
        super(message);
    }
}
