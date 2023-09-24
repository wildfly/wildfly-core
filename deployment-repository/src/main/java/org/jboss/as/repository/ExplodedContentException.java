/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.repository;

/**
 * Exception thrown while manipulating exploded managed content.
 *
 * @author Brian Stansberry
 */
public class ExplodedContentException extends Exception {

    public ExplodedContentException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExplodedContentException(String message) {
        super(message);
    }

    ExplodedContentException(Throwable cause) {
        super(cause);
    }
}
