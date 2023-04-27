/*
 * JBoss, Home of Professional Open Source.
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

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronCommonConstants.PERMISSION_SETS;
import static org.wildfly.extension.elytron.ElytronCommonConstants.SECURITY_PROPERTY;
import static org.wildfly.extension.elytron.PermissionMapperDefinitions.PERMISSIONS;

import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * The subsystem parser, which uses stax to read and write to and from xml.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 * @since 5.0
 */
class ElytronSubsystemParser3_0 extends ElytronSubsystemParser2_0 {

    final PersistentResourceXMLDescription permissionSetParser = PersistentResourceXMLDescription.builder(PermissionSetDefinition.getPermissionSet().getPathElement())
            .setXmlWrapperElement(PERMISSION_SETS)
            .addAttribute(PERMISSIONS)
            .build();

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_3_0;
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
                .addChild(getProviderParser())
                .addChild(getAuditLoggingParser())
                .addChild(getDomainParser())
                .addChild(getRealmParser())
                .addChild(getCredentialSecurityFactoryParser())
                .addChild(getMapperParser())
                .addChild(getPermissionSetParser()) // new
                .addChild(getHttpParser())
                .addChild(getSaslParser())
                .addChild(getTlsParser())
                .addChild(getCredentialStoresParser())
                .addChild(getDirContextParser())
                .addChild(getPolicyParser())
                .build();
    }

    protected PersistentResourceXMLDescription getMapperParser() {
        return new MapperParser(MapperParser.Version.VERSION_3_0).getParser();
    }

    PersistentResourceXMLDescription getPermissionSetParser() {
        return permissionSetParser;
    }
}
