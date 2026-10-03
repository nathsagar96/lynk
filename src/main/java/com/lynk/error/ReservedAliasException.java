package com.lynk.error;

import java.util.Collection;

/**
 * The requested custom alias is well-formed but reserved: it would shadow an application or
 * framework route at the root of the redirect mapping.
 */
public class ReservedAliasException extends RuntimeException {

    public ReservedAliasException(String alias, Collection<String> reserved) {
        super("The alias '" + alias + "' is reserved. Reserved aliases: " + String.join(", ", reserved) + ".");
    }
}
