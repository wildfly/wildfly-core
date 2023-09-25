/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.extension;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.IOException;
import java.net.ServerSocket;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * Fake extension to use in testing extension management.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 * @author Kabir Khan
 */
public class TestHostCapableExtension implements Extension {

    public static final String MODULE_NAME = "org.jboss.as.test.hc.extension";
    public static final String SUBSYSTEM_NAME = "HC";

    private final EmptySubsystemParser parser = new EmptySubsystemParser("urn:jboss:test:extension:HC:1.0");


    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration reg = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1, 1, 1));
        reg.setHostCapable();
        reg.registerXMLElementWriter(parser);
        reg.registerSubsystemModel(new RootResourceDefinition(SUBSYSTEM_NAME));
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, parser.getNamespace(), parser);
    }

    private static class RootResourceDefinition extends SimpleResourceDefinition {
        static final String SOCKET_CAPABILITY_NAME = "org.wildfly.network.socket-binding";
        private static final String TEST_CAPABILITY_NAME = "org.wildfly.test.hc.capability";
        static final RuntimeCapability<Void> TEST_CAPABILITY =
                RuntimeCapability.Builder.of(TEST_CAPABILITY_NAME, true)
                        //.addDynamicRequirements(SOCKET_CAPABILITY_NAME)
                        .build();


        private static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder("name", ModelType.STRING, false).build();
        private static final SimpleAttributeDefinition SOCKET_BINDING =
                new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SOCKET_BINDING, ModelType.STRING, true)
                .setCapabilityReference(SOCKET_CAPABILITY_NAME, TEST_CAPABILITY_NAME)
                .build();
        private static final OperationDefinition TEST_OP = new SimpleOperationDefinitionBuilder("test-op", NonResolvingResourceDescriptionResolver.INSTANCE).build();

        public RootResourceDefinition(String name) {
            super(new SimpleResourceDefinition.Parameters(PathElement.pathElement(SUBSYSTEM, name), NonResolvingResourceDescriptionResolver.INSTANCE)
                    .setAddHandler(new AddSubsystemHandler())
                    .setRemoveHandler(new RemoveSubsystemHandler())
                    .addCapabilities(TEST_CAPABILITY));
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            super.registerAttributes(resourceRegistration);
            OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(NAME, SOCKET_BINDING);
            resourceRegistration.registerReadWriteAttribute(NAME, null, writeHandler);
            resourceRegistration.registerReadWriteAttribute(SOCKET_BINDING, null, writeHandler);
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            super.registerOperations(resourceRegistration);
            resourceRegistration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
            resourceRegistration.registerOperationHandler(TEST_OP, new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    ServiceController<?> sc = context.getServiceRegistry(false).getService(createServiceName(context.getCurrentAddress()));
                    context.getResult().set(sc != null);
                }
            });
        }

        private static ServiceName createServiceName(PathAddress address) {
            ServiceName name = ServiceName.JBOSS;
            name = name.append("test");
            for (PathElement element : address) {
                name = name.append(element.getKey(), element.getValue());
            }
            return name;
        }

        private static class AddSubsystemHandler extends AbstractAddStepHandler {

            AddSubsystemHandler() {
                super(NAME, SOCKET_BINDING);
            }

            @Override
            protected void performRuntime(OperationContext context, ModelNode operation, Resource resource)
                    throws OperationFailedException {
                boolean hasSocketBinding = resource.getModel().hasDefined(SOCKET_BINDING.getName());
                TestService service = new TestService(hasSocketBinding);
                ServiceBuilder<TestService> serviceBuilder = context.getServiceTarget().addService(createServiceName(context.getCurrentAddress()), service);
                if (hasSocketBinding) {
                    final String socketName = SOCKET_BINDING.resolveModelAttribute(context, resource.getModel()).asString();
                    final ServiceName socketBindingName = context.getCapabilityServiceName(RootResourceDefinition.SOCKET_CAPABILITY_NAME, socketName, SocketBinding.class);
                    serviceBuilder.addDependency(socketBindingName, SocketBinding.class, service.socketBindingInjector);
                }
                serviceBuilder.install();
            }
        }


        private static class RemoveSubsystemHandler extends AbstractRemoveStepHandler {

            @Override
            protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                    throws OperationFailedException {
                context.removeService(createServiceName(context.getCurrentAddress()));
            }
        }

        private static class TestService implements Service<TestService> {
            private final boolean hasSocketBinding;
            InjectedValue<SocketBinding> socketBindingInjector = new InjectedValue<>();
            private volatile ServerSocket serverSocket;

            public TestService(boolean hasSocketBinding) {
                this.hasSocketBinding = hasSocketBinding;
            }

            @Override
            public TestService getValue() throws IllegalStateException, IllegalArgumentException {
                return this;
            }

            @Override
            public void start(StartContext context) throws StartException {
                if (hasSocketBinding) {
                    SocketBinding binding = socketBindingInjector.getValue();
                    try {
                        serverSocket = binding.createServerSocket();
                    } catch (IOException e) {
                        throw new StartException(e);
                    }
                }
            }

            @Override
            public void stop(StopContext context) {
                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException ignore) {
                    }
                }
            }
        }
    }

}
