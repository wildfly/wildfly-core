/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
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

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.jboss.as.controller.PersistentResourceXMLDescription.decorator;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CUSTOM_CREDENTIAL_SECURITY_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.wildfly.extension.elytron.common.FileAttributeDefinitions;
import org.wildfly.security.SecurityFactory;

/**
 * Parser and Marshaller for {@link SecurityFactory<org.wildfly.security.credential.Credential>} resources.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Tomaz Cerar
 */
class CredentialSecurityFactoryParser {

    private final PersistentResourceXMLDescription kerberosSecurityParser = builder(PathElement.pathElement(KERBEROS_SECURITY_FACTORY))
            .setUseElementsForGroups(false)
            .addAttribute(KerberosSecurityFactoryDefinition.PRINCIPAL)
            .addAttribute(KerberosSecurityFactoryDefinition.PATH)
            .addAttribute(FileAttributeDefinitions.RELATIVE_TO)
            .addAttribute(KerberosSecurityFactoryDefinition.SERVER)
            .addAttribute(KerberosSecurityFactoryDefinition.OBTAIN_KERBEROS_TICKET)
            .addAttribute(KerberosSecurityFactoryDefinition.MINIMUM_REMAINING_LIFETIME)
            .addAttribute(KerberosSecurityFactoryDefinition.REQUEST_LIFETIME)
            .addAttribute(KerberosSecurityFactoryDefinition.FAIL_CACHE)
            .addAttribute(KerberosSecurityFactoryDefinition.DEBUG)
            .addAttribute(KerberosSecurityFactoryDefinition.WRAP_GSS_CREDENTIAL)
            .addAttribute(KerberosSecurityFactoryDefinition.REQUIRED)
            .addAttribute(KerberosSecurityFactoryDefinition.MECHANISM_NAMES)
            .addAttribute(KerberosSecurityFactoryDefinition.MECHANISM_OIDS)
            .addAttribute(KerberosSecurityFactoryDefinition.OPTIONS)
            .build();
    final PersistentResourceXMLDescription parser = decorator(ElytronDescriptionConstants.CREDENTIAL_SECURITY_FACTORIES)
            .addChild(MapperParser.getCustomComponentParser(CUSTOM_CREDENTIAL_SECURITY_FACTORY))
            .addChild(kerberosSecurityParser)
            .build();


    CredentialSecurityFactoryParser() {

    }


}
