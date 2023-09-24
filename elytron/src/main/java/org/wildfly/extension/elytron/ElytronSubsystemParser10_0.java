/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_DOMAIN;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_DOMAINS;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * The subsystem parser, which uses stax to read and write to and from xml.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 * @since 12.0
 */
public class ElytronSubsystemParser10_0 extends ElytronSubsystemParser9_0 {

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_10_0;
    }

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
            .addAttribute(DomainDefinition.EVIDENCE_DECODER)
            .addAttribute(DomainDefinition.ROLE_DECODER) // new
            .build();

    @Override
    PersistentResourceXMLDescription getDomainParser() {
        return domainParser;
    }

    @Override
    protected PersistentResourceXMLDescription getMapperParser() {
        return new MapperParser(MapperParser.Version.VERSION_10_0).getParser();
    }

}

