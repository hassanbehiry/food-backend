package com.food.foodapp.common.exception;

public class MenuItemUnavailableException extends RuntimeException {

    public MenuItemUnavailableException(String message) {
        super(message);
    }
}
