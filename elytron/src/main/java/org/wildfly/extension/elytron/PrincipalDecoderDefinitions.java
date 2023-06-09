/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.PRINCIPAL_DECODER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.common.ElytronReloadRequiredWriteAttributeHandler;
import org.wildfly.extension.elytron.common.TrivialCapabilityServiceRemoveHandler;
import org.wildfly.extension.elytron.common.TrivialService;
import org.wildfly.extension.elytron.common.TrivialService.ValueSupplier;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
import org.wildfly.extension.elytron.common.capabilities.PrincipalTransformer;
import org.wildfly.security.asn1.OidsUtil;
import org.wildfly.security.auth.server.PrincipalDecoder;
import org.wildfly.security.x500.principal.X500AttributePrincipalDecoder;


/**
 * Holder for {@link ResourceDefinition} instances for services that return {@link PrincipalDecoder}
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class PrincipalDecoderDefinitions {

    static final SimpleAttributeDefinition OID = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.OID, ModelType.STRING, false)
        .setAllowExpression(true)
        .setMinSize(1)
        .setAlternatives(ElytronDescriptionConstants.ATTRIBUTE_NAME)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition ATTRIBUTE_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ATTRIBUTE_NAME, ModelType.STRING, false)
        .setAllowExpression(true)
        .setMinSize(1)
        .setAlternatives(ElytronDescriptionConstants.OID)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition JOINER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.JOINER, ModelType.STRING, true)
        .setAllowExpression(true)
        .setMinSize(0)
        .setDefaultValue(new ModelNode("."))
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition START_SEGMENT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.START_SEGMENT, ModelType.INT, true)
        .setAllowExpression(true)
        .setDefaultValue(ModelNode.ZERO)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition MAXIMUM_SEGMENTS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MAXIMUM_SEGMENTS, ModelType.INT, true)
        .setAllowExpression(true)
        .setDefaultValue(new ModelNode(Integer.MAX_VALUE))
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition REVERSE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REVERSE, ModelType.BOOLEAN, true)
        .setAllowExpression(true)
        .setDefaultValue(ModelNode.FALSE)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition CONVERT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CONVERT, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setRestartAllServices()
            .build();

    static final StringListAttributeDefinition REQUIRED_OIDS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.REQUIRED_OIDS)
        .setRequired(false)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final StringListAttributeDefinition REQUIRED_ATTRIBUTES = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.REQUIRED_ATTRIBUTES)
        .setRequired(false)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition CONSTANT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CONSTANT, ModelType.STRING, false)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final StringListAttributeDefinition PRINCIPAL_DECODERS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.PRINCIPAL_DECODERS)
        .setMinSize(2)
        .setRequired(true)
        .setCapabilityReference(PRINCIPAL_DECODER_RUNTIME_CAPABILITY.getName(), PRINCIPAL_DECODER_RUNTIME_CAPABILITY.getName())
            .setAttributeParser(AttributeParsers.STRING_LIST_NAMED_ELEMENT)
            .setAttributeMarshaller(AttributeMarshallers.STRING_LIST_NAMED_ELEMENT)
            .setXmlName(ElytronDescriptionConstants.PRINCIPAL_DECODER)
        .setRestartAllServices()
        .build();

    private static final AggregateComponentDefinition<PrincipalDecoder> AGGREGATE_PRINCIPAL_DECODER = AggregateComponentDefinition.create(PrincipalDecoder.class,
            ElytronDescriptionConstants.AGGREGATE_PRINCIPAL_DECODER, ElytronDescriptionConstants.PRINCIPAL_DECODERS, PRINCIPAL_DECODER_RUNTIME_CAPABILITY, PrincipalDecoder::aggregate);

    static AggregateComponentDefinition<PrincipalDecoder> getAggregatePrincipalDecoderDefinition() {
        return AGGREGATE_PRINCIPAL_DECODER;
    }

    static ResourceDefinition getConstantPrincipalDecoder() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { CONSTANT };
        AbstractAddStepHandler add = new PrincipalDecoderAddHandler(attributes) {

            @Override
            protected ValueSupplier<PrincipalDecoder> getValueSupplier(ServiceBuilder<?> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {
                final String constant = CONSTANT.resolveModelAttribute(context, model).asString();
                return () -> PrincipalDecoder.constant(constant);
            }

        };

        return new PrincipalDecoderResourceDefinition(ElytronDescriptionConstants.CONSTANT_PRINCIPAL_DECODER, add, attributes);
    }

    static ResourceDefinition getX500AttributePrincipalDecoder() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { OID, ATTRIBUTE_NAME, JOINER, START_SEGMENT, MAXIMUM_SEGMENTS, REVERSE, CONVERT, REQUIRED_OIDS, REQUIRED_ATTRIBUTES };
        AbstractAddStepHandler add = new PrincipalDecoderAddHandler(attributes) {

            @Override
            protected ValueSupplier<PrincipalDecoder> getValueSupplier(ServiceBuilder<?> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {
                ModelNode oidNode = OID.resolveModelAttribute(context, model);
                ModelNode attributeNode = ATTRIBUTE_NAME.resolveModelAttribute(context, model);

                final String oid;
                if (oidNode.isDefined()) {
                    oid = oidNode.asString();
                } else if (attributeNode.isDefined()) {
                    oid = OidsUtil.attributeNameToOid(OidsUtil.Category.RDN, attributeNode.asString());
                    if (oid == null) {
                        throw ElytronSubsystemMessages.ROOT_LOGGER.unableToObtainOidForX500Attribute(attributeNode.asString());
                    }
                } else {
                    throw ElytronSubsystemMessages.ROOT_LOGGER.x500AttributeMustBeDefined();
                }

                final String joiner = JOINER.resolveModelAttribute(context, model).asString();
                final int startSegment = START_SEGMENT.resolveModelAttribute(context, model).asInt();
                final int maximumSegments = MAXIMUM_SEGMENTS.resolveModelAttribute(context, model).asInt();
                final boolean reverse = REVERSE.resolveModelAttribute(context, model).asBoolean();
                final boolean convert = CONVERT.resolveModelAttribute(context, model).asBoolean();

                final List<String> requiredOids = REQUIRED_OIDS.unwrap(context, model);
                final List<String> list = new ArrayList<>(requiredOids);
                for (String name : REQUIRED_ATTRIBUTES.unwrap(context, model)) {
                    String s = OidsUtil.attributeNameToOid(OidsUtil.Category.RDN, name);
                    list.add(s);
                }

                return () -> new X500AttributePrincipalDecoder(oid, joiner, startSegment, maximumSegments, reverse, convert, list.toArray(new String[list.size()]));
            }

        };

        return new PrincipalDecoderResourceDefinition(ElytronDescriptionConstants.X500_ATTRIBUTE_PRINCIPAL_DECODER, add, attributes);
    }

    static ResourceDefinition getConcatenatingPrincipalDecoder() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { JOINER, PRINCIPAL_DECODERS };

        AbstractAddStepHandler add = new PrincipalDecoderAddHandler(attributes) {

            @Override
            protected ValueSupplier<PrincipalDecoder> getValueSupplier(ServiceBuilder<?> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {
                final String joiner = JOINER.resolveModelAttribute(context, model).asString();
                final List<String> decoders = PRINCIPAL_DECODERS.unwrap(context, model);

                final List<InjectedValue<PrincipalDecoder>> principalDecoderInjectors = new ArrayList<>();
                final String baseCapabilityName = PRINCIPAL_DECODER_RUNTIME_CAPABILITY.getName();
                for (String decoder : decoders) {
                    InjectedValue<PrincipalDecoder> principalDecoderInjector = new InjectedValue<>();
                    String runtimeCapabilityName = RuntimeCapability.buildDynamicCapabilityName(baseCapabilityName, decoder);
                    ServiceName decoderServiceName = context.getCapabilityServiceName(runtimeCapabilityName, PrincipalDecoder.class);
                    serviceBuilder.addDependency(decoderServiceName, PrincipalDecoder.class, principalDecoderInjector);
                    principalDecoderInjectors.add(principalDecoderInjector);
                }
                return () -> {
                    final ArrayList<PrincipalDecoder> principalDecoders = new ArrayList<>(principalDecoderInjectors.size());
                    for (InjectedValue<PrincipalDecoder> current : principalDecoderInjectors) {
                        principalDecoders.add(current.getValue());
                    }
                    return PrincipalDecoder.concatenating(joiner, principalDecoders.toArray(new PrincipalDecoder[principalDecoders.size()]));
                };
            }

        };

        return new PrincipalDecoderResourceDefinition(ElytronDescriptionConstants.CONCATENATING_PRINCIPAL_DECODER, add, attributes);
    }

    private static class PrincipalDecoderResourceDefinition extends SimpleResourceDefinition {

        private final String pathKey;
        private final AttributeDefinition[] attributes;

        PrincipalDecoderResourceDefinition(String pathKey, AbstractAddStepHandler add, AttributeDefinition ... attributes) {
            super(new Parameters(PathElement.pathElement(pathKey),
                    ElytronExtension.getResourceDescriptionResolver(pathKey))
                .setAddHandler(add)
                .setRemoveHandler(new TrivialCapabilityServiceRemoveHandler(add, PRINCIPAL_DECODER_RUNTIME_CAPABILITY, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY))
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(PRINCIPAL_DECODER_RUNTIME_CAPABILITY, PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY));
            this.pathKey = pathKey;
            this.attributes = attributes;
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
             if (attributes != null && attributes.length > 0) {
                 AbstractWriteAttributeHandler write = new ElytronReloadRequiredWriteAttributeHandler(attributes);
                 for (AttributeDefinition current : attributes) {
                     resourceRegistration.registerReadWriteAttribute(current, null, write);
                 }
             }
        }

    }

    private static class PrincipalDecoderAddHandler extends BaseAddHandler {

        private static final Set<RuntimeCapability> CAPABILITIES;

        static {
            Set<RuntimeCapability> capabilities = new HashSet<>(2);
            capabilities.add(PRINCIPAL_DECODER_RUNTIME_CAPABILITY);
            capabilities.add(PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY);
            CAPABILITIES = capabilities;
        }

        private PrincipalDecoderAddHandler(AttributeDefinition ... attributes) {
            super(CAPABILITIES, attributes);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            RuntimeCapability<Void> decoderRuntimeCapability = PRINCIPAL_DECODER_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName decoderName = decoderRuntimeCapability.getCapabilityServiceName(PrincipalDecoder.class);

            ServiceTarget serviceTarget = context.getServiceTarget();

            TrivialService<PrincipalDecoder> principalDecoderService = new TrivialService<PrincipalDecoder>();
            ServiceBuilder<PrincipalDecoder> decoderBuilder = serviceTarget.addService(decoderName, principalDecoderService);
            principalDecoderService.setValueSupplier(getValueSupplier(decoderBuilder, context, model));

            commonDependencies(decoderBuilder)
                .setInitialMode(Mode.LAZY)
                .install();

            final InjectedValue<PrincipalDecoder> injectedDecoder = new InjectedValue<>();
            TrivialService<PrincipalTransformer> transformerService = new TrivialService<>(() -> PrincipalTransformer.from(injectedDecoder.getValue().asPrincipalRewriter()));

            RuntimeCapability<Void> transformerRuntimeCapability = PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName transformerName = transformerRuntimeCapability.getCapabilityServiceName(PrincipalTransformer.class);

            serviceTarget.addService(transformerName, transformerService)
                .addDependency(decoderName, PrincipalDecoder.class, injectedDecoder)
                .setInitialMode(Mode.LAZY)
                .install();
        }

        protected ValueSupplier<PrincipalDecoder> getValueSupplier(ServiceBuilder<?> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {
            return () -> null;
        }

    }

}
