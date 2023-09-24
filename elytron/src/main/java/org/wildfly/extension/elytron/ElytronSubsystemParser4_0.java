/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * The subsystem parser, which uses stax to read and write to and from xml.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @since 6.0
 */
public class ElytronSubsystemParser4_0 extends ElytronSubsystemParser3_0 {

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_4_0;
    }

    @Override
    PersistentResourceXMLDescription getAuditLoggingParser() {
        return new AuditLoggingParser().parser4_0;
    }

    @Override
    protected PersistentResourceXMLDescription getMapperParser() {
        return new MapperParser(MapperParser.Version.VERSION_4_0).getParser();
    }

    @Override
    PersistentResourceXMLDescription getTlsParser() {
        return new TlsParser().tlsParser_4_0;
    }

}
