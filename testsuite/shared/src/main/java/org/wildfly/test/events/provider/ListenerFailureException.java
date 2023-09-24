/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.events.provider;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class ListenerFailureException extends RuntimeException{

    public ListenerFailureException() {
    }

    public ListenerFailureException(String message) {
        super(message);
    }

    public ListenerFailureException(String message, Throwable cause) {
        super(message, cause);
    }

    public ListenerFailureException(Throwable cause) {
        super(cause);
    }

}
