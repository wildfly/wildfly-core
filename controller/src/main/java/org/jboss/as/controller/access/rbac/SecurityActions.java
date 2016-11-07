/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
