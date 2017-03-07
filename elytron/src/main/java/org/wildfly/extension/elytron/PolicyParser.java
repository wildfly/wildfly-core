/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CLASS_NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONFIGURATION_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CUSTOM_POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DEFAULT_POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.JACC_POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MODULE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.POLICY;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.verifyNamespace;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.extension.elytron.PolicyDefinitions.CustomPolicyDefinition;
import org.wildfly.extension.elytron.PolicyDefinitions.JaccPolicyDefinition;

/**
 * A parser for the security realm definition.
 *
 * <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class PolicyParser {

    void readPolicy(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addPolicy = new ModelNode();

        addPolicy.get(OP).set(ADD);

        String defaultPolicy = null;
        final int count = reader.getAttributeCount();

        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case DEFAULT_POLICY:
                        defaultPolicy = value;
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        boolean providerFound = defaultPolicy == null;

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            switch (localName) {
                // Permission Mapper
                case JACC_POLICY:
                    providerFound = defaultPolicy.equals(parseJaccPolicy(addPolicy, reader, operations)) || providerFound;
                    break;
                case CUSTOM_POLICY:
                    providerFound = defaultPolicy.equals(parseCustomPolicy(addPolicy, reader, operations)) || providerFound;
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }

        if (!providerFound) {
            throw missingRequired(reader, DEFAULT_POLICY);
        }

        addPolicy.get(OP_ADDR).set(parentAddress).add(POLICY, defaultPolicy);
        operations.add(addPolicy);
    }

    private String parseJaccPolicy(ModelNode policyModel, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode providerModel = new ModelNode();
        Set<String> requiredAttributes = new HashSet<>(Arrays.asList(new String[] { NAME }));
        String name = null;

        for (int i = 0; i < reader.getAttributeCount(); i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        JaccPolicyDefinition.NAME.parseAndSetParameter(value, providerModel, reader);
                        break;
                    case POLICY:
                        JaccPolicyDefinition.POLICY_PROVIDER.parseAndSetParameter(value, providerModel, reader);
                        break;
                    case CONFIGURATION_FACTORY:
                        JaccPolicyDefinition.CONFIGURATION_FACTORY.parseAndSetParameter(value, providerModel, reader);
                        break;
                    case MODULE:
                        JaccPolicyDefinition.MODULE.parseAndSetParameter(value, providerModel, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        requireNoContent(reader);

        ModelNode policies = policyModel.get(JACC_POLICY);

        if (!policies.isDefined()) {
            policies.setEmptyList();
        }

        policies.add(providerModel);

        return name;
    }

    private String parseCustomPolicy(ModelNode policyModel, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode providerModel = new ModelNode();
        Set<String> requiredAttributes = new HashSet<>(Arrays.asList(new String[] { NAME, CLASS_NAME }));
        String name = null;

        for (int i = 0; i < reader.getAttributeCount(); i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        CustomPolicyDefinition.NAME.parseAndSetParameter(value, providerModel, reader);
                        break;
                    case CLASS_NAME:
                        CustomPolicyDefinition.CLASS_NAME.parseAndSetParameter(value, providerModel, reader);
                        break;
                    case MODULE:
                        CustomPolicyDefinition.MODULE.parseAndSetParameter(value, providerModel, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        requireNoContent(reader);

        ModelNode policies = policyModel.get(CUSTOM_POLICY);

        if (!policies.isDefined()) {
            policies.setEmptyList();
        }

        policies.add(providerModel);

        return name;
    }

    void writePolicy(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (!subsystem.hasDefined(POLICY)) {
            return;
        }

        Property policy = subsystem.get(POLICY).asProperty();
        String defaultPolicy = policy.getName();
        ModelNode policyModel = policy.getValue();

        if (policyModel.get(DEFAULT_POLICY).isDefined()) {
            defaultPolicy = policyModel.get(DEFAULT_POLICY).asString();
        }

        boolean mappersStarted = false;

        mappersStarted = mappersStarted | writeJaccPolicy(mappersStarted, defaultPolicy, policyModel, writer);
        mappersStarted = mappersStarted | writeCustomPolicy(mappersStarted, defaultPolicy, policyModel, writer);

        if (mappersStarted) {
            writer.writeEndElement();
        }
    }

    private boolean writeJaccPolicy(boolean started, String defaultPolicy, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(JACC_POLICY)) {
            ModelNode jaccPolicies = subsystem.get(JACC_POLICY);

            startPolicy(started, defaultPolicy, subsystem, writer);

            for (ModelNode jaccPolicy : jaccPolicies.asList()) {
                writer.writeStartElement(JACC_POLICY);

                JaccPolicyDefinition.NAME.marshallAsAttribute(jaccPolicy, writer);
                JaccPolicyDefinition.POLICY_PROVIDER.marshallAsAttribute(jaccPolicy, writer);
                JaccPolicyDefinition.CONFIGURATION_FACTORY.marshallAsAttribute(jaccPolicy, writer);
                JaccPolicyDefinition.MODULE.marshallAsAttribute(jaccPolicy, writer);

                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeCustomPolicy(boolean started, String defaultPolicy, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CUSTOM_POLICY)) {
            ModelNode jaccPolicies = subsystem.get(CUSTOM_POLICY);

            startPolicy(started, defaultPolicy, subsystem, writer);

            for (ModelNode customPolicy : jaccPolicies.asList()) {
                writer.writeStartElement(CUSTOM_POLICY);

                CustomPolicyDefinition.NAME.marshallAsAttribute(customPolicy, writer);
                CustomPolicyDefinition.CLASS_NAME.marshallAsAttribute(customPolicy, writer);
                CustomPolicyDefinition.MODULE.marshallAsAttribute(customPolicy, writer);

                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private void startPolicy(boolean started, String defaultPolicy, ModelNode policyModel, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (started == false) {
            writer.writeStartElement(POLICY);
            writer.writeAttribute(DEFAULT_POLICY, defaultPolicy);
        }
    }
}

