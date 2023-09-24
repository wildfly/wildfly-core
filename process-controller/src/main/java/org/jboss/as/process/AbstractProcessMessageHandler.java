/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process;

import java.io.IOException;
import java.util.Map;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractProcessMessageHandler implements ProcessMessageHandler {

    public void handleProcessAdded(final ProcessControllerClient client, final String processName) {
    }

    public void handleProcessStarted(final ProcessControllerClient client, final String processName) {
    }

    public void handleProcessStopped(final ProcessControllerClient client, final String processName, final long uptimeMillis) {
    }

    public void handleProcessRemoved(final ProcessControllerClient client, final String processName) {
    }

    public void handleProcessInventory(final ProcessControllerClient client, final Map<String, ProcessInfo> inventory) {
    }

    public void handleConnectionShutdown(final ProcessControllerClient client) {
    }

    public void handleConnectionFailure(final ProcessControllerClient client, final IOException cause) {
    }

    public void handleConnectionFinished(final ProcessControllerClient client) {
    }

    public void handleOperationFailed(ProcessControllerClient client, OperationType operation, String processName) {
    }
}
