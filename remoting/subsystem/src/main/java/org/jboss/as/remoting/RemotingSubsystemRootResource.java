/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.as.remoting;

import static org.jboss.as.remoting.Capabilities.HTTP_LISTENER_REGISTRY_CAPABILITY_NAME;
import static org.jboss.as.remoting.Capabilities.IO_WORKER_CAPABILITY_NAME;
import static org.jboss.as.remoting.Capabilities.REMOTING_ENDPOINT_CAPABILITY_NAME;

import java.util.logging.Level;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
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

    private static final String ENDPOINT = "endpoint";

    static final SimpleAttributeDefinition WORKER = new SimpleAttributeDefinitionBuilder(CommonAttributes.WORKER, ModelType.STRING)
            .setRequired(false)
            .setAttributeGroup(ENDPOINT)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setValidator(new StringLengthValidator(1))
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

    private static final String[] SERVER_ATTR_NAMES = new String[OPTIONS.length + 1];
    static {
        SERVER_ATTR_NAMES[0] = WORKER.getName();
        for (int i = 0; i < OPTIONS.length; i++) {
            SERVER_ATTR_NAMES[i + 1] = OPTIONS[i].getName();
        }
    }

    //The defaults for these come from XnioWorker
    static final SimpleAttributeDefinition WORKER_READ_THREADS = createWorkerAttribute(CommonAttributes.WORKER_READ_THREADS, Attribute.WORKER_READ_THREADS, 1);
    static final SimpleAttributeDefinition WORKER_TASK_CORE_THREADS = createWorkerAttribute(CommonAttributes.WORKER_TASK_CORE_THREADS, Attribute.WORKER_TASK_CORE_THREADS, 4);
    static final SimpleAttributeDefinition WORKER_TASK_KEEPALIVE = createWorkerAttribute(CommonAttributes.WORKER_TASK_KEEPALIVE, Attribute.WORKER_TASK_KEEPALIVE, 60);
    static final SimpleAttributeDefinition WORKER_TASK_LIMIT = createWorkerAttribute(CommonAttributes.WORKER_TASK_LIMIT, Attribute.WORKER_TASK_LIMIT, 0x4000);
    static final SimpleAttributeDefinition WORKER_TASK_MAX_THREADS = createWorkerAttribute(CommonAttributes.WORKER_TASK_MAX_THREADS, Attribute.WORKER_TASK_MAX_THREADS, 16);
    static final SimpleAttributeDefinition WORKER_WRITE_THREADS = createWorkerAttribute(CommonAttributes.WORKER_WRITE_THREADS, Attribute.WORKER_WRITE_THREADS, 1);


    static SimpleAttributeDefinition[] LEGACY_ATTRIBUTES = new SimpleAttributeDefinition[]{
            WORKER_READ_THREADS,
            WORKER_TASK_CORE_THREADS,
            WORKER_TASK_KEEPALIVE,
            WORKER_TASK_LIMIT,
            WORKER_TASK_MAX_THREADS,
            WORKER_WRITE_THREADS
    };

    private static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME);

    static ResourceDefinition create(ProcessType processType, boolean forDomain) {
        return new RemotingSubsystemRootResource(new Attributes(processType, forDomain));
    }

    private final Attributes attributes;

    private RemotingSubsystemRootResource(Attributes attributes) {
        super(new Parameters(PATH, RemotingExtension.getResourceDescriptionResolver(RemotingExtension.SUBSYSTEM_NAME))
                .setAddHandler(new RemotingSubsystemAdd(attributes))
                .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setRemoveHandler(new ReloadRequiredRemoveStepHandler())
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES)
                .setCapabilities(REMOTING_ENDPOINT_CAPABILITY, HTTP_LISTENER_REGISTRY_CAPABILITY)
        );
        this.attributes = attributes;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        if (attributes.legacy != null) {
            OperationStepHandler threadWrites = new ModelOnlyWriteAttributeHandler(attributes.legacy);
            for (final AttributeDefinition attribute : attributes.legacy) {
                resourceRegistration.registerReadWriteAttribute(attribute, null, threadWrites);
            }
        }

        resourceRegistration.registerReadWriteAttribute(attributes.worker, null, new WorkerAttributeWriteHandler());

        OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(attributes.options);
        for (final AttributeDefinition attribute: attributes.options) {
            resourceRegistration.registerReadWriteAttribute(attribute, null, writeHandler);
        }
    }

    private static OptionAttributeDefinition createOptionAttribute(String name, Option<?> option, ModelNode defaultValue) {
        OptionAttributeDefinition.Builder builder = OptionAttributeDefinition.builder(name, option)
                .setAllowExpression(true)
                .setAttributeGroup(ENDPOINT)
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES);
        if (defaultValue != null) {
            builder = builder.setDefaultValue(defaultValue);
        }
        return builder.build();
    }

    private static SimpleAttributeDefinition createWorkerAttribute(String name, Attribute attribute, int defaultValue) {
        return SimpleAttributeDefinitionBuilder.create(name, ModelType.INT, true)
                .setDefaultValue(new ModelNode().set(defaultValue))
                .setAlternatives(SERVER_ATTR_NAMES)
                .setXmlName(attribute.getLocalName())
                .setValidator(new IntRangeValidator(1))
                .setAllowExpression(true)
                .setDeprecated(ModelVersion.create(2,0))
                .build();
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

    static class Attributes {
        final boolean forDomain;
        private final AttributeDefinition[] legacy;
        private final AttributeDefinition worker;
        private final AttributeDefinition[] options;
        final AttributeDefinition[] all;

        private Attributes(ProcessType processType, boolean forDomain) {
            this.forDomain = forDomain;
            this.options = OPTIONS;
            if (processType.isServer()) {
                this.worker = WORKER;
                this.legacy = new AttributeDefinition[LEGACY_ATTRIBUTES.length];
                for (int i = 0; i < LEGACY_ATTRIBUTES.length; i++) {
                    // Reject any defined value, but that means there can't be a default (as that gets validated)
                    // Also, a default is incorrect on a server, as there really is no value
                    this.legacy[i] = SimpleAttributeDefinitionBuilder.create(LEGACY_ATTRIBUTES[i])
                            .setValidator(WorkerThreadValidator.INSTANCE)
                            .setDefaultValue(null)
                            .build();
                }
            } else if (forDomain) {
                this.worker = SimpleAttributeDefinitionBuilder.create(WORKER).setCapabilityReference((CapabilityReferenceRecorder) null).build();
                this.legacy = LEGACY_ATTRIBUTES;
            } else {
                this.worker = WORKER;
                this.legacy = null;
            }
            int count = options.length + 1 + (legacy == null ? 0 : legacy.length);
            all = new AttributeDefinition[count];
            int idx = 0;
            if (legacy != null) {
                System.arraycopy(legacy, 0, all, 0, legacy.length);
                idx += legacy.length;
            }
            all[idx] = worker;
            System.arraycopy(options, 0, all, idx + 1, options.length);
        }
    }

    private static class WorkerThreadValidator implements ParameterValidator {

        private static final ParameterValidator INSTANCE = new WorkerThreadValidator();

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            if (value.isDefined()) {
                throw RemotingLogger.ROOT_LOGGER.workerConfigurationIgnored();
            }
        }
    }
}
