/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.plan;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;

import java.net.InetAddress;
import java.security.PrivilegedAction;
import java.util.List;

import org.jboss.as.controller.AccessAuditContext;
import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.remote.BlockingQueueOperationListener;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * Task responsible for updating a single server-group.
 *
 * @author Emanuel Muckenhuber
 */
// TODO cleanup ServerGroupRolloutTask vs. ServerUpdateTask vs. Concurrent/RollingUpdateTask
abstract class AbstractServerGroupRolloutTask implements Runnable {

    protected final List<ServerUpdateTask> tasks;
    protected final ServerUpdatePolicy updatePolicy;
    protected final ServerTaskExecutor executor;
    protected final SecurityIdentity securityIdentity;
    protected final InetAddress sourceAddress;
    protected final BlockingTimeout blockingTimeout;

    public AbstractServerGroupRolloutTask(List<ServerUpdateTask> tasks, ServerUpdatePolicy updatePolicy, ServerTaskExecutor executor, SecurityIdentity securityIdentity, InetAddress sourceAddress, BlockingTimeout blockingTimeout) {
        this.tasks = tasks;
        this.updatePolicy = updatePolicy;
        this.executor = executor;
        this.securityIdentity = securityIdentity;
        this.sourceAddress = sourceAddress;
        this.blockingTimeout = blockingTimeout;
    }

    @Override
    public void run() {
        try {
            AccessAuditContext.doAs(securityIdentity, sourceAddress, new PrivilegedAction<Void>() {

                @Override
                public Void run() {
                    execute();
                    return null;
                }

            });
        } catch (Throwable t) {
            DomainControllerLogger.HOST_CONTROLLER_LOGGER.debugf(t, "failed to process task %s", tasks.iterator().next().getOperation());
        }
    }

    /**
     * Execute the the rollout task.
     */
    protected abstract void execute();

    /**
     * Record a prepared operation.
     *
     * @param identity the server identity
     * @param prepared the prepared operation
     */
    protected void recordPreparedOperation(final ServerIdentity identity, final TransactionalProtocolClient.PreparedOperation<ServerTaskExecutor.ServerOperation> prepared) {
        final ModelNode preparedResult = prepared.getPreparedResult();
        // Hmm do the server results need to get translated as well as the host one?
        // final ModelNode transformedResult = prepared.getOperation().transformResult(preparedResult);
        updatePolicy.recordServerResult(identity, preparedResult);
        executor.recordPreparedOperation(prepared);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{server-group=" + updatePolicy.getServerGroupName() + "}";
    }

    void handlePreparePhaseTimeout(ServerIdentity identity, ServerUpdateTask task, long timeout) {

        blockingTimeout.proxyTimeoutDetected(identity.toPathAddress());

        // Record a synthetic prepared result so the timeout can impact the updatePolicy and
        // possibly trigger a ServerRequestRestartTask if the overall rollout isn't rolled back
        final ServerTaskExecutor.ServerOperation serverOperation = new ServerTaskExecutor.ServerOperation(identity, task.getOperation(), null, null, OperationResultTransformer.ORIGINAL_RESULT);
        final String failureMsg = ControllerLogger.ROOT_LOGGER.proxiedOperationTimedOut(task.getOperation().get(OP).asString(), identity.toPathAddress(), timeout);
        final ModelNode failureNode = new ModelNode();
        failureNode.get(OUTCOME).set(FAILED);
        failureNode.get(FAILURE_DESCRIPTION).set(failureMsg);
        final BlockingQueueOperationListener.FailedOperation<ServerTaskExecutor.ServerOperation> prepared =
                new BlockingQueueOperationListener.FailedOperation<>(serverOperation, failureNode, true);

        final ModelNode preparedResult = prepared.getPreparedResult();
        updatePolicy.recordServerResult(identity, preparedResult);
        executor.recordOperationPrepareTimeout(prepared);
    }
}
