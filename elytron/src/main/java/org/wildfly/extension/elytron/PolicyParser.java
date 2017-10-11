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
import static org.wildfly.extension.elytron.ElytronExtension.NAMESPACE_1_0;
import static org.wildfly.extension.elytron.ElytronExtension.NAMESPACE_1_1;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.extension.elytron.PolicyDefinitions.CustomPolicyDefinition;
import org.wildfly.extension.elytron.PolicyDefinitions.JaccPolicyDefinition;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;

/**
 * A parser for the security realm definition.
 *
 * <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class PolicyParser {

    private final ElytronSubsystemParser elytronSubsystemParser;
    private volatile PersistentResourceXMLDescription description;

    PolicyParser(ElytronSubsystemParser elytronSubsystemParser) {
        this.elytronSubsystemParser = elytronSubsystemParser;
    }

    private void verifyNamespace(XMLExtendedStreamReader reader) throws XMLStreamException {
        elytronSubsystemParser.verifyNamespace(reader);
    }

    private PersistentResourceXMLDescription getXmlDescription() {
        if (description == null) {
            String namespace = elytronSubsystemParser.getNamespace();
            if (!namespace.equals(NAMESPACE_1_0) && !namespace.equals(NAMESPACE_1_1)) {
                throw new IllegalStateException("TODO");
            }
        }
        return description;
    }

    void readPolicy(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        PersistentResourceXMLDescription xmlDescription = getXmlDescription();
        if (xmlDescription == null) {
            readPolicyLegacy(parentAddress, reader, operations);
        } else {
            description.parse(reader, PathAddress.pathAddress(parentAddress), operations);
        }
    }

    private void readPolicyLegacy(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
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
                        // Use an AD to validate the resource name, but don't add the value to the op
                        // It's just the value in the resource address
                        ModelNode placeholder = new ModelNode().setEmptyObject();
                        PolicyDefinitions.RESOURCE_NAME.parseAndSetParameter(value, placeholder, reader);
                        defaultPolicy = value;
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (defaultPolicy == null) {
            throw missingRequired(reader, DEFAULT_POLICY);
        }

        boolean providerFound = false;

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            switch (localName) {
                // Permission Mapper
                case JACC_POLICY: {
                    ModelNode providerModel = new ModelNode().setEmptyObject();
                    String policyName = parseJaccPolicy(providerModel, reader);
                    if (!providerFound && defaultPolicy.equals(policyName)) {
                        providerFound = true;
                        addPolicy.get(JACC_POLICY).set(providerModel);
                    }  else {
                        //ignore it but don't reject it as the xsd says it's allowed
                        // But log a WARN to help notify the user
                        ElytronSubsystemMessages.ROOT_LOGGER.discardingUnusedPolicy(JACC_POLICY, NAME, policyName);
                    }
                    break;
                }
                case CUSTOM_POLICY: {
                    ModelNode providerModel = new ModelNode().setEmptyObject();
                    String policyName = parseCustomPolicy(providerModel, reader);
                    if (!providerFound && defaultPolicy.equals(policyName)) {
                        providerFound = true;
                        addPolicy.get(CUSTOM_POLICY).set(providerModel);
                    } else {
                        // ignore it but don't reject it as the xsd says it's allowed.
                        // But log a WARN to help notify the user
                        ElytronSubsystemMessages.ROOT_LOGGER.discardingUnusedPolicy(CUSTOM_POLICY, NAME, policyName);
                    }
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }

        if (!providerFound) {
            throw ElytronSubsystemMessages.ROOT_LOGGER.cannotFindPolicyProvider(defaultPolicy, reader.getLocation());
        }

        addPolicy.get(OP_ADDR).set(parentAddress).add(POLICY, defaultPolicy);
        operations.add(addPolicy);
    }

    private String parseJaccPolicy(ModelNode providerModel, XMLExtendedStreamReader reader)
            throws XMLStreamException {
        Set<String> requiredAttributes = new HashSet<>(Collections.singletonList(NAME));
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

        return name;
    }

    private String parseCustomPolicy(ModelNode providerModel, XMLExtendedStreamReader reader)
            throws XMLStreamException {
        Set<String> requiredAttributes = new HashSet<>(Arrays.asList(NAME, CLASS_NAME));
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

        return name;
    }

    void writePolicy(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        PersistentResourceXMLDescription xmlDescription = getXmlDescription();
        if (xmlDescription == null) {
            writePolicyLegacy(subsystem, writer);
        } else {
            xmlDescription.persist(writer, subsystem);
        }
    }

    private void writePolicyLegacy(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {

        if (!subsystem.hasDefined(POLICY)) {
            return;
        }

        Property policy = subsystem.get(POLICY).asProperty();
        String defaultPolicy = policy.getName();
        ModelNode policyModel = policy.getValue();

        boolean mappersStarted = writeJaccPolicy(defaultPolicy, policyModel, writer);
        mappersStarted = mappersStarted | writeCustomPolicy(mappersStarted, defaultPolicy, policyModel, writer);

        if (mappersStarted) {
            writer.writeEndElement();
        }
    }

    private boolean writeJaccPolicy(String defaultPolicy, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(JACC_POLICY)) {
            ModelNode jaccPolicy = subsystem.get(JACC_POLICY);

            startPolicy(false, defaultPolicy, writer);

            writer.writeStartElement(JACC_POLICY);

            writer.writeAttribute(NAME, defaultPolicy);
            JaccPolicyDefinition.POLICY_PROVIDER.marshallAsAttribute(jaccPolicy, writer);
            JaccPolicyDefinition.CONFIGURATION_FACTORY.marshallAsAttribute(jaccPolicy, writer);
            JaccPolicyDefinition.MODULE.marshallAsAttribute(jaccPolicy, writer);

            writer.writeEndElement();

            return true;
        }

        return false;
    }

    private boolean writeCustomPolicy(boolean started, String defaultPolicy, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CUSTOM_POLICY)) {
            ModelNode customPolicy = subsystem.get(CUSTOM_POLICY);

            startPolicy(started, defaultPolicy, writer);

            writer.writeStartElement(CUSTOM_POLICY);

            writer.writeAttribute(NAME, defaultPolicy);
            CustomPolicyDefinition.CLASS_NAME.marshallAsAttribute(customPolicy, writer);
            CustomPolicyDefinition.MODULE.marshallAsAttribute(customPolicy, writer);

            writer.writeEndElement();

            return true;
        }

        return false;
    }

    private void startPolicy(boolean started, String defaultPolicy, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (started == false) {
            writer.writeStartElement(POLICY);
            writer.writeAttribute(DEFAULT_POLICY, defaultPolicy);
        }
    }
}

