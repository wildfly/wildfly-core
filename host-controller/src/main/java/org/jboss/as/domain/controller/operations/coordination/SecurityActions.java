/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations.coordination;

import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedAction;

import org.jboss.as.controller.AccessAuditContext;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Security actions for the 'org.jboss.as.domain.controller.operations.coordination' package.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
class SecurityActions {

    static AccessAuditContext currentAccessAuditContext() {
        return createAccessAuditContextActions().currentContext();
    }

    private static AccessAuditContextActions createAccessAuditContextActions() {
        return WildFlySecurityManager.isChecking() ? AccessAuditContextActions.PRIVILEGED : AccessAuditContextActions.NON_PRIVILEGED;
    }

    private interface AccessAuditContextActions {

        AccessAuditContext currentContext();

        AccessAuditContextActions NON_PRIVILEGED = new AccessAuditContextActions() {

            @Override
            public AccessAuditContext currentContext() {
                return AccessAuditContext.currentAccessAuditContext();
            }
        };

        AccessAuditContextActions PRIVILEGED = new AccessAuditContextActions() {

            private final PrivilegedAction<AccessAuditContext> PRIVILEGED_ACTION = new PrivilegedAction<AccessAuditContext>() {

                @Override
                public AccessAuditContext run() {
                    return NON_PRIVILEGED.currentContext();
                }

            };

            @Override
            public AccessAuditContext currentContext() {
                return doPrivileged(PRIVILEGED_ACTION);
            }
        };

    }
}
