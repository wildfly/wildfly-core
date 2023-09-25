/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.management;

import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedAction;

import org.jboss.as.controller.AccessAuditContext;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.ServerAuthenticationContext;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Security actions for the 'org.jboss.as.controller.access.management' package.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SecurityActions {

    private SecurityActions() {
    }

    static AccessAuditContext currentAccessAuditContext() {
        return createAccessAuditContextActions().currentContext();
    }

    static ServerAuthenticationContext createServerAuthenticationContext(final SecurityDomain securityDomain) {
        return createElytronActions().createServerAuthenticationContext(securityDomain);
    }

    private static AccessAuditContextActions createAccessAuditContextActions() {
        return WildFlySecurityManager.isChecking() ? AccessAuditContextActions.PRIVILEGED : AccessAuditContextActions.NON_PRIVILEGED;
    }

    private static ElytronActions createElytronActions() {
        return WildFlySecurityManager.isChecking() ? ElytronActions.PRIVILEGED : ElytronActions.NON_PRIVILEGED;
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

    private interface ElytronActions {

        ServerAuthenticationContext createServerAuthenticationContext(final SecurityDomain securityDomain);

        ElytronActions NON_PRIVILEGED = new ElytronActions() {

            @Override
            public ServerAuthenticationContext createServerAuthenticationContext(final SecurityDomain securityDomain) {
                return securityDomain.createNewAuthenticationContext();
            }
        };

        ElytronActions PRIVILEGED = new ElytronActions() {

            @Override
            public ServerAuthenticationContext createServerAuthenticationContext(final SecurityDomain securityDomain) {
                return doPrivileged((PrivilegedAction<ServerAuthenticationContext>) (() -> NON_PRIVILEGED
                        .createServerAuthenticationContext(securityDomain)));
            }
        };
    }

}
