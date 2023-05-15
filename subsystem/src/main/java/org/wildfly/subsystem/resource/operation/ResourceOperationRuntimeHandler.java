/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.operation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;

/**
 * Handles runtime/rollback behavior for {@value org.jboss.as.controller.descriptions.ModelDescriptionConstants#ADD}/{@value org.jboss.as.controller.descriptions.ModelDescriptionConstants#REMOVE}
 * resource and {@value org.jboss.as.controller.descriptions.ModelDescriptionConstants#WRITE_ATTRIBUTE_OPERATION} operations.
 * @author Paul Ferraro
 */
public interface ResourceOperationRuntimeHandler {

    /**
     * Returns a {@link ResourceOperationRuntimeHandler} that configures and installs a single service from the specified factory.
     * @param configurator a service configurator
     * @return an operation runtime handler
     */
    static ResourceOperationRuntimeHandler configureService(ResourceServiceConfigurator configurator) {
        return new ResourceServiceConfiguratorRuntimeHandler(configurator, Resource::getModel);
    }

    /**
     * Returns a {@link ResourceOperationRuntimeHandler} for resource with children that configures and installs a single service from the specified factory, using a recursively read model.
     * @param configurator a service configurator
     * @return an operation runtime handler
     */
    static ResourceOperationRuntimeHandler configureParentService(ResourceServiceConfigurator configurator) {
        return new ResourceServiceConfiguratorRuntimeHandler(configurator, Resource.Tools::readModel);
    }

    /**
     * Returns a {@link ResourceOperationRuntimeHandler} that restarts the runtime behavior of its parent resource.
     * @param parentRuntimeHandler the runtime handler of the parent resource
     * @return an operation runtime handler
     */
    static ResourceOperationRuntimeHandler restartParent(ResourceOperationRuntimeHandler parentRuntimeHandler) {
        return restartAncestor(parentRuntimeHandler, PathAddress::getParent);
    }

    /**
     * Returns a {@link ResourceOperationRuntimeHandler} that restarts the runtime behavior of an ancestor resource.
     * @param ancestorRuntimeHandler the runtime handler of an ancestor resource
     * @param ancestorAddressResolver the resolver of the ancestor path address
     * @return an operation runtime handler
     */
    static ResourceOperationRuntimeHandler restartAncestor(ResourceOperationRuntimeHandler ancestorRuntimeHandler, UnaryOperator<PathAddress> ancestorAddressResolver) {
        return new RestartAncestorResourceServiceConfiguratorRuntimeHandler(ancestorRuntimeHandler, ancestorAddressResolver);
    }

    /**
     * Adds runtime behavior for the specified resource.
     * @param context the context of the operation
     * @param resource the target resource
     * @throws OperationFailedException if the operation should fail
     */
    default void addRuntime(OperationContext context, Resource resource) throws OperationFailedException {
        this.addRuntime(context, this.readModel(resource));
    }

    /**
     * Adds runtime behavior for a resource with the specified model.
     * @param context the context of the operation
     * @param model the resource model
     * @throws OperationFailedException if the operation should fail
     */
    void addRuntime(OperationContext context, ModelNode model) throws OperationFailedException;

    /**
     * Removes runtime behavior for the specified resource.
     * @param context the context of the operation
     * @param resource the target resource
     * @throws OperationFailedException if the operation should fail
     */
    default void removeRuntime(OperationContext context, Resource resource) throws OperationFailedException {
        this.removeRuntime(context, this.readModel(resource));
    }

    /**
     * Removes runtime behavior for a resource with the specified model.
     * @param context the context of the operation
     * @param model the resource model
     * @throws OperationFailedException if the operation should fail
     */
    void removeRuntime(OperationContext context, ModelNode model) throws OperationFailedException;

    /**
     * Returns the model of the specified resource.
     * This can be overridden to provide a recursively read model.
     * @param resource the resource associated with the current operation
     * @return the resource model
     */
    default ModelNode readModel(Resource resource) {
        return resource.getModel();
    }

    /**
     * Performs installation of a single service, configured via a {@link ResourceServiceConfigurator}.
     */
    class ResourceServiceConfiguratorRuntimeHandler implements ResourceOperationRuntimeHandler {
        private final ResourceServiceConfigurator configurator;
        private final Function<Resource, ModelNode> modelReader;
        private final Map<PathAddress, ServiceController<?>> controllers = new ConcurrentHashMap<>();

        ResourceServiceConfiguratorRuntimeHandler(ResourceServiceConfigurator configurator, Function<Resource, ModelNode> modelReader) {
            this.configurator = configurator;
            this.modelReader = modelReader;
        }

        @Override
        public void addRuntime(OperationContext context, ModelNode model) throws OperationFailedException {
            ResourceServiceInstaller installer = this.configurator.configure(context, model);
            this.controllers.put(context.getCurrentAddress(), installer.install(context));
        }

        @Override
        public void removeRuntime(OperationContext context, ModelNode model) throws OperationFailedException {
            ServiceController<?> controller = this.controllers.remove(context.getCurrentAddress());
            if (controller != null) {
                context.removeService(controller);
            }
        }

        @Override
        public ModelNode readModel(Resource resource) {
            return this.modelReader.apply(resource);
        }
    }

    /**
     * Runtime handler that restarts the runtime handler of an ancestor resource.
     */
    class RestartAncestorResourceServiceConfiguratorRuntimeHandler implements ResourceOperationRuntimeHandler {
        private final ResourceOperationRuntimeHandler ancestorRuntimeHandler;
        private final UnaryOperator<PathAddress> ancestorAddressResolver;

        RestartAncestorResourceServiceConfiguratorRuntimeHandler(ResourceOperationRuntimeHandler ancestorRuntimeHandler, UnaryOperator<PathAddress> ancestorAddressResolver) {
            this.ancestorRuntimeHandler = ancestorRuntimeHandler;
            this.ancestorAddressResolver = ancestorAddressResolver;
        }

        @Override
        public void addRuntime(OperationContext context, ModelNode model) {
            this.restartRuntime(context, model);
        }

        @Override
        public void removeRuntime(OperationContext context, ModelNode model) {
            this.restartRuntime(context, model);
        }

        private void restartRuntime(OperationContext context, ModelNode model) {
            PathAddress childAddress = context.getCurrentAddress();
            PathAddress ancestorAddress = this.ancestorAddressResolver.apply(childAddress);

            if (!context.isBooting()) {
                // Detect whether this was triggered during rollback
                boolean runtime = !context.isRollbackOnly();
                if (context.isResourceServiceRestartAllowed()) {
                    if (runtime ? context.markResourceRestarted(ancestorAddress, this) : context.revertResourceRestarted(ancestorAddress, this)) {
                        ResourceOperationRuntimeHandler ancestorRuntimeHandler = this.ancestorRuntimeHandler;
                        Resource ancestorResource = context.readResourceFromRoot(ancestorAddress);
                        Resource originalAncestorResource = context.getOriginalRootResource().navigate(ancestorAddress);
                        // Remove/recreate parent services in separate step using an OperationContext relative to the ancestor resource
                        // This is necessary to support service installation via CapabilityServiceTarget
                        // We don't actually execute a read-resource operation, we just need a valid operation with the correct address
                        context.addStep(Util.getReadResourceOperation(ancestorAddress), new OperationStepHandler() {
                            @Override
                            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                                ancestorRuntimeHandler.removeRuntime(context, runtime ? originalAncestorResource : ancestorResource);
                                ancestorRuntimeHandler.addRuntime(context, runtime ? ancestorResource : originalAncestorResource);
                            }
                        }, OperationContext.Stage.RUNTIME, true);
                    }
                } else {
                    if (runtime) {
                        context.reloadRequired();
                    } else {
                        context.revertReloadRequired();
                    }
                }
            }
        }
    }
}
