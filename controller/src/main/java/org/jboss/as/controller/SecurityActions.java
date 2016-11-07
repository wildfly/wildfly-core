/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller;

import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedAction;

import org.jboss.as.controller.access.Caller;
import org.jboss.as.controller.access.InVmAccess;
import org.wildfly.security.auth.server.SecurityIdentity;
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

    static Caller getCaller(final Caller currentCaller, final SecurityIdentity securityIdentity) {
        return createCallerActions().getCaller(currentCaller, securityIdentity);
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

    private static CallerActions createCallerActions() {
        return WildFlySecurityManager.isChecking() ? CallerActions.PRIVILEGED : CallerActions.NON_PRIVILEGED;
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

    private interface CallerActions {

        Caller getCaller(Caller currentCaller, SecurityIdentity securityIdentity);


        CallerActions NON_PRIVILEGED = new CallerActions() {

            @Override
            public Caller getCaller(Caller currentCaller, SecurityIdentity securityIdentity) {
                // This is deliberately checking the Subject is the exact same instance.
                if (currentCaller == null || securityIdentity != currentCaller.getSecurityIdentity()) {
                    return Caller.createCaller(securityIdentity);
                }

                return currentCaller;
            }

        };

        CallerActions PRIVILEGED = new CallerActions() {

            @Override
            public Caller getCaller(final Caller currentCaller, final SecurityIdentity securityIdentity) {
                return doPrivileged(new PrivilegedAction<Caller>() {

                    @Override
                    public Caller run() {
                        return NON_PRIVILEGED.getCaller(currentCaller, securityIdentity);
                    }
                });
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
            public <T> T runInVm(PrivilegedAction<T> action) {
                return doPrivileged((PrivilegedAction<T>) () -> InVmAccess.runInVm(action));
            }

            @Override
            public boolean isInVmCall() {
                return doPrivileged((PrivilegedAction<Boolean>) NON_PRIVILEGED::isInVmCall);
            }
        };
    }
}
