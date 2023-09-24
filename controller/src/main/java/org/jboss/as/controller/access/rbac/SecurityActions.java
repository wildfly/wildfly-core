/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.rbac;

import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedAction;

import org.jboss.as.controller.access.InVmAccess;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Security actions for the 'org.jboss.as.controller.access.rbac' package.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SecurityActions {

    private SecurityActions() {
    }

    static boolean isInVmCall() {
        return createInVmActions().isInVmCall();
    }

    private static InVmActions createInVmActions() {
        return WildFlySecurityManager.isChecking() ? InVmActions.PRIVILEGED : InVmActions.NON_PRIVILEGED;
    }

    private interface InVmActions {

        boolean isInVmCall();

        InVmActions NON_PRIVILEGED = new InVmActions() {

            @Override
            public boolean isInVmCall() {
                return InVmAccess.isInVmCall();
            }
        };

        InVmActions PRIVILEGED = new InVmActions() {

            private final PrivilegedAction<Boolean> PRIVILEGED_ACTION = new PrivilegedAction<Boolean>() {

                @Override
                public Boolean run() {
                    return NON_PRIVILEGED.isInVmCall();
                }

            };

            @Override
            public boolean isInVmCall() {
                return doPrivileged(PRIVILEGED_ACTION);
            }
        };

    }


}
