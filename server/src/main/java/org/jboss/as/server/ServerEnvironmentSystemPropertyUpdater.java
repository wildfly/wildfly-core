/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.common.ProcessEnvironmentSystemPropertyUpdater;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerEnvironmentSystemPropertyUpdater implements ProcessEnvironmentSystemPropertyUpdater {

    private final ServerEnvironment serverEnvironment;

    public ServerEnvironmentSystemPropertyUpdater(ServerEnvironment serverEnvironment) {
        this.serverEnvironment = serverEnvironment;
    }

    @Override
    public boolean isRuntimeSystemPropertyUpdateAllowed(String propertyName, String propertyValue, boolean bootTime)
            throws OperationFailedException {
        return serverEnvironment.isRuntimeSystemPropertyUpdateAllowed(propertyName, propertyValue, bootTime);
    }

    @Override
    public void systemPropertyUpdated(String propertyName, String propertyValue) {
        serverEnvironment.systemPropertyUpdated(propertyName, propertyValue);
    }

}
