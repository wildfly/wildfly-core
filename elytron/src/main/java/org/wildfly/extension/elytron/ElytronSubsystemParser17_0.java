/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

import static org.jboss.as.controller.PersistentResourceXMLDescription.decorator;
import static org.wildfly.extension.elytron.ElytronCommonConstants.SECURITY_DOMAIN;
import static org.wildfly.extension.elytron.ElytronCommonConstants.VIRTUAL_SECURITY_DOMAIN;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * The subsystem parser, which uses stax to read and write to and from xml.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class ElytronSubsystemParser17_0 extends ElytronSubsystemParser16_0 {

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_17_0;
    }

    final PersistentResourceXMLDescription securityDomainParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(SECURITY_DOMAIN))
            .addAttribute(DomainDefinition.DEFAULT_REALM)
            .addAttribute(DomainDefinition.PERMISSION_MAPPER)
            .addAttribute(DomainDefinition.PRE_REALM_PRINCIPAL_TRANSFORMER)
            .addAttribute(DomainDefinition.POST_REALM_PRINCIPAL_TRANSFORMER)
            .addAttribute(DomainDefinition.PRINCIPAL_DECODER)
            .addAttribute(DomainDefinition.REALM_MAPPER)
            .addAttribute(DomainDefinition.ROLE_MAPPER)
            .addAttribute(DomainDefinition.TRUSTED_SECURITY_DOMAINS)
            .addAttribute(DomainDefinition.TRUSTED_VIRTUAL_SECURITY_DOMAINS) // new
            .addAttribute(DomainDefinition.OUTFLOW_ANONYMOUS)
            .addAttribute(DomainDefinition.OUTFLOW_SECURITY_DOMAINS)
            .addAttribute(DomainDefinition.SECURITY_EVENT_LISTENER)
            .addAttribute(DomainDefinition.REALMS)
            .addAttribute(DomainDefinition.EVIDENCE_DECODER)
            .addAttribute(DomainDefinition.ROLE_DECODER)
            .build();

    final PersistentResourceXMLDescription virtualSecurityDomainParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(VIRTUAL_SECURITY_DOMAIN))
            .addAttribute(DomainDefinition.OUTFLOW_ANONYMOUS)
            .addAttribute(VirtualDomainDefinition.OUTFLOW_SECURITY_DOMAINS)
            .addAttribute(VirtualDomainDefinition.AUTH_METHOD)
            .build();

    final PersistentResourceXMLDescription domainParser = decorator(ElytronCommonConstants.SECURITY_DOMAINS)
            .addChild(securityDomainParser)
            .addChild(virtualSecurityDomainParser)
            .build();

    @Override
    PersistentResourceXMLDescription getDomainParser() {
        return domainParser;
    }

}

