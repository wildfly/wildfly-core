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
 * @since 16.0
 */
public class ElytronSubsystemParser14_0 extends ElytronSubsystemParser13_0 {

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_14_0;
    }

    @Override
    PersistentResourceXMLDescription getRealmParser() {
        return new RealmParser().realmParser_14_0;
    }

    PersistentResourceXMLDescription getTlsParser() {
        return new TlsParser().tlsParser_14_0;
    }

}

