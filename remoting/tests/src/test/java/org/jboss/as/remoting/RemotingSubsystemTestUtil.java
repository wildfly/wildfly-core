/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import static org.jboss.as.controller.capability.RuntimeCapability.buildDynamicCapabilityName;
import static org.jboss.as.controller.capability.RuntimeCapability.resolveCapabilityName;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.ExtensionRegistryType;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.dmr.ModelType;
import org.wildfly.extension.io.IOExtension;
import org.wildfly.io.IOServiceDescriptor;

/**
 * Utilities for the remoting subsystem tests.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
class RemotingSubsystemTestUtil {

    static final AdditionalInitialization DEFAULT_ADDITIONAL_INITIALIZATION =
            AdditionalInitialization.withCapabilities(
                    buildDynamicCapabilityName(IOServiceDescriptor.WORKER.getName(), "default"),
                    // This one is specified in one of the test configs
                    buildDynamicCapabilityName(IOServiceDescriptor.WORKER.getName(), "default-remoting"),
                    resolveCapabilityName(OutboundSocketBinding.SERVICE_DESCRIPTOR, "dummy-outbound-socket"),
                    resolveCapabilityName(OutboundSocketBinding.SERVICE_DESCRIPTOR, "other-outbound-socket"),
                    resolveCapabilityName(SocketBinding.SERVICE_DESCRIPTOR, "remoting")

            );

    static final AdditionalInitialization HC_ADDITIONAL_INITIALIZATION =
            new AdditionalInitialization.ManagementAdditionalInitialization() {

                @Override
                protected ProcessType getProcessType() {
                    return ProcessType.HOST_CONTROLLER;
                }

                @Override
                protected void initializeExtraSubystemsAndModel(ExtensionRegistry extensionRegistry, Resource rootResource, ManagementResourceRegistration rootRegistration, RuntimeCapabilityRegistry capabilityRegistry) {
                    super.initializeExtraSubystemsAndModel(extensionRegistry, rootResource, rootRegistration, capabilityRegistry);
                    AdditionalInitialization.registerCapabilities(capabilityRegistry,
                            // This one is specified in one of the test configs
                            buildDynamicCapabilityName(IOServiceDescriptor.WORKER.getName(), "default-remoting"));

                    // Deal with the fact that legacy parsers will add the io extension/subsystem
                    registerIOExtension(extensionRegistry, rootRegistration);
                }
            };

    static void registerIOExtension(ExtensionRegistry extensionRegistry, ManagementResourceRegistration rootRegistration) {
        ManagementResourceRegistration extReg = rootRegistration.registerSubModel(new SimpleResourceDefinition(PathElement.pathElement(EXTENSION, "org.wildfly.extension.io"),
                NonResolvingResourceDescriptionResolver.INSTANCE, new ModelOnlyAddStepHandler(), ModelOnlyRemoveStepHandler.INSTANCE));
        extReg.registerReadOnlyAttribute(new SimpleAttributeDefinitionBuilder("module", ModelType.STRING).build(), null);

        Extension ioe = new IOExtension();
        ioe.initialize(extensionRegistry.getExtensionContext("org.wildfly.extension.io", rootRegistration, ExtensionRegistryType.MASTER));

    }
    private RemotingSubsystemTestUtil() {
        //
    }
}
