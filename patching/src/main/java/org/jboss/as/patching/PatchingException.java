/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching;


/**
 * @author Emanuel Muckenhuber
 */
public class PatchingException extends Exception {

    private static final long serialVersionUID = 1L;

    public PatchingException() {
        super("patching exception");
    }

    public PatchingException(String message) {
        super(message);
    }

    public PatchingException(String message, Throwable cause) {
        super(message, cause);
    }

    public PatchingException(Throwable cause) {
        super(cause);
    }

    public PatchingException(String format, Object... args) {
        this(String.format(format, args));
    }

    public PatchingException(Throwable cause, String format, Object... args) {
        this(String.format(format, args), cause);
    }
}
