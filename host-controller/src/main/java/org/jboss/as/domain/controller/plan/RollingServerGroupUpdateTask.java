/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.controller.plan;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.jboss.as.domain.controller.ServerIdentity;

/**
 * @author Emanuel Muckenhuber
 */
class RollingServerGroupUpdateTask extends AbstractServerGroupRolloutTask implements Runnable {

    public RollingServerGroupUpdateTask(List<ServerUpdateTask> tasks, ServerUpdatePolicy updatePolicy,
                                        ServerTaskExecutor executor, SecurityIdentity securityIdentity, InetAddress sourceAddress, BlockingTimeout blockingTimeout) {
        super(tasks, updatePolicy, executor, securityIdentity, sourceAddress, blockingTimeout);
    }

    @Override
    public void execute() {
        boolean interrupted = false;
        final ServerTaskExecutor.ServerOperationListener listener = new ServerTaskExecutor.ServerOperationListener();
        for(final ServerUpdateTask task : tasks) {
            final ServerIdentity identity = task.getServerIdentity();
            if(interrupted || ! updatePolicy.canUpdateServer(identity)) {
                DomainControllerLogger.HOST_CONTROLLER_LOGGER.tracef("Skipping server update task for %s", identity);
                continue;
            }
            // Execute the task
            long timeout = executor.executeTask(listener, task);
            if (timeout > -1) {
                try {
                    // Wait for the prepared result
                    final TransactionalProtocolClient.PreparedOperation<ServerTaskExecutor.ServerOperation> prepared =
                            listener.retrievePreparedOperation(timeout, TimeUnit.MILLISECONDS);
                    if (prepared != null) {
                        recordPreparedOperation(identity, prepared);
                    } else {
                        DomainControllerLogger.HOST_CONTROLLER_LOGGER.timedOutAwaitingPreparedResponse(getClass().getSimpleName(), timeout, Collections.singleton(identity));
                        executor.cancelTask(identity);
                        handlePreparePhaseTimeout(identity, task, timeout);
                    }
                } catch (InterruptedException e) {
                    DomainControllerLogger.HOST_CONTROLLER_LOGGER.interruptedAwaitingPreparedResponse(getClass().getSimpleName(), Collections.singleton(identity));
                    executor.cancelTask(identity);
                    interrupted = true;
                }
            }
        }
        if(interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
