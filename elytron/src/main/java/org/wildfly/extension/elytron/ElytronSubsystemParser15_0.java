/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * The subsystem parser, which uses stax to read and write to and from xml.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 * @since 17.0
 */
public class ElytronSubsystemParser15_0 extends ElytronSubsystemParser14_0 {

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_15_0;
    }

    @Override
    PersistentResourceXMLDescription getRealmParser() {
        return new RealmParser().realmParser_15_0;
    }
}

