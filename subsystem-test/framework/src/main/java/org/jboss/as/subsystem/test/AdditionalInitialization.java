/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.subsystem.test;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.model.test.ModelTestModelDescriptionValidator.AttributeOrParameterArbitraryDescriptorValidator;
import org.jboss.as.subsystem.test.ModelDescriptionValidator.ValidationConfiguration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceTarget;

/**
 * Allows you to additionally initialize the service container and the model controller
 * beyond the subsystem being tested. Override this class to add behaviour.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class AdditionalInitialization extends AdditionalParsers {
    public static final AdditionalInitialization MANAGEMENT = new ManagementAdditionalInitialization();

    public static final AdditionalInitialization ADMIN_ONLY_HC = new ManagementAdditionalInitialization() {
        @Override
        protected ProcessType getProcessType() {
            return ProcessType.HOST_CONTROLLER;
        }
    };

    public static class HostControllerAdditionalInitialization extends AdditionalInitialization implements Serializable {
        private static final long serialVersionUID = -509444465514822866L;

        @Override
        protected ProcessType getProcessType() {
            return ProcessType.HOST_CONTROLLER;
        }

        @Override
        protected RunningMode getRunningMode() {
            return RunningMode.ADMIN_ONLY;
        }

        @Override
        protected boolean isValidateOperations() {
            return true;
        }
    }

    /**
     * An {@code AdditionalInitialization} whose {@link #getRunningMode() running mode} is
     * {@link org.jboss.as.controller.RunningMode#ADMIN_ONLY}
     */
    public static class ManagementAdditionalInitialization extends AdditionalInitialization implements Serializable {
        private static final long serialVersionUID = -509444465514822866L;

        @Override
        protected RunningMode getRunningMode() {
            return RunningMode.ADMIN_ONLY;
        }
    }

    /**
     * Creates a {@link org.jboss.as.subsystem.test.AdditionalInitialization.ManagementAdditionalInitialization} with
     * the given {@link org.jboss.as.controller.capability.RuntimeCapability capabilities} registered, making it
     * possible for subsystems under test to require them. No runtime API will be available, but that should not
     * be needed for a {@link org.jboss.as.controller.RunningMode#ADMIN_ONLY} test.
     *
     * @param capabilities the capabilities
     * @return the additional initialization
     */
    public static AdditionalInitialization withCapabilities(final String... capabilities) {
        return new ManagementAdditionalInitialization() {

            @Override
            protected void initializeExtraSubystemsAndModel(ExtensionRegistry extensionRegistry, Resource rootResource, ManagementResourceRegistration rootRegistration, RuntimeCapabilityRegistry capabilityRegistry) {
                super.initializeExtraSubystemsAndModel(extensionRegistry, rootResource, rootRegistration, capabilityRegistry);
                registerCapabilities(capabilityRegistry, capabilities);
            }
        };
    }

    /**
     * Creates a {@link org.jboss.as.subsystem.test.AdditionalInitialization} with
     * the given {@link org.jboss.as.controller.capability.RuntimeCapability capabilities} registered, making it
     * possible for subsystems under test to require them. Any runtime API provided in the {@code capabilities}
     * parameter will be available.
     *
     * @param capabilities the capabilities; key is the name of the capability, value is its runtime API
     * @return the additional initialization
     */
    public static AdditionalInitialization withCapabilities(final Map<String, Object> capabilities) {
        return new AdditionalInitialization() {

            @Override
            protected void initializeExtraSubystemsAndModel(ExtensionRegistry extensionRegistry, Resource rootResource, ManagementResourceRegistration rootRegistration, RuntimeCapabilityRegistry capabilityRegistry) {
                super.initializeExtraSubystemsAndModel(extensionRegistry, rootResource, rootRegistration, capabilityRegistry);
                registerCapabilities(capabilityRegistry, capabilities);
            }
        };
    }

    public static final AttributeOrParameterArbitraryDescriptorValidator ARBITRARY_DESCRIPTOR_VALIDATOR = (ModelType currentType, ModelNode currentNode, String descriptor) -> null;

    /**
     * Creates a {@link org.jboss.as.subsystem.test.AdditionalInitialization} with arbitrary descriptors, making it
     * possible for subsystems under test to require them.
     *
     * @param address
     * @param operations list of operations the arbitrary descriptors will be used.
     * @param arbitraryDescriptors the arbitrary descriptors; key is the name of the descriptor, value is its value.
     * @return the additional initialization
     */
    public static AdditionalInitialization withArbitraryDescriptors(final ModelNode address, final List<String> operations, final Map<String, String> arbitraryDescriptors) {
        return new AdditionalInitialization() {
            @Override
            protected ValidationConfiguration getModelValidationConfiguration() {
                ValidationConfiguration config = super.getModelValidationConfiguration();
                arbitraryDescriptors.entrySet().stream().map((arbitraryDescriptor) -> {
                    config.registerAttributeArbitraryDescriptor(address, arbitraryDescriptor.getKey(), arbitraryDescriptor.getValue(), ARBITRARY_DESCRIPTOR_VALIDATOR);
                    return arbitraryDescriptor;
                }).forEach((arbitraryDescriptor) -> {
                    for (String operation : operations) {
                        config.registerArbitraryDescriptorForOperation(address, operation, arbitraryDescriptor.getValue(), ARBITRARY_DESCRIPTOR_VALIDATOR);
                    }
                });
                return config;
            }
        };
    }

    /**
     * Simple utility method to register a
     * {@link org.jboss.as.controller.capability.RuntimeCapability RuntimeCapability<Void>} for each of the given
     * capability names. They will be registered against
     * {@link CapabilityScope#GLOBAL} and with the root resource and no
     * specific attribute as their {@link org.jboss.as.controller.capability.registry.RegistrationPoint}.
     *
     * @param capabilityRegistry registry to use
     * @param capabilities names of the capabilities.
     */
    public static void registerCapabilities(RuntimeCapabilityRegistry capabilityRegistry, String... capabilities) {
        for (final String capabilityName : capabilities) {
            RuntimeCapability<Void> capability = RuntimeCapability.Builder.of(capabilityName).build();
            capabilityRegistry.registerCapability(new RuntimeCapabilityRegistration(capability, CapabilityScope.GLOBAL,
                    new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));

        }
    }

    /**
     * Simple utility method to register a
     * {@link org.jboss.as.controller.capability.RuntimeCapability RuntimeCapability<?>} for each of the given
     * capability. They will be registered against {@link CapabilityScope#GLOBAL} and with the root resource and no
     * specific attribute as their {@link org.jboss.as.controller.capability.registry.RegistrationPoint}.
     *
     * @param capabilityRegistry registry to use
     * @param capabilities the capabilities.
     */
    public static void registerCapabilities(RuntimeCapabilityRegistry capabilityRegistry, RuntimeCapability<?>... capabilities) {
        for (final RuntimeCapability<?> capability : capabilities) {
            capabilityRegistry.registerCapability(new RuntimeCapabilityRegistration(capability, CapabilityScope.GLOBAL,
                    new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));

        }
    }

    /**
     * Simple utility method to register a {@link org.jboss.as.controller.capability.RuntimeCapability} with the
     * specified {@link org.jboss.as.controller.capability.RuntimeCapability#getRuntimeAPI() runtime API}
     * for each of the given capability names. They will be registered against {@link CapabilityScope#GLOBAL}
     * and with the root resource and no specific attribute as their {@link org.jboss.as.controller.capability.registry.RegistrationPoint}.
     *
     * @param capabilityRegistry registry to use
     * @param capabilities map of names of capabilities to their runtime API implementation.
     */
    public static void registerCapabilities(RuntimeCapabilityRegistry capabilityRegistry, final Map<String, Object> capabilities) {
        for (Map.Entry<String, Object> entry : capabilities.entrySet()) {
            final String capabilityName = entry.getKey();
            RuntimeCapability<?> capability = createCapability(capabilityName, entry.getValue());
            capabilityRegistry.registerCapability(new RuntimeCapabilityRegistration(capability, CapabilityScope.GLOBAL,
                    new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));

        }
    }

    /**
     * Simple utility method to register a {@link org.jboss.as.controller.capability.RuntimeCapability} with the
     * specified service type for each of the given capability names. They will be registered against
     * {@link CapabilityScope#GLOBAL}
     * and with the root resource and no specific attribute as their {@link org.jboss.as.controller.capability.registry.RegistrationPoint}.
     *
     * @param capabilityRegistry registry to use
     * @param capabilities map of names of capabilities to the type exposed by the MSC service the capability installs
     */
    public static void registerServiceCapabilities(RuntimeCapabilityRegistry capabilityRegistry, final Map<String, Class> capabilities) {
        for (Map.Entry<String, Class> entry : capabilities.entrySet()) {
            final String capabilityName = entry.getKey();
            RuntimeCapability<?> capability = RuntimeCapability.Builder.of(capabilityName, entry.getValue()).build();
            capabilityRegistry.registerCapability(new RuntimeCapabilityRegistration(capability, CapabilityScope.GLOBAL,
                    new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));

        }
    }

    /**
     * Simple utility method to register a single {@link org.jboss.as.controller.capability.RuntimeCapability} with the
     * specified service type for the given capability name. It will be registered against
     * {@link CapabilityScope#GLOBAL}
     * and with the root resource and no specific attribute as its {@link org.jboss.as.controller.capability.registry.RegistrationPoint}.
     *
     * @param capabilityRegistry registry to use
     * @param name the names of they capability
     * @param serviceType the type exposed by the MSC service the capability installs
     */
    public static void registerServiceCapability(RuntimeCapabilityRegistry capabilityRegistry, String name, Class serviceType) {
        RuntimeCapability<?> capability = RuntimeCapability.Builder.of(name, serviceType).build();
        capabilityRegistry.registerCapability(new RuntimeCapabilityRegistration(capability, CapabilityScope.GLOBAL,
                new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));
    }

    private static <T> RuntimeCapability<T> createCapability(final String capabilityName, final T api) {
        return RuntimeCapability.Builder.of(capabilityName, api).build();
    }

    /**
     * The process type to be used for the installed controller
     *
     * @return the process type
     */
    protected ProcessType getProcessType() {
        return ProcessType.STANDALONE_SERVER;
    }

    /**
     * The running mode to be used for the installed controller when deciding whether to
     * execute the runtime parts of the operation handlers. e.g. if {@link RunningMode#ADMIN_ONLY} the
     * runtime parts of the operation handlers should not get called since that will make {@link org.jboss.as.controller.OperationContext#isNormalServer()}
     * server return {@code false}
     *
     * @return the running mode
     */
    protected RunningMode getRunningMode() {
        return RunningMode.NORMAL;
    }

    /**
     * Whether or not the runtime resources should be registered. If {@link RunningMode#ADMIN_ONLY} the runtime resources will not
     * get registered since {@link org.jboss.as.controller.ExtensionContext#isRuntimeOnlyRegistrationValid()} will return false.
     *
     * @return the running mode
     */
    protected RunningMode getExtensionRegistryRunningMode() {
        return RunningMode.NORMAL;
    }


    /**
     * Return {@code true} to validate operations against their description provider when executing in the controller. The default is
     * {@code false}
     *
     * @return Whether operations should be validated or not
     */
    protected boolean isValidateOperations() {
        return true;
    }

    /**
     * Create a registry for extra configuration that should be taken into account when validating the description providers for the subsystem
     * in the controller. The default is an empty registry.
     *
     * @return An ArbitraryDescriptors instance containing the arbitrary descriptors, or {@code null} to not validate the description providers.
     */
    protected ModelDescriptionValidator.ValidationConfiguration getModelValidationConfiguration() {
        return new ValidationConfiguration();
    }


    /**
     * Creates the controller initializer.
     * Override this method to do custom initialization.
     *
     * @return the created controller initializer
     */
    protected ControllerInitializer createControllerInitializer() {
        return new ControllerInitializer();
    }

    /**
     * Allows easy initialization of commonly used parts of the model and invocation of associated boottime operations
     *
     * @param controllerInitializer the controller initializer
     */
    protected void setupController(ControllerInitializer controllerInitializer) {
    }

    /**
     * Adds extra services to the service controller
     *
     * @param target the service controller target
     */
    protected void addExtraServices(ServiceTarget target) {
    }

    /**
     * Allows extra initialization of the model and addition of extra subsystems
     *
     * @param extensionRegistry allows installation of extra subsystem extensions, call
     *                          {@link ExtensionRegistry#getExtensionContext(String, ManagementResourceRegistration, org.jboss.as.controller.extension.ExtensionRegistryType)}
     *                          and then {@code Extension.initialize(extensionContext)} for each extra extension you have
     * @param rootResource the root model resource which allows you to for example add child elements to the model
     * @param rootRegistration the root resource registration which allows you to for example add additional operations to the model
     * @param capabilityRegistry registry for capabilities and requirements exposed by the extra subsystems
     */
    protected void initializeExtraSubystemsAndModel(ExtensionRegistry extensionRegistry, Resource rootResource, ManagementResourceRegistration rootRegistration,
                                                    RuntimeCapabilityRegistry capabilityRegistry) {
    }

}
