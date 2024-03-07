/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;

import static org.jboss.as.controller.PersistentResourceXMLDescription.decorator;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_DOMAIN;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_DOMAINS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.VIRTUAL_SECURITY_DOMAIN;

class SecurityDomainParser {


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

    final PersistentResourceXMLDescription parser_17_0 = decorator(ElytronDescriptionConstants.SECURITY_DOMAINS)
            .addChild(securityDomainParser)
            .addChild(virtualSecurityDomainParser)
            .build();

    final PersistentResourceXMLDescription parser_10_0 = PersistentResourceXMLDescription.builder(PathElement.pathElement(SECURITY_DOMAIN))
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
            .addAttribute(DomainDefinition.EVIDENCE_DECODER)
            .addAttribute(DomainDefinition.ROLE_DECODER) // new
            .build();

    final PersistentResourceXMLDescription parser_8_0 = PersistentResourceXMLDescription.builder(PathElement.pathElement(SECURITY_DOMAIN))
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
            .addAttribute(DomainDefinition.EVIDENCE_DECODER) // new
            .build();

    final PersistentResourceXMLDescription parser_1_0 = PersistentResourceXMLDescription.builder(PathElement.pathElement(SECURITY_DOMAIN))
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
}
