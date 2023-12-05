/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedAction;

import org.jboss.as.controller.access.InVmAccess;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Security actions for the 'org.jboss.as.controller' package.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SecurityActions {

    private SecurityActions() {
    }

    static boolean isInVmCall() {
        return createInVmActions().isInVmCall();
    }

    static <T> T runInVm(PrivilegedAction<T> action) {
        return createInVmActions().runInVm(action);
    }

    static AccessAuditContext currentAccessAuditContext() {
        return createAccessAuditContextActions().currentContext();
    }

    private static AccessAuditContextActions createAccessAuditContextActions() {
        return WildFlySecurityManager.isChecking() ? AccessAuditContextActions.PRIVILEGED : AccessAuditContextActions.NON_PRIVILEGED;
    }

    private static InVmActions createInVmActions() {
        return WildFlySecurityManager.isChecking() ? InVmActions.PRIVILEGED : InVmActions.NON_PRIVILEGED;
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

    private interface InVmActions {

        boolean isInVmCall();

        <T> T runInVm(PrivilegedAction<T> action);

        InVmActions NON_PRIVILEGED = new InVmActions() {

            @Override
            public <T> T runInVm(PrivilegedAction<T> action) {
                return InVmAccess.runInVm(action);
            }

            @Override
            public boolean isInVmCall() {
                return InVmAccess.isInVmCall();
            }
        };

        InVmActions PRIVILEGED = new InVmActions() {

            @Override
            public <T> T runInVm(final PrivilegedAction<T> action) {
                return doPrivileged((PrivilegedAction<T>) () -> InVmAccess.runInVm(action));
            }

            @Override
            public boolean isInVmCall() {
                return doPrivileged((PrivilegedAction<Boolean>) NON_PRIVILEGED::isInVmCall);
            }
        };
    }
}
