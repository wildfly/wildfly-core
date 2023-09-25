/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.access.rbac;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class UnknowRoleException extends RuntimeException {

    public UnknowRoleException() {
    }

    public UnknowRoleException(String message) {
        super(message);
    }

    public UnknowRoleException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnknowRoleException(Throwable cause) {
        super(cause);
    }
}
