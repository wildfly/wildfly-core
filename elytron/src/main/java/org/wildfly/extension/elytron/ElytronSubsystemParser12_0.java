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
 * @since 14.0
 */
public class ElytronSubsystemParser12_0 extends ElytronSubsystemParser11_0 {

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_12_0;
    }

    @Override
    protected PersistentResourceXMLDescription getMapperParser() {
        return new MapperParser(MapperParser.Version.VERSION_12_0).getParser();
    }


    PersistentResourceXMLDescription getTlsParser() {
        return new TlsParser().tlsParser_12_0;
    }
}

