/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import java.util.function.UnaryOperator;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.wildfly.subsystem.resource.operation.AddResourceOperationStepHandler;
import org.wildfly.subsystem.resource.operation.RemoveResourceOperationStepHandler;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;
import org.wildfly.subsystem.resource.operation.RestartParentAddResourceOperationStepHandler;
import org.wildfly.subsystem.resource.operation.RestartParentRemoveResourceOperationStepHandler;
import org.wildfly.subsystem.resource.operation.RestartParentWriteAttributeOperationStepHandler;
import org.wildfly.subsystem.resource.operation.WriteAttributeOperationStepHandler;
import org.wildfly.subsystem.service.ResourceServiceConfiguratorFactory;

/**
 * Interface implemented by self-registering management components.
 * @author Paul Ferraro
 */
public interface ManagementResourceRegistrar {

    enum RestartMode {
        NONE(OperationEntry.Flag.RESTART_NONE, OperationEntry.Flag.RESTART_NONE),
        RESTART_SERVICES(OperationEntry.Flag.RESTART_NONE, OperationEntry.Flag.RESTART_RESOURCE_SERVICES),
        RELOAD_REQUIRED(OperationEntry.Flag.RESTART_ALL_SERVICES, OperationEntry.Flag.RESTART_ALL_SERVICES),
        RESTART_REQUIRED(OperationEntry.Flag.RESTART_JVM, OperationEntry.Flag.RESTART_JVM),
        ;
        private final OperationEntry.Flag addOperationFlag;
        private final OperationEntry.Flag removeOperationFlag;

        RestartMode(OperationEntry.Flag addOperationFlag, OperationEntry.Flag removeOperationFlag) {
            this.addOperationFlag = addOperationFlag;
            this.removeOperationFlag = removeOperationFlag;
        }

        OperationEntry.Flag getAddOperationFlag() {
            return this.addOperationFlag;
        }

        OperationEntry.Flag getRemoveOperationFlag() {
            return this.removeOperationFlag;
        }
    }

    /**
     * Registers this object with a resource.
     * @param registration a registration for a management resource
     */
    void register(ManagementResourceRegistration registration);

    static ManagementResourceRegistrar of(ResourceDescriptor descriptor) {
        return of(descriptor, ResourceOperationRuntimeHandler.NONE, RestartMode.NONE);
    }

    static ManagementResourceRegistrar of(ResourceDescriptor descriptor, ResourceOperationRuntimeHandler handler) {
        return of(descriptor, handler, RestartMode.RESTART_SERVICES);
    }

    static ManagementResourceRegistrar of(ResourceDescriptor descriptor, ResourceOperationRuntimeHandler handler, RestartMode mode) {
        return new ResourceDescriptorRegistrar(descriptor, new AddResourceOperationStepHandler(descriptor, handler, mode.getAddOperationFlag()), new RemoveResourceOperationStepHandler(descriptor, handler, mode.getRemoveOperationFlag()), new WriteAttributeOperationStepHandler(descriptor, handler));
    }

    static ManagementResourceRegistrar of(ResourceServiceConfiguratorFactory parentFactory, ResourceDescriptor descriptor) {
        return of(parentFactory, descriptor, ResourceOperationRuntimeHandler.NONE);
    }

    static ManagementResourceRegistrar of(ResourceServiceConfiguratorFactory parentFactory, UnaryOperator<PathAddress> parentAddressProvider, ResourceDescriptor descriptor) {
        return of(parentFactory, parentAddressProvider, descriptor, ResourceOperationRuntimeHandler.NONE);
    }

    static ManagementResourceRegistrar of(ResourceServiceConfiguratorFactory parentFactory, ResourceDescriptor descriptor, ResourceOperationRuntimeHandler handler) {
        return of(parentFactory, PathAddress::getParent, descriptor, handler);
    }

    static ManagementResourceRegistrar of(ResourceServiceConfiguratorFactory parentFactory, UnaryOperator<PathAddress> parentAddressProvider, ResourceDescriptor descriptor, ResourceOperationRuntimeHandler handler) {
        return new ResourceDescriptorRegistrar(descriptor, new RestartParentAddResourceOperationStepHandler(parentFactory, parentAddressProvider, descriptor, handler), new RestartParentRemoveResourceOperationStepHandler(parentFactory, parentAddressProvider, descriptor, handler), new RestartParentWriteAttributeOperationStepHandler(parentFactory, parentAddressProvider, descriptor));
    }
}
