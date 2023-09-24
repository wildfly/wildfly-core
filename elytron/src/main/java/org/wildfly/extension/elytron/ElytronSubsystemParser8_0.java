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
 * @since 10.0
 */
public class ElytronSubsystemParser8_0 extends ElytronSubsystemParser7_0 {

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
            .addAttribute(DomainDefinition.EVIDENCE_DECODER) // new
            .build();

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_8_0;
    }

    @Override
    PersistentResourceXMLDescription getRealmParser() {
        return new RealmParser().realmParser_8_0;
    }

    PersistentResourceXMLDescription getTlsParser() {
        return new TlsParser().tlsParser_8_0;
    }

    @Override
    protected PersistentResourceXMLDescription getMapperParser() {
        return new MapperParser(MapperParser.Version.VERSION_8_0).getParser();
    }


    @Override
    PersistentResourceXMLDescription getDomainParser() {
        return domainParser;
    }

    @Override
    PersistentResourceXMLDescription getAuditLoggingParser() {
        return new AuditLoggingParser().parser8_0;
    }
}
