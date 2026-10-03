package com.lynk.error;

/**
 * The requested custom alias is already taken.
 */
public class AliasAlreadyExistsException extends RuntimeException {

    public AliasAlreadyExistsException(String alias) {
        super("The custom alias '" + alias + "' is already in use.");
    }
}
