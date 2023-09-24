/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.util;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessState.State;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelController.OperationTransactionControl;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.operations.ApplyExtensionsHandler;
import org.jboss.as.host.controller.mgmt.HostControllerRegistrationHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;

/**
 * A simple {@code Service<ModelController>} base class for use in unit tests.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public abstract class TestModelControllerService extends AbstractControllerService {

    private final ControlledProcessState processState;
    final AtomicBoolean state = new AtomicBoolean(true);
    private final CountDownLatch latch = new CountDownLatch(2);
    private final HostControllerRegistrationHandler.OperationExecutor internalExecutor;
    private final AbstractControllerTestBase.DelegatingResourceDefinitionInitializer initializer;
    private ManagementResourceRegistration rootMRR;

    protected TestModelControllerService(final ProcessType processType, final ConfigurationPersister configurationPersister, final ControlledProcessState processState,
                                         final ResourceDefinition rootResourceDefinition, final ManagedAuditLogger auditLogger,
                                         final AbstractControllerTestBase.DelegatingResourceDefinitionInitializer initializer,
                                         final CapabilityRegistry capabilityRegistry) {
        super(processType, new RunningModeControl(RunningMode.NORMAL), configurationPersister, processState, rootResourceDefinition,
                null, ExpressionResolver.TEST_RESOLVER, auditLogger, new DelegatingConfigurableAuthorizer(), new ManagementSecurityIdentitySupplier(), capabilityRegistry);
        this.processState = processState;
        internalExecutor = new InternalExecutor();
        this.initializer = initializer;
    }

    public AtomicBoolean getSharedState() {
        return state;
    }

    public State getCurrentProcessState() {
        return processState.getState();
    }

    public void awaitStartup(long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (!latch.await(timeout, timeUnit)) {
            throw new RuntimeException("Failed to boot in timely fashion");
        }
    }

    @Override
    public void start(StartContext context) throws StartException {
        if (initializer != null) {
            initializer.setDelegate();
        }
        super.start(context);
        latch.countDown();
    }

    @Override
    protected void bootThreadDone() {
        super.bootThreadDone();
        latch.countDown();
    }

    @Override
    protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
        rootMRR = managementModel.getRootResourceRegistration();
    }


    public org.jboss.as.host.controller.mgmt.HostControllerRegistrationHandler.OperationExecutor getInternalExecutor() {
        return internalExecutor;
    }

    private final class InternalExecutor implements HostControllerRegistrationHandler.OperationExecutor {

        @Override
        public ModelNode execute(Operation operation, OperationMessageHandler handler, OperationTransactionControl control,
                OperationStepHandler step) {
            return internalExecute(operation, handler, control, step).getResponseNode();
        }

        @Override
        public ModelNode installSlaveExtensions(List<ModelNode> extensions) {
            Operation operation = ApplyExtensionsHandler.getOperation(extensions);
            OperationStepHandler stepHandler = rootMRR.getOperationHandler(PathAddress.EMPTY_ADDRESS, ApplyExtensionsHandler.OPERATION_NAME);
            return internalExecute(operation, OperationMessageHandler.logging, OperationTransactionControl.COMMIT, stepHandler, false, true).getResponseNode();
        }

        @Override
        public ModelNode executeReadOnly(ModelNode operation, OperationStepHandler handler, OperationTransactionControl control) {
            return executeReadOnlyOperation(operation, control, handler);
        }

        @Override
        public ModelNode executeReadOnly(ModelNode operation, Resource model, OperationStepHandler handler, OperationTransactionControl control) {
            return executeReadOnlyOperation(operation, model, control, handler);
        }


        @Override
        public void acquireReadlock(final Integer operationID) throws IllegalArgumentException, InterruptedException {
            if (operationID == null) {
                throw new IllegalArgumentException("operationID may not be null");
            }
            TestModelControllerService.this.acquireReadLock(operationID);
        }

        @Override
        public void releaseReadlock(final Integer operationID) throws IllegalArgumentException {
            if (operationID == null) {
                throw new IllegalArgumentException("operationID may not be null");
            }
            TestModelControllerService.this.releaseReadLock(operationID);
        }

    }
}
