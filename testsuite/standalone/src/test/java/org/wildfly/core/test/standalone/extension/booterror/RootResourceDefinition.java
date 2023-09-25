/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.extension.booterror;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * @author Brian Stansberry
 */
public class RootResourceDefinition extends SimpleResourceDefinition {

    static final RuntimeCapability<Void> CAPABILITY = RuntimeCapability.Builder.of("boot.error", Void.class)
            .addRequirements("org.wildfly.management.model-controller-client-factory", "org.wildfly.management.executor")
            .build();

    static final SimpleAttributeDefinition ATTRIBUTE = new SimpleAttributeDefinitionBuilder("attribute", ModelType.STRING, true).build();
    static final String BOOT_ERROR_MSG = "Failed during start";

    private static final ModelNode BAD_OPERATION;

    static {
        BAD_OPERATION = Util.createEmptyOperation("non-existent", PathAddress.EMPTY_ADDRESS);
        BAD_OPERATION.protect();
    }

    static String bootError = null;

    RootResourceDefinition() {
        super(new Parameters(PathElement.pathElement(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME), NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddHandler(new AddSubsystemHandler())
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE)
                .setCapabilities(CAPABILITY)
        );

        bootError = null;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadOnlyAttribute(ATTRIBUTE,
                (context, operation) -> context.getResult().set(bootError == null ? new ModelNode() : new ModelNode(bootError)));
    }


    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new SimpleResourceDefinition(new Parameters(PathElement.pathElement("key", "value"), NonResolvingResourceDescriptionResolver.INSTANCE)
        .setAddHandler(new AbstractAddStepHandler() {

            @Override
            protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
                if (context.isBooting()) {
                    // Record a boot error
                    context.getFailureDescription().set(BOOT_ERROR_MSG);
                }
            }
        })
        .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE)));
    }

    private static class AddSubsystemHandler extends AbstractAddStepHandler {
        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) {

            // Add a service that will invoke an op that produces a failure during start. The service
            // will start during boot so that simulates a non-boot op failing during boot
            CapabilityServiceBuilder<?> builder = context.getCapabilityServiceTarget().addCapability(CAPABILITY);
            Supplier<ModelControllerClientFactory> mccf = builder.requiresCapability("org.wildfly.management.model-controller-client-factory", ModelControllerClientFactory.class);
            Supplier<Executor> executor = builder.requiresCapability("org.wildfly.management.executor", Executor.class);
            builder.setInstance(new BootErrorService(mccf, executor)).install();
        }
    }

    private static class BootErrorService implements Service {
        private final Supplier<ModelControllerClientFactory> mccf;
        private final Supplier<Executor> executor;

        private BootErrorService(Supplier<ModelControllerClientFactory> mccf, Supplier<Executor> executor) {
            this.mccf = mccf;
            this.executor = executor;
        }

        @Override
        public void start(StartContext context) throws StartException {
            ModelNode response = mccf.get().createSuperUserClient(executor.get()).execute(BAD_OPERATION);
            bootError = response.hasDefined(FAILURE_DESCRIPTION) ? response.get(FAILURE_DESCRIPTION).asString() : null;
        }

        @Override
        public void stop(StopContext context) {
            // no-op
        }
    }

}
