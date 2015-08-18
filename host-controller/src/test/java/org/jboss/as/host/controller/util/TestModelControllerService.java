/*
 *
 *  * JBoss, Home of Professional Open Source.
 *  * Copyright 2013, Red Hat, Inc., and individual contributors
 *  * as indicated by the @author tags. See the copyright.txt file in the
 *  * distribution for a full listing of individual contributors.
 *  *
 *  * This is free software; you can redistribute it and/or modify it
 *  * under the terms of the GNU Lesser General Public License as
 *  * published by the Free Software Foundation; either version 2.1 of
 *  * the License, or (at your option) any later version.
 *  *
 *  * This software is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  * Lesser General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU Lesser General Public
 *  * License along with this software; if not, write to the Free
 *  * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
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
                                         final AbstractControllerTestBase.DelegatingResourceDefinitionInitializer initializer) {
        super(processType, new RunningModeControl(RunningMode.NORMAL), configurationPersister, processState, rootResourceDefinition,
                null, ExpressionResolver.TEST_RESOLVER, auditLogger, new DelegatingConfigurableAuthorizer(), new CapabilityRegistry(true));
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
    }
}
