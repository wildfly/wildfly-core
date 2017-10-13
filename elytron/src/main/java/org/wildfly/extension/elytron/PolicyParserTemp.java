/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CUSTOM_POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DEFAULT_POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.JACC_POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.POLICY;
import static org.wildfly.extension.elytron.ElytronExtension.NAMESPACE_1_0;
import static org.wildfly.extension.elytron.ElytronExtension.NAMESPACE_1_1;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Temporary replacement for PolicyParser so the WFCORE-2882 work can be applied
 * with minimal conflicts.
 *
 * @author Brian Stansberry
 */
class PolicyParserTemp  {

    private final PersistentResourceXMLDescription policyParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(POLICY))
            .addAttribute(PolicyDefinitions.JaccPolicyDefinition.POLICY)
            .addAttribute(PolicyDefinitions.CustomPolicyDefinition.POLICY)
            .build();

    private final PersistentResourceXMLDescription legacyPolicyParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(POLICY))
            .setNameAttributeName(DEFAULT_POLICY)
            .addAttribute(new ObjectListAttributeDefinition.Builder(JACC_POLICY,
                    new ObjectTypeAttributeDefinition.Builder(JACC_POLICY,
                            PolicyDefinitions.RESOURCE_NAME,
                            PolicyDefinitions.JaccPolicyDefinition.POLICY_PROVIDER,
                            PolicyDefinitions.JaccPolicyDefinition.CONFIGURATION_FACTORY,
                            PolicyDefinitions.JaccPolicyDefinition.MODULE)
                            .build())
                    .setRequired(false)
                    .setMaxSize(1) // xsd says > 1 is ok, but the resource will reject that, so might as well reject in the parser
                    .setAttributeParser(AttributeParsers.UNWRAPPED_OBJECT_LIST_PARSER)
                    .build())
            .addAttribute(new ObjectListAttributeDefinition.Builder(CUSTOM_POLICY,
                    new ObjectTypeAttributeDefinition.Builder(CUSTOM_POLICY,
                            PolicyDefinitions.RESOURCE_NAME,
                            PolicyDefinitions.CustomPolicyDefinition.CLASS_NAME,
                            PolicyDefinitions.CustomPolicyDefinition.MODULE)
                            .build())
                    .setRequired(false)
                    .setMaxSize(1)  // xsd says > 1 is ok, but the resource will reject that, so might as well reject in the parser
                    .setAttributeParser(AttributeParsers.UNWRAPPED_OBJECT_LIST_PARSER)
                    .build())
            .build();

    private final ElytronSubsystemParser elytronSubsystemParser;

    PolicyParserTemp(ElytronSubsystemParser elytronSubsystemParser) {
        this.elytronSubsystemParser = elytronSubsystemParser;
    }

    void readPolicy(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        PersistentResourceXMLDescription description = isLegacy() ? legacyPolicyParser : policyParser;
        description.parse(reader, PathAddress.pathAddress(parentAddress), operations);
    }

    void writePolicy(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (isLegacy()) {
            throw new UnsupportedOperationException();
        } else {
            policyParser.persist(writer, subsystem);
        }
    }

    private boolean isLegacy() {
        String namespace = elytronSubsystemParser.getNamespace();
        return (namespace.equals(NAMESPACE_1_0) || namespace.equals(NAMESPACE_1_1));
    }
}
