/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.extension;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface RuntimeHostControllerInfoAccessor {
    /**
     * If the {@link OperationContext#getProcessType()#isHostController()} is true return an instance of
     * LocalHostControllerInfoImpl
     *
     * @param context the operation context
     * @return the LocalHostControllerInfoImpl, or {@code null} if we are not a host controller
     * @throws IllegalStateException if we are a host controller booting and not yet in the {@link OperationContext.Stage#RUNTIME} stage
     */
    HostControllerInfo getHostControllerInfo(OperationContext context) throws OperationFailedException ;

    RuntimeHostControllerInfoAccessor SERVER = new RuntimeHostControllerInfoAccessor() {
        @Override
        public HostControllerInfo getHostControllerInfo(OperationContext context) {
            return null;
        }
    };

    interface HostControllerInfo {
        boolean isMasterHc();
    }
}
