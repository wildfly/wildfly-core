/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.plan;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * @author Emanuel Muckenhuber
 */
class ConcurrentServerGroupUpdateTask extends AbstractServerGroupRolloutTask implements Runnable {

    public ConcurrentServerGroupUpdateTask(List<ServerUpdateTask> tasks, ServerUpdatePolicy updatePolicy,
                                           ServerTaskExecutor executor, SecurityIdentity securityIdentity, InetAddress sourceAddress, BlockingTimeout blockingTimeout) {
        super(tasks, updatePolicy, executor, securityIdentity, sourceAddress, blockingTimeout);
    }

    @Override
    public void execute() {
        final Map<ServerIdentity, ServerUpdateTask> outstanding = new HashMap<>();
        final ServerTaskExecutor.ServerOperationListener listener = new ServerTaskExecutor.ServerOperationListener();
        int preparedTimeout = 0;
        for(final ServerUpdateTask task : tasks) {
            final ServerIdentity identity = task.getServerIdentity();
            if (updatePolicy.canUpdateServer(identity) && !Thread.currentThread().isInterrupted()) {
                // Execute the task
                int serverTimeout = executor.executeTask(listener, task);
                if (serverTimeout > -1) {
                    outstanding.put(task.getServerIdentity(), task);
                    if (serverTimeout > preparedTimeout) {
                        preparedTimeout = serverTimeout;
                    }
                }
            } else {
                DomainControllerLogger.HOST_CONTROLLER_LOGGER.tracef("Skipping server update task for %s", identity);
            }
        }
        boolean interrupted = false;
        long deadline = System.currentTimeMillis() + preparedTimeout;
        long remaining = preparedTimeout;
        while (!interrupted && !outstanding.isEmpty() && remaining > 0) {
            try {
                // Wait for all prepared results
                final TransactionalProtocolClient.PreparedOperation<ServerTaskExecutor.ServerOperation> prepared = listener.retrievePreparedOperation(remaining, TimeUnit.MILLISECONDS);
                if (prepared == null) {
                    // timed out
                    break;
                }
                final ServerIdentity identity = prepared.getOperation().getIdentity();
                recordPreparedOperation(identity, prepared);
                outstanding.remove(identity);
            } catch (InterruptedException e) {
                interrupted = true;
            }
            remaining = deadline - System.currentTimeMillis();
        }

        if (!outstanding.isEmpty()) {
            if (interrupted) {
                DomainControllerLogger.HOST_CONTROLLER_LOGGER.interruptedAwaitingPreparedResponse(getClass().getSimpleName(), outstanding.keySet());
            } else {
                DomainControllerLogger.HOST_CONTROLLER_LOGGER.timedOutAwaitingPreparedResponse(getClass().getSimpleName(), preparedTimeout, outstanding.keySet());
            }
            for (Map.Entry<ServerIdentity, ServerUpdateTask> entry : outstanding.entrySet()) {
                ServerIdentity identity = entry.getKey();
                executor.cancelTask(identity);
                if (!interrupted) {
                    handlePreparePhaseTimeout(identity, entry.getValue(), preparedTimeout);
                }
            }
        }

        if(interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
