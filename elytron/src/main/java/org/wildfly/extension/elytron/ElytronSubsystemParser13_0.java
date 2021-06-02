/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ENCRYPTION;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.EXPRESSION;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.EXPRESSION_RESOLVER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_PROPERTY;

import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * The subsystem parser, which uses stax to read and write to and from xml.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @since 15.0
 */
public class ElytronSubsystemParser13_0 extends ElytronSubsystemParser12_0 {

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_13_0;
    }

    @Override
    PersistentResourceXMLDescription getCredentialStoresParser() {
        return new CredentialStoreParser().getCredentialStoresParser_13().build();
    }

    // New
    PersistentResourceXMLDescription getExpressionResolverParser() {
        return PersistentResourceXMLDescription.builder(
                PathElement.pathElement(EXPRESSION, ENCRYPTION))
                .setXmlElementName(EXPRESSION_RESOLVER)
                .addAttribute(ExpressionResolverResourceDefinition.RESOLVERS)
                .addAttribute(ExpressionResolverResourceDefinition.DEFAULT_RESOLVER)
                .addAttribute(ExpressionResolverResourceDefinition.PREFIX)
                .build();
    }

    public PersistentResourceXMLDescription getParserDescription() {
        return PersistentResourceXMLDescription.builder(ElytronExtension.SUBSYSTEM_PATH, getNameSpace())
                .addAttribute(ElytronDefinition.DEFAULT_AUTHENTICATION_CONTEXT)
                .addAttribute(ElytronDefinition.INITIAL_PROVIDERS)
                .addAttribute(ElytronDefinition.FINAL_PROVIDERS)
                .addAttribute(ElytronDefinition.DISALLOWED_PROVIDERS)
                .addAttribute(ElytronDefinition.SECURITY_PROPERTIES, new AttributeParsers.PropertiesParser(null, SECURITY_PROPERTY, true), new AttributeMarshallers.PropertiesAttributeMarshaller(null, SECURITY_PROPERTY, true))
                .addAttribute(ElytronDefinition.REGISTER_JASPI_FACTORY)
                .addAttribute(ElytronDefinition.DEFAULT_SSL_CONTEXT)
                .addChild(getAuthenticationClientParser())
                .addChild(getProviderParser())
                .addChild(getAuditLoggingParser())
                .addChild(getDomainParser())
                .addChild(getRealmParser())
                .addChild(getCredentialSecurityFactoryParser())
                .addChild(getMapperParser())
                .addChild(getPermissionSetParser())
                .addChild(getHttpParser())
                .addChild(getSaslParser())
                .addChild(getTlsParser())
                .addChild(getCredentialStoresParser())
                .addChild(getExpressionResolverParser()) // New
                .addChild(getDirContextParser())
                .addChild(getPolicyParser())
                .addChild(jaspiConfigurationParser)
                .build();
    }


}

