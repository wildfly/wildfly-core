/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

import static org.wildfly.extension.elytron.ElytronCommonCapabilities.EVIDENCE_DECODER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronCommonConstants.X500_SUBJECT_EVIDENCE_DECODER;
import static org.wildfly.extension.elytron.ElytronCommonConstants.X509_SUBJECT_ALT_NAME_EVIDENCE_DECODER;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.StringAllowedValuesValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.security.auth.server.EvidenceDecoder;
import org.wildfly.security.x500.GeneralName;
import org.wildfly.security.x500.principal.X500SubjectEvidenceDecoder;
import org.wildfly.security.x500.principal.X509SubjectAltNameEvidenceDecoder;

/**
 * Holder for {@link ResourceDefinition} instances for services that return an {@link EvidenceDecoder}.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
class EvidenceDecoderDefinitions {

    private static final String RFC_822_NAME = "rfc822Name";
    private static final String DNS_NAME = "dNSName";
    private static final String DIRECTORY_NAME = "directoryName";
    private static final String URI_NAME = "uniformResourceIdentifier";
    private static final String IP_ADDRESS = "iPAddress";
    private static final String REGISTERED_ID = "registeredID";

    private enum SubjectAltNameType {

        RFC_822_NAME_TYPE(RFC_822_NAME, GeneralName.RFC_822_NAME),
        DNS_NAME_TYPE(DNS_NAME, GeneralName.DNS_NAME),
        DIRECTORY_NAME_TYPE(DIRECTORY_NAME, GeneralName.DIRECTORY_NAME),
        URI_NAME_TYPE(URI_NAME, GeneralName.URI_NAME),
        IP_ADDRESS_TYPE(IP_ADDRESS, GeneralName.IP_ADDRESS),
        REGISTERED_ID_TYPE(REGISTERED_ID, GeneralName.REGISTERED_ID);

        private final String name;
        private final int type;

        SubjectAltNameType(final String name, final int type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public int getType() {
            return type;
        }

        public static SubjectAltNameType fromName(final String name) {
            switch (name) {
                case RFC_822_NAME:
                    return RFC_822_NAME_TYPE;
                case DNS_NAME:
                    return DNS_NAME_TYPE;
                case DIRECTORY_NAME:
                    return DIRECTORY_NAME_TYPE;
                case URI_NAME:
                    return URI_NAME_TYPE;
                case IP_ADDRESS:
                    return IP_ADDRESS_TYPE;
                case REGISTERED_ID:
                    return REGISTERED_ID_TYPE;
                default:
                    throw new IllegalArgumentException(name);
            }
        }
    }

    static final SimpleAttributeDefinition ALT_NAME_TYPE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.ALT_NAME_TYPE, ModelType.STRING, false)
            .setValidator(new StringAllowedValuesValidator(RFC_822_NAME, DNS_NAME, DIRECTORY_NAME, URI_NAME, IP_ADDRESS, REGISTERED_ID))
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SEGMENT = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.SEGMENT, ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.ZERO)
            .setValidator(new IntRangeValidator(0, true, true))
            .setRestartAllServices()
            .build();

    private static final ElytronCommonAggregateComponentDefinition<EvidenceDecoder> AGGREGATE_EVIDENCE_DECODER = ElytronCommonAggregateComponentDefinition.create(EvidenceDecoder.class,
            ElytronCommonConstants.AGGREGATE_EVIDENCE_DECODER, ElytronCommonConstants.EVIDENCE_DECODERS, EVIDENCE_DECODER_RUNTIME_CAPABILITY, EvidenceDecoder::aggregate);

    static ResourceDefinition getX500SubjectEvidenceDecoderDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] {};
        AbstractAddStepHandler add = new ElytronCommonTrivialAddHandler<EvidenceDecoder>(EvidenceDecoder.class, attributes, EVIDENCE_DECODER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<EvidenceDecoder> getValueSupplier(ServiceBuilder<EvidenceDecoder> serviceBuilder,
                                                                           OperationContext context, ModelNode model) throws OperationFailedException {
                return () -> new X500SubjectEvidenceDecoder();
            }
        };
        return new ElytronCommonTrivialResourceDefinition(X500_SUBJECT_EVIDENCE_DECODER, add, attributes, EVIDENCE_DECODER_RUNTIME_CAPABILITY);
    }

    static ResourceDefinition getX509SubjectAltNameEvidenceDecoderDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] { ALT_NAME_TYPE, SEGMENT };
        AbstractAddStepHandler add = new ElytronCommonTrivialAddHandler<EvidenceDecoder>(EvidenceDecoder.class, attributes, EVIDENCE_DECODER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<EvidenceDecoder> getValueSupplier(ServiceBuilder<EvidenceDecoder> serviceBuilder,
                                                                           OperationContext context, ModelNode model) throws OperationFailedException {
                final String  altNameType = ALT_NAME_TYPE.resolveModelAttribute(context, model).asString();
                final int segment  = SEGMENT.resolveModelAttribute(context, model).asInt();
                return () -> new X509SubjectAltNameEvidenceDecoder(SubjectAltNameType.fromName(altNameType).getType(), segment);
            }
        };
        return new ElytronCommonTrivialResourceDefinition(X509_SUBJECT_ALT_NAME_EVIDENCE_DECODER, add, attributes, EVIDENCE_DECODER_RUNTIME_CAPABILITY);
    }

    static ElytronCommonAggregateComponentDefinition<EvidenceDecoder> getAggregateEvidenceDecoderDefinition() {
        return AGGREGATE_EVIDENCE_DECODER;
    }

}
