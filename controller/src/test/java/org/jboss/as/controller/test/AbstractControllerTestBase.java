/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLED_BACK;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceBuilder;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.TestModelControllerService;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.notification.NotificationHandlerRegistry;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.persistence.AbstractConfigurationPersister;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.staxmapper.XMLElementWriter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class AbstractControllerTestBase {


    protected abstract void initModel(ManagementModel managementModel);

    protected ServiceContainer container;
    protected ModelController controller;
    protected volatile ProcessType processType;
    protected CapabilityRegistry capabilityRegistry;
    private NotificationHandlerRegistry notificationHandlerRegistry;

    protected AbstractControllerTestBase(ProcessType processType) {
        this.processType = processType;
    }

    protected AbstractControllerTestBase() {
        this(ProcessType.EMBEDDED_SERVER);
    }

    public ModelController getController() {
        return controller;
    }

    public ServiceContainer getContainer() {
        return container;
    }

    protected ModelNode createOperation(String operationName, String... address) {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(operationName);
        if (address.length > 0) {
            for (String addr : address) {
                operation.get(OP_ADDR).add(addr);
            }
        } else {
            operation.get(OP_ADDR).setEmptyList();
        }

        return operation;
    }

    protected ModelNode createOperation(String operationName, PathAddress address) {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(operationName);
        if (address.size() > 0) {
            operation.get(OP_ADDR).set(address.toModelNode());
        } else {
            operation.get(OP_ADDR).setEmptyList();
        }

        return operation;
    }

    protected ModelNode createOperation(String operationName) {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(operationName);
        operation.get(OP_ADDR).setEmptyList();
        return operation;
    }

    public ModelNode executeForResult(ModelNode operation) throws OperationFailedException {
        return executeCheckNoFailure(operation).get(RESULT);
    }

    public void executeForFailure(ModelNode operation) {
        try {
            ModelNode result = executeForResult(operation);
            Assert.fail(operation + " did not fail; returned " + result);
        } catch (OperationFailedException expected) {
            // ignore
        }
    }

    public ModelNode executeCheckNoFailure(ModelNode operation) throws OperationFailedException {
        ModelNode rsp = getController().execute(operation, null, null, null);
        assertNoUndefinedRolledBackNode(rsp);
        if (FAILED.equals(rsp.get(OUTCOME).asString())) {
            ModelNode fd = rsp.get(FAILURE_DESCRIPTION);
            throw new OperationFailedException(fd.toString(), fd);
        }
        return rsp;
    }

    public ModelNode executeCheckForFailure(ModelNode operation) {
        ModelNode rsp = getController().execute(operation, null, null, null);
        assertNoUndefinedRolledBackNode(rsp);
        if (!FAILED.equals(rsp.get(OUTCOME).asString())) {
            Assert.fail("Should have failed!");
        }
        return rsp;
    }

    @Before
    public void setupController() throws InterruptedException {
        container = ServiceContainer.Factory.create("test");
        ServiceTarget target = container.subTarget();
        ModelControllerService svc = createModelControllerService(processType);
        target.addService(ServiceName.of("ModelController")).setInstance(svc).install();
        svc.awaitStartup(30, TimeUnit.SECONDS);
        controller = svc.getValue();
        capabilityRegistry = svc.getCapabilityRegistry();
        notificationHandlerRegistry = svc.getNotificationHandlerRegistry();
        ModelNode setup = Util.getEmptyOperation("setup", new ModelNode());
        controller.execute(setup, null, null, null);
    }

    @After
    public void shutdownServiceContainer() {
        if (container != null) {
            container.shutdown();
            try {
                container.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                container = null;
            }
        }
    }

    protected ModelControllerService createModelControllerService(ProcessType processType) {
        return new ModelControllerService(processType);
    }

    protected void addBootOperations(List<ModelNode> bootOperations) {

    }

    protected NotificationHandlerRegistry getNotificationHandlerRegistry() {
        return notificationHandlerRegistry;
    }

    public class ModelControllerService extends TestModelControllerService {

        public ModelControllerService(final ProcessType processType) {
            this(processType, new RunningModeControl(RunningMode.NORMAL));
        }

        public ModelControllerService(final ProcessType processType, RunningModeControl runningModeControl) {
            this(processType, runningModeControl, null);
        }

        public ModelControllerService(final ProcessType processType, RunningModeControl runningModeControl, Supplier<ExecutorService> executorService) {
            super(processType, runningModeControl, executorService, new EmptyConfigurationPersister(), new ControlledProcessState(true),
                    ResourceBuilder.Factory.create(PathElement.pathElement("root"), NonResolvingResourceDescriptionResolver.INSTANCE).build()
            );
        }

        public ModelControllerService(final ProcessType processType, ResourceDefinition resourceDefinition){
            super(processType, new EmptyConfigurationPersister(), new ControlledProcessState(true), resourceDefinition);
        }

        @Override
        protected boolean boot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure)
                throws ConfigurationPersistenceException {
            addBootOperations(bootOperations);
            return super.boot(bootOperations, rollbackOnRuntimeFailure);
        }

        protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
            try {
                AbstractControllerTestBase.this.initModel(managementModel);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class EmptyConfigurationPersister extends AbstractConfigurationPersister {

        public EmptyConfigurationPersister() {
            super(null);
        }

        public EmptyConfigurationPersister(XMLElementWriter<ModelMarshallingContext> rootDeparser) {
            super(rootDeparser);
        }

        @Override
        public PersistenceResource store(final ModelNode model, Set<PathAddress> affectedAddresses) {
            return NullPersistenceResource.INSTANCE;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<ModelNode> load() {
            return new ArrayList<ModelNode>();
        }

        private static class NullPersistenceResource implements ConfigurationPersister.PersistenceResource {

            private static final NullPersistenceResource INSTANCE = new NullPersistenceResource();

            @Override
            public void commit() {
            }

            @Override
            public void rollback() {
            }
        }
    }

    static void createModel(final OperationContext context, final ModelNode node) {
        createModel(context, PathAddress.EMPTY_ADDRESS, node);
    }

    static void createModel(final OperationContext context, final PathAddress base, final ModelNode node) {
        if (!node.isDefined()) {
            return;
        }
        final ManagementResourceRegistration registration = context.getResourceRegistrationForUpdate();
        final Set<String> children = registration.getChildNames(base);
        final ModelNode current = new ModelNode();
        final Resource resource = base.size() == 0 ? context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS) : context.createResource(base);
        if (node.getType() == ModelType.OBJECT) {
            for (final String key : node.keys()) {
                if (!children.contains(key)) {
                    current.get(key).set(node.get(key));
                }
            }
            resource.getModel().set(current);
        } else {
            resource.getModel().set(node);
            return;
        }
        if (children != null && !children.isEmpty()) {
            for (final String childType : children) {
                if (node.hasDefined(childType)) {
                    for (final String key : node.get(childType).keys()) {
                        createModel(context, base.append(PathElement.pathElement(childType, key)), node.get(childType, key));
                    }
                }
            }
        }
    }

    static void assertNoUndefinedRolledBackNode(ModelNode response) {
        assertFalse("Response has undefined rolled-back node", response.has(ROLLED_BACK) && !response.hasDefined(ROLLED_BACK));
    }

}
