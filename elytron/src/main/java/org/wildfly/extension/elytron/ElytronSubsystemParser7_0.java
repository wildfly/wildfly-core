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
 * @since 9.0
 */
public class ElytronSubsystemParser7_0 extends ElytronSubsystemParser6_0 {

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_7_0;
    }

    @Override
    PersistentResourceXMLDescription getRealmParser() {
        return new RealmParser().realmParser_7_0;
    }

}
