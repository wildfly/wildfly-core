/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import static org.jboss.as.remoting.Capabilities.HTTP_LISTENER_REGISTRY_CAPABILITY_NAME;
import static org.jboss.as.remoting.Capabilities.IO_WORKER_CAPABILITY_NAME;
import static org.jboss.as.remoting.Capabilities.REMOTING_ENDPOINT_CAPABILITY_NAME;

import java.util.Collection;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.RemotingOptions;
import org.wildfly.extension.io.OptionAttributeDefinition;
import org.xnio.Option;

import io.undertow.server.ListenerRegistry;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class RemotingSubsystemRootResource extends SimpleResourceDefinition {

    static final RuntimeCapability<Void> REMOTING_ENDPOINT_CAPABILITY =
            RuntimeCapability.Builder.of(REMOTING_ENDPOINT_CAPABILITY_NAME, Endpoint.class).build();

    static final RuntimeCapability<Void> HTTP_LISTENER_REGISTRY_CAPABILITY =
            RuntimeCapability.Builder.of(HTTP_LISTENER_REGISTRY_CAPABILITY_NAME, ListenerRegistry.class).build();

    static final SimpleAttributeDefinition WORKER = new SimpleAttributeDefinitionBuilder(CommonAttributes.WORKER, ModelType.STRING)
            .setRequired(false)
            .setAttributeGroup(Element.ENDPOINT.getLocalName())
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setValidator(new StringLengthValidator(1))
            // TODO WFCORE-6490 Attributes that reference capabilities should never have a default value.
            .setDefaultValue(new ModelNode("default"))
            .setCapabilityReference(IO_WORKER_CAPABILITY_NAME, REMOTING_ENDPOINT_CAPABILITY)
            .build();

    private static final OptionAttributeDefinition SEND_BUFFER_SIZE = createOptionAttribute("send-buffer-size", RemotingOptions.SEND_BUFFER_SIZE, new ModelNode(RemotingOptions.DEFAULT_SEND_BUFFER_SIZE));
    private static final OptionAttributeDefinition RECEIVE_BUFFER_SIZE = createOptionAttribute("receive-buffer-size", RemotingOptions.RECEIVE_BUFFER_SIZE, new ModelNode(RemotingOptions.DEFAULT_RECEIVE_BUFFER_SIZE));
    private static final OptionAttributeDefinition BUFFER_REGION_SIZE = createOptionAttribute("buffer-region-size", RemotingOptions.BUFFER_REGION_SIZE, null);
    private static final OptionAttributeDefinition TRANSMIT_WINDOW_SIZE = createOptionAttribute("transmit-window-size", RemotingOptions.TRANSMIT_WINDOW_SIZE, new ModelNode(RemotingOptions.INCOMING_CHANNEL_DEFAULT_TRANSMIT_WINDOW_SIZE));
    private static final OptionAttributeDefinition RECEIVE_WINDOW_SIZE = createOptionAttribute("receive-window-size", RemotingOptions.RECEIVE_WINDOW_SIZE, new ModelNode(RemotingOptions.INCOMING_CHANNEL_DEFAULT_RECEIVE_WINDOW_SIZE));
    private static final OptionAttributeDefinition MAX_OUTBOUND_CHANNELS = createOptionAttribute("max-outbound-channels", RemotingOptions.MAX_OUTBOUND_CHANNELS, new ModelNode(RemotingOptions.DEFAULT_MAX_OUTBOUND_CHANNELS));
    private static final OptionAttributeDefinition MAX_INBOUND_CHANNELS = createOptionAttribute("max-inbound-channels", RemotingOptions.MAX_INBOUND_CHANNELS, new ModelNode(RemotingOptions.DEFAULT_MAX_INBOUND_CHANNELS));
    private static final OptionAttributeDefinition AUTHORIZE_ID = createOptionAttribute("authorize-id", RemotingOptions.AUTHORIZE_ID, null);
    private static final OptionAttributeDefinition AUTH_REALM = createOptionAttribute("auth-realm", RemotingOptions.AUTH_REALM, null);
    private static final OptionAttributeDefinition AUTHENTICATION_RETRIES = createOptionAttribute("authentication-retries", RemotingOptions.AUTHENTICATION_RETRIES, new ModelNode(RemotingOptions.DEFAULT_AUTHENTICATION_RETRIES));
    private static final OptionAttributeDefinition MAX_OUTBOUND_MESSAGES = createOptionAttribute("max-outbound-messages", RemotingOptions.MAX_OUTBOUND_MESSAGES, new ModelNode(RemotingOptions.OUTGOING_CHANNEL_DEFAULT_MAX_OUTBOUND_MESSAGES));
    private static final OptionAttributeDefinition MAX_INBOUND_MESSAGES = createOptionAttribute("max-inbound-messages", RemotingOptions.MAX_INBOUND_MESSAGES, new ModelNode(RemotingOptions.INCOMING_CHANNEL_DEFAULT_MAX_OUTBOUND_MESSAGES));
    private static final OptionAttributeDefinition HEARTBEAT_INTERVAL = createOptionAttribute("heartbeat-interval", RemotingOptions.HEARTBEAT_INTERVAL, new ModelNode(RemotingOptions.DEFAULT_HEARTBEAT_INTERVAL));
    private static final OptionAttributeDefinition MAX_INBOUND_MESSAGE_SIZE = createOptionAttribute("max-inbound-message-size", RemotingOptions.MAX_INBOUND_MESSAGE_SIZE, new ModelNode(RemotingOptions.DEFAULT_MAX_INBOUND_MESSAGE_SIZE));
    private static final OptionAttributeDefinition MAX_OUTBOUND_MESSAGE_SIZE = createOptionAttribute("max-outbound-message-size", RemotingOptions.MAX_OUTBOUND_MESSAGE_SIZE, new ModelNode(RemotingOptions.DEFAULT_MAX_OUTBOUND_MESSAGE_SIZE));
    private static final OptionAttributeDefinition SERVER_NAME = createOptionAttribute("server-name", RemotingOptions.SERVER_NAME, null);
    static final OptionAttributeDefinition SASL_PROTOCOL = createOptionAttribute("sasl-protocol", RemotingOptions.SASL_PROTOCOL, new ModelNode(RemotingOptions.DEFAULT_SASL_PROTOCOL));

    static final OptionAttributeDefinition[] OPTIONS = new OptionAttributeDefinition[] {
            SEND_BUFFER_SIZE, RECEIVE_BUFFER_SIZE, BUFFER_REGION_SIZE, TRANSMIT_WINDOW_SIZE, RECEIVE_WINDOW_SIZE,
            MAX_OUTBOUND_CHANNELS, MAX_INBOUND_CHANNELS, AUTHORIZE_ID, AUTH_REALM, AUTHENTICATION_RETRIES, MAX_OUTBOUND_MESSAGES,
            MAX_INBOUND_MESSAGES, HEARTBEAT_INTERVAL, MAX_INBOUND_MESSAGE_SIZE, MAX_OUTBOUND_MESSAGE_SIZE, SERVER_NAME, SASL_PROTOCOL
    };
    static final Collection<AttributeDefinition> ATTRIBUTES = Stream.concat(Stream.of(WORKER), Stream.of(OPTIONS)).collect(Collectors.toList());

    static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME);

    RemotingSubsystemRootResource() {
        super(new Parameters(PATH, RemotingExtension.getResourceDescriptionResolver(RemotingExtension.SUBSYSTEM_NAME))
                .setAddHandler(new RemotingSubsystemAdd())
                .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES)
                .setCapabilities(REMOTING_ENDPOINT_CAPABILITY, HTTP_LISTENER_REGISTRY_CAPABILITY)
        );
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(WORKER, null, new WorkerAttributeWriteHandler());

        OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(OPTIONS);
        for (final AttributeDefinition attribute: OPTIONS) {
            resourceRegistration.registerReadWriteAttribute(attribute, null, writeHandler);
        }
    }

    private static OptionAttributeDefinition createOptionAttribute(String name, Option<?> option, ModelNode defaultValue) {
        OptionAttributeDefinition.Builder builder = OptionAttributeDefinition.builder(name, option)
                .setAllowExpression(true)
                .setAttributeGroup(Element.ENDPOINT.getLocalName())
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES);
        if (defaultValue != null) {
            builder = builder.setDefaultValue(defaultValue);
        }
        return builder.build();
    }

    private static class WorkerAttributeWriteHandler extends ReloadRequiredWriteAttributeHandler {

        WorkerAttributeWriteHandler() {
            super(WORKER);
        }

        @Override
        protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue,
                ModelNode oldValue, Resource model) throws OperationFailedException {
            super.finishModelStage(context, operation, attributeName, newValue, oldValue, model);
            context.addResponseWarning(Level.WARNING, RemotingLogger.ROOT_LOGGER.warningOnWorkerChange(newValue.asString()));
        }
    }
}
