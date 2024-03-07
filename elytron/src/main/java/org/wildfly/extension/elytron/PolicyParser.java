/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;

import static org.wildfly.extension.elytron.ElytronDescriptionConstants.JACC_POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.POLICY;

class PolicyParser {

    PersistentResourceXMLDescription parser_1_0 = PersistentResourceXMLDescription.builder(PathElement.pathElement(POLICY))
            .setNameAttributeName(PolicyDefinitions.DEFAULT_POLICY.getName())
            .addAttribute(PolicyDefinitions.DEFAULT_POLICY)
            .addAttribute(JaccPolicyDefinition.POLICIES, AttributeParsers.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshallers.OBJECT_LIST_UNWRAPPED)
            .addAttribute(CustomPolicyDefinition.POLICIES, AttributeParsers.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshallers.OBJECT_LIST_UNWRAPPED)
            .build();

    PersistentResourceXMLDescription parser_1_2 = PersistentResourceXMLDescription.builder(PathElement.pathElement(POLICY))
            .addAttribute(PolicyDefinitions.JaccPolicyDefinition.POLICY)
                .addAttribute(PolicyDefinitions.CustomPolicyDefinition.POLICY)
                .build();

    private static class JaccPolicyDefinition {
        static ObjectTypeAttributeDefinition POLICY = new ObjectTypeAttributeDefinition.Builder(JACC_POLICY, PolicyDefinitions.RESOURCE_NAME, PolicyDefinitions.JaccPolicyDefinition.POLICY_PROVIDER, PolicyDefinitions.JaccPolicyDefinition.CONFIGURATION_FACTORY, PolicyDefinitions.JaccPolicyDefinition.MODULE).build();
        static final ObjectListAttributeDefinition POLICIES = new ObjectListAttributeDefinition.Builder(JACC_POLICY, POLICY)
                .setMinSize(1)
                .setRequired(false)
                .build();
    }

    private static class CustomPolicyDefinition {
        static ObjectTypeAttributeDefinition POLICY = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.CUSTOM_POLICY, PolicyDefinitions.RESOURCE_NAME, PolicyDefinitions.CustomPolicyDefinition.CLASS_NAME, PolicyDefinitions.CustomPolicyDefinition.MODULE).build();
        static final ObjectListAttributeDefinition POLICIES = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.CUSTOM_POLICY, POLICY)
                .setRequired(false)
                .build();
    }
}
