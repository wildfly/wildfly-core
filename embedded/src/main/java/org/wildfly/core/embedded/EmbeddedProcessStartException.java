/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded;

/**
 * Exception thrown during {@link EmbeddedManagedProcess#start()}.
 *
 * @author Brian Stansberry
 */
public class EmbeddedProcessStartException extends Exception {

    private static final long serialVersionUID = 7991468792402261287L;

    public EmbeddedProcessStartException(String message) {
        super(message);
    }

    public EmbeddedProcessStartException(String message, Throwable cause) {
        super(message, cause);
    }

    public EmbeddedProcessStartException(Throwable cause) {
        super(cause);
    }
}
