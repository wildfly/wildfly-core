/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DIR_CONTEXTS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.JACC_POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_DOMAIN;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_DOMAINS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_PROPERTY;

import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;

/**
 * The subsystem parser, which uses stax to read and write to and from xml
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a> *
 * @author Tomaz Cerar
 */
class ElytronSubsystemParser1_0 extends PersistentResourceXMLParser {

    final PersistentResourceXMLDescription domainParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(SECURITY_DOMAIN))
            .setXmlWrapperElement(SECURITY_DOMAINS)
            .addAttribute(DomainDefinition.DEFAULT_REALM)
            .addAttribute(DomainDefinition.PERMISSION_MAPPER)
            .addAttribute(DomainDefinition.PRE_REALM_PRINCIPAL_TRANSFORMER)
            .addAttribute(DomainDefinition.POST_REALM_PRINCIPAL_TRANSFORMER)
            .addAttribute(DomainDefinition.PRINCIPAL_DECODER)
            .addAttribute(DomainDefinition.REALM_MAPPER)
            .addAttribute(DomainDefinition.ROLE_MAPPER)
            .addAttribute(DomainDefinition.TRUSTED_SECURITY_DOMAINS)
            .addAttribute(DomainDefinition.OUTFLOW_ANONYMOUS)
            .addAttribute(DomainDefinition.OUTFLOW_SECURITY_DOMAINS)
            .addAttribute(DomainDefinition.SECURITY_EVENT_LISTENER)
            .addAttribute(DomainDefinition.REALMS)
            .build();

    final PersistentResourceXMLDescription dirContextParser = PersistentResourceXMLDescription.decorator(DIR_CONTEXTS)
            .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.DIR_CONTEXT))
                    .addAttributes(DirContextDefinition.ATTRIBUTES))
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

    private final PersistentResourceXMLDescription policyParser = builder(PathElement.pathElement(POLICY))
            .setNameAttributeName(PolicyDefinitions.DEFAULT_POLICY.getName())
            .addAttribute(PolicyDefinitions.DEFAULT_POLICY)
            .addAttribute(JaccPolicyDefinition.POLICIES, AttributeParsers.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshallers.OBJECT_LIST_UNWRAPPED)
            .addAttribute(CustomPolicyDefinition.POLICIES, AttributeParsers.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshallers.OBJECT_LIST_UNWRAPPED)
            .build();

    PersistentResourceXMLDescription getMapperParser() {
        return new MapperParser(MapperParser.Version.VERSION_1_0).getParser();
    }

    PersistentResourceXMLDescription getCredentialStoresParser() {
        return new CredentialStoreParser().getCredentialStoresParser().build();
    }

    PersistentResourceXMLDescription getDomainParser() {
        return domainParser;
    }

    PersistentResourceXMLDescription getDirContextParser() {
        return dirContextParser;
    }

    PersistentResourceXMLDescription getPolicyParser() {
        return policyParser;
    }

    PersistentResourceXMLDescription getHttpParser() {
        return new HttpParser().parser;
    }

    PersistentResourceXMLDescription getSaslParser() {
        return new SaslParser().parser;
    }

    PersistentResourceXMLDescription getTlsParser() {
        return new TlsParser().tlsParser;
    }

    PersistentResourceXMLDescription getRealmParser() {
        return new RealmParser().realmParser;
    }

    PersistentResourceXMLDescription getAuthenticationClientParser() {
        return new AuthenticationClientParser().parser;
    }

    PersistentResourceXMLDescription getAuditLoggingParser() {
        return new AuditLoggingParser().parser;
    }

    PersistentResourceXMLDescription getProviderParser() {
        return new ProviderParser().parser;
    }

    PersistentResourceXMLDescription getCredentialSecurityFactoryParser() {
        return new CredentialSecurityFactoryParser().parser;
    }


    String getNameSpace() {
        return ElytronExtension.NAMESPACE_1_0;
    }

    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return PersistentResourceXMLDescription.builder(ElytronExtension.SUBSYSTEM_PATH, getNameSpace())
                .addAttribute(ElytronDefinition.DEFAULT_AUTHENTICATION_CONTEXT)
                .addAttribute(ElytronDefinition.INITIAL_PROVIDERS)
                .addAttribute(ElytronDefinition.FINAL_PROVIDERS)
                .addAttribute(ElytronDefinition.DISALLOWED_PROVIDERS)
                .addAttribute(ElytronDefinition.SECURITY_PROPERTIES, new AttributeParsers.PropertiesParser(null, SECURITY_PROPERTY, true), new AttributeMarshallers.PropertiesAttributeMarshaller(null, SECURITY_PROPERTY, true))
                .addChild(getAuthenticationClientParser())
                .addChild(getAuditLoggingParser())
                .addChild(getProviderParser())
                .addChild(getDomainParser())
                .addChild(getRealmParser())
                .addChild(getMapperParser())
                .addChild(getTlsParser())
                .addChild(getDirContextParser())
                .addChild(getCredentialStoresParser())
                .addChild(getSaslParser())
                .addChild(getHttpParser())
                .addChild(getPolicyParser())
                .addChild(getCredentialSecurityFactoryParser())
                .build();
    }

}
