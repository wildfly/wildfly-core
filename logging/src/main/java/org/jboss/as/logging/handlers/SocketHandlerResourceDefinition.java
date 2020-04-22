/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.logging.handlers;

import static org.jboss.as.logging.CommonAttributes.AUTOFLUSH;
import static org.jboss.as.logging.CommonAttributes.ENABLED;
import static org.jboss.as.logging.CommonAttributes.ENCODING;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.Logging.createOperationFailure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import javax.net.ssl.SSLContext;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.LoggingOperations;
import org.jboss.as.logging.LoggingOperations.LoggingAddOperationStepHandler;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.handlers.ClientSocketFactory;
import org.jboss.logmanager.handlers.DelayedHandler;
import org.jboss.logmanager.handlers.SocketHandler;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.function.Functions;

/**
 * Represents a {@link SocketHandler}. The handler will be wrapped with a {@link DelayedHandler} for booting from the
 * {@code logging.properties} file.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("Convert2Lambda")
public class SocketHandlerResourceDefinition extends TransformerResourceDefinition {
    public static final String NAME = "socket-handler";
    private static final PathElement PATH = PathElement.pathElement(NAME);

    public static final SimpleAttributeDefinition BLOCK_ON_RECONNECT = SimpleAttributeDefinitionBuilder.create("block-on-reconnect", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    public static final SimpleAttributeDefinition FILTER_SPEC = SimpleAttributeDefinitionBuilder.create(AbstractHandlerDefinition.FILTER_SPEC)
            .setAlternatives(new String[0])
            .build();

    public static final SimpleAttributeDefinition NAMED_FORMATTER = SimpleAttributeDefinitionBuilder.create(AbstractHandlerDefinition.NAMED_FORMATTER)
            .setAttributeMarshaller(ElementAttributeMarshaller.NAME_ATTRIBUTE_MARSHALLER)
            .setAlternatives(new String[0])
            .setRequired(true)
            .build();

    public static final SimpleAttributeDefinition OUTBOUND_SOCKET_BINDING_REF = SimpleAttributeDefinitionBuilder.create("outbound-socket-binding-ref", ModelType.STRING, false)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_BINDING_REF)
            .setAllowExpression(true)
            .setCapabilityReference(Capabilities.OUTBOUND_SOCKET_BINDING_CAPABILITY)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleAttributeDefinition PROTOCOL = SimpleAttributeDefinitionBuilder.create("protocol", ModelType.STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(SocketHandler.Protocol.TCP.name()))
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setValidator(new EnumValidator<>(SocketHandler.Protocol.class, SocketHandler.Protocol.values()))
            .build();

    public static final SimpleAttributeDefinition SSL_CONTEXT = SimpleAttributeDefinitionBuilder.create("ssl-context", ModelType.STRING, true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SSL_REF)
            .setAllowExpression(true)
            .setCapabilityReference(Capabilities.SSL_CONTEXT_CAPABILITY)
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {
            AUTOFLUSH,
            BLOCK_ON_RECONNECT,
            LEVEL,
            ENABLED,
            ENCODING,
            NAMED_FORMATTER,
            FILTER_SPEC,
            PROTOCOL,
            OUTBOUND_SOCKET_BINDING_REF,
            SSL_CONTEXT,
    };

    private static final LoggingAddOperationStepHandler ADD_HANDLER = new LoggingAddOperationStepHandler(ATTRIBUTES) {

        @Override
        protected OperationStepHandler additionalModelStep(final LogContextConfiguration logContextConfiguration) {
            return new OperationStepHandler() {
                @Override
                public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                    // If we don't require runtime steps to be executed we need to discard records on the delayed handler
                    if (!requiresRuntime(context)) {
                        HandlerConfiguration configuration = logContextConfiguration.getHandlerConfiguration(context.getCurrentAddressValue());
                        if (configuration != null) {
                            if (!(configuration.getInstance() instanceof DelayedHandler)) {
                                throw LoggingLogger.ROOT_LOGGER.invalidType(DelayedHandler.class, configuration.getInstance().getClass());
                            }
                            final DelayedHandler delayedHandler = (DelayedHandler) configuration.getInstance();
                            // Clear any previous handlers and close them, then add the new handler
                            final Handler[] current = delayedHandler.setHandlers(new Handler[] {new DiscardingHandler()});
                            if (current != null) {
                                for (Handler handler : current) {
                                    handler.close();
                                }
                            }
                        }
                    }
                }
            };
        }

        @Override
        public void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            HandlerConfiguration configuration = logContextConfiguration.getHandlerConfiguration(name);
            if (configuration == null) {
                configuration = logContextConfiguration.addHandlerConfiguration(null, DelayedHandler.class.getName(), name);
            } else {
                if (!(configuration.getInstance() instanceof DelayedHandler)) {
                    throw LoggingLogger.ROOT_LOGGER.invalidType(DelayedHandler.class, configuration.getInstance().getClass());
                }
            }
            ENABLED.setPropertyValue(context, model, configuration);
            final ModelNode filter = FILTER_SPEC.resolveModelAttribute(context, model);
            if (filter.isDefined()) {
                configuration.setFilter(filter.asString());
            }
            configuration.setLevel(LEVEL.resolvePropertyValue(context, model));
            configuration.setFormatterName(NAMED_FORMATTER.resolveModelAttribute(context, model).asString());
        }

        @Override
        protected OperationStepHandler afterCommit(final LogContextConfiguration logContextConfiguration, final ModelNode model) {
            return new OperationStepHandler() {
                @Override
                public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                    final String name = context.getCurrentAddressValue();
                    final HandlerConfiguration configuration = logContextConfiguration.getHandlerConfiguration(name);

                    final SocketHandler.Protocol protocol = SocketHandler.Protocol.valueOf(PROTOCOL.resolveModelAttribute(context, model).asString());
                    final boolean autoflush = AUTOFLUSH.resolveModelAttribute(context, model).asBoolean();
                    final boolean blockOnReconnect = BLOCK_ON_RECONNECT.resolveModelAttribute(context, model).asBoolean();
                    final boolean enabled = ENABLED.resolveModelAttribute(context, model).asBoolean();

                    final DelayedHandler delayedHandler = (DelayedHandler) configuration.getInstance();
                    final String socketBindingName = OUTBOUND_SOCKET_BINDING_REF.resolveModelAttribute(context, model).asString();
                    final ModelNode sslContextRef = SSL_CONTEXT.resolveModelAttribute(context, model);

                    final ServiceName serviceName = Capabilities.HANDLER_CAPABILITY.getCapabilityServiceName(
                            Capabilities.HANDLER_CAPABILITY.getDynamicName(context.getCurrentAddress()));
                    final ServiceBuilder<?> serviceBuilder = context.getServiceTarget().addService(serviceName);

                    final Supplier<OutboundSocketBinding> outboundSocketBinding = serviceBuilder.requires(
                            context.getCapabilityServiceName(Capabilities.OUTBOUND_SOCKET_BINDING_CAPABILITY, socketBindingName,
                                    OutboundSocketBinding.class)
                    );
                    final Supplier<SocketBindingManager> socketBindingManager = serviceBuilder.requires(
                            context.getCapabilityServiceName(Capabilities.SOCKET_BINDING_MANAGER_CAPABILITY, SocketBindingManager.class)
                    );
                    final Supplier<SSLContext> sslContext;
                    if (sslContextRef.isDefined()) {
                        sslContext = serviceBuilder.requires(
                                context.getCapabilityServiceName(Capabilities.SSL_CONTEXT_CAPABILITY, sslContextRef.asString(), SSLContext.class));
                    } else {
                        if (protocol == SocketHandler.Protocol.SSL_TCP) {
                            // Attempt to use the default SSL context if a context reference was not set, but we're
                            // using the SSL_TCP protocol
                            try {
                                sslContext = Functions.constantSupplier(SSLContext.getDefault());
                            } catch (NoSuchAlgorithmException e) {
                                throw LoggingLogger.ROOT_LOGGER.failedToConfigureSslContext(e, NAME, context.getCurrentAddressValue());
                            }
                        } else {
                            // Not using SSL_TCP use a null value to be ignored in the WildFlyClientSocketFactory
                            sslContext = Functions.constantSupplier(null);
                        }
                    }

                    // A service needs to be used to ensure the dependent services are installed
                    serviceBuilder.setInstance(new Service() {

                        @Override
                        public synchronized void start(final StartContext context) {
                            final ClientSocketFactory clientSocketFactory = new WildFlyClientSocketFactory(socketBindingManager.get(),
                                    outboundSocketBinding.get(), sslContext.get(), name);
                            delayedHandler.setCloseChildren(true);
                            final SocketHandler socketHandler = new SocketHandler(clientSocketFactory, protocol);
                            socketHandler.setAutoFlush(autoflush);
                            socketHandler.setBlockOnReconnect(blockOnReconnect);
                            socketHandler.setEnabled(enabled);
                            // Get the filter, formatter and level from the DelayedHandler.
                            socketHandler.setFilter(delayedHandler.getFilter());
                            socketHandler.setFormatter(delayedHandler.getFormatter());
                            socketHandler.setLevel(delayedHandler.getLevel());
                            // Clear any previous handlers and close them, then add the new handler
                            final Handler[] current = delayedHandler.setHandlers(new Handler[] {socketHandler});
                            if (current != null) {
                                for (Handler handler : current) {
                                    handler.close();
                                }
                            }
                        }

                        @Override
                        public void stop(final StopContext context) {
                            // Nothing to do on stop
                        }
                    }).install();
                }
            };
        }
    };

    private static final OperationStepHandler REMOVE_HANDLER = new HandlerOperations.LogHandlerRemoveHandler() {
        @Override
        public void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            super.performRuntime(context, operation, model, logContextConfiguration);
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(final OperationContext context, final ModelNode operation) {
                    final ServiceName serviceName = Capabilities.HANDLER_CAPABILITY.getCapabilityServiceName(
                            Capabilities.HANDLER_CAPABILITY.getDynamicName(context.getCurrentAddress()));
                    context.removeService(serviceName);
                }
            }, Stage.RUNTIME);
        }

        @Override
        protected void revertRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            ADD_HANDLER.performRuntime(context, operation, model, logContextConfiguration);
        }
    };

    public static final SocketHandlerResourceDefinition INSTANCE = new SocketHandlerResourceDefinition();

    private SocketHandlerResourceDefinition() {
        super(new Parameters(PATH, LoggingExtension.getResourceDescriptionResolver(NAME))
                .setAddHandler(ADD_HANDLER)
                .setRemoveHandler(REMOVE_HANDLER)
                .setCapabilities(Capabilities.HANDLER_CAPABILITY)
        );

    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition attribute : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attribute, null, WriteAttributeHandler.INSTANCE);
        }
    }

    @Override
    public void registerTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder rootResourceBuilder,
                                     final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
        switch (modelVersion) {
            case VERSION_6_0_0:
                rootResourceBuilder.rejectChildResource(getPathElement());
                loggingProfileBuilder.rejectChildResource(getPathElement());
                break;
        }
    }

    private static class WriteAttributeHandler extends LoggingOperations.LoggingWriteAttributeHandler {
        static final WriteAttributeHandler INSTANCE = new WriteAttributeHandler();

        WriteAttributeHandler() {
            super(ATTRIBUTES);
        }

        @SuppressWarnings("deprecation")
        @Override
        protected boolean applyUpdate(final OperationContext context, final String attributeName, final String addressName,
                                      final ModelNode value, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {

            // First get the handler configuration.
            final HandlerConfiguration configuration = logContextConfiguration.getHandlerConfiguration(addressName);
            if (configuration == null) {
                throw createOperationFailure(LoggingLogger.ROOT_LOGGER.handlerConfigurationNotFound(addressName));
            }
            // Handle writing the attribute
            if (LEVEL.getName().equals(attributeName)) {
                configuration.setLevel(value.asString());
            } else if (NAMED_FORMATTER.getName().equals(attributeName)) {
                if (value.isDefined()) {
                    configuration.setFormatterName(value.asString());
                } else {
                    configuration.setFormatterName(null);
                }
            } else if (FILTER_SPEC.getName().equals(attributeName)) {
                if (value.isDefined()) {
                    configuration.setFilter(value.asString());
                } else {
                    configuration.setFilter(null);
                }
            }
            return Logging.requiresReload(getAttributeDefinition(attributeName).getFlags());

        }

        @Override
        protected OperationStepHandler afterCommit(final LogContextConfiguration logContextConfiguration,
                                                   final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue) {
            return new OperationStepHandler() {
                @Override
                public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                    final String addressName = context.getCurrentAddressValue();

                    // First get the handler configuration.
                    final HandlerConfiguration configuration = logContextConfiguration.getHandlerConfiguration(addressName);
                    if (configuration == null) {
                        throw createOperationFailure(LoggingLogger.ROOT_LOGGER.handlerConfigurationNotFound(addressName));
                    }
                    // Should always be a DelayedHandler
                    Handler instance = configuration.getInstance();
                    if (!(instance instanceof DelayedHandler)) {
                        throw LoggingLogger.ROOT_LOGGER.invalidType(DelayedHandler.class, instance.getClass());
                    }
                    final DelayedHandler delayedHandler = (DelayedHandler) instance;

                    // Should only contain a single handler which should be a socket handler
                    final Handler[] children = delayedHandler.getHandlers();
                    if (children == null || children.length == 0) {
                        throw LoggingLogger.ROOT_LOGGER.invalidType(SocketHandler.class, null);
                    }
                    instance = children[0];
                    if (!(instance instanceof SocketHandler)) {
                        throw LoggingLogger.ROOT_LOGGER.invalidType(SocketHandler.class, instance.getClass());
                    }
                    final SocketHandler socketHandler = (SocketHandler) instance;

                    // Only configure the socket-handler instance if the results are okay to keep
                    context.completeStep(new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(final OperationContext.ResultAction resultAction, final OperationContext context, final ModelNode operation) {
                            if (resultAction == OperationContext.ResultAction.KEEP) {
                                // Handle writing the attribute
                                if (LEVEL.getName().equals(attributeName)) {
                                    socketHandler.setLevel(delayedHandler.getLevel());
                                } else if (NAMED_FORMATTER.getName().equals(attributeName)) {
                                    socketHandler.setFormatter(delayedHandler.getFormatter());
                                } else if (FILTER_SPEC.getName().equals(attributeName)) {
                                    socketHandler.setFilter(delayedHandler.getFilter());
                                } else if (AUTOFLUSH.getName().equals(attributeName)) {
                                    socketHandler.setAutoFlush(resolvedValue.asBoolean());
                                } else if (BLOCK_ON_RECONNECT.getName().equals(attributeName)) {
                                    socketHandler.setBlockOnReconnect(resolvedValue.asBoolean());
                                } else if (ENABLED.getName().equals(attributeName)) {
                                    socketHandler.setEnabled(resolvedValue.asBoolean());
                                } else if (PROTOCOL.getName().equals(attributeName)) {
                                    socketHandler.setProtocol(SocketHandler.Protocol.valueOf(resolvedValue.asString()));
                                }
                            }
                        }
                    });
                }
            };
        }
    }

    private static class WildFlyClientSocketFactory implements ClientSocketFactory {
        private final SocketBindingManager socketBinding;
        private final OutboundSocketBinding outboundSocketBinding;
        private final String name;
        private final SSLContext sslContext;

        private WildFlyClientSocketFactory(final SocketBindingManager socketBinding,
                                           final OutboundSocketBinding outboundSocketBinding, final SSLContext sslContext,
                                           final String name) {
            this.socketBinding = socketBinding;
            this.outboundSocketBinding = outboundSocketBinding;
            this.sslContext = sslContext;
            this.name = name;
        }

        @Override
        public DatagramSocket createDatagramSocket() throws SocketException {
            return socketBinding.createDatagramSocket(name);
        }

        @Override
        public Socket createSocket() throws IOException {
            if (sslContext != null) {
                return sslContext.getSocketFactory().createSocket(getAddress(), getPort());
            }
            return outboundSocketBinding.connect();
        }

        @Override
        public InetAddress getAddress() {
            try {
                return outboundSocketBinding.getResolvedDestinationAddress();
            } catch (UnknownHostException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public int getPort() {
            return outboundSocketBinding.getDestinationPort();
        }
    }

    private static class DiscardingHandler extends Handler {

        @Override
        public void publish(final LogRecord record) {
            // Do nothing
        }

        @Override
        public void flush() {
            // Do nothing
        }

        @Override
        public void close() throws SecurityException {
            // Do nothing
        }
    }
}
