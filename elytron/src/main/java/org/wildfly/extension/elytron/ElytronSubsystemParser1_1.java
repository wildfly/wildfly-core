/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * The subsystem parser, which uses stax to read and write to and from xml
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a> *
 * @author Tomaz Cerar
 */
class ElytronSubsystemParser1_1 extends ElytronSubsystemParser1_0 {

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_1_1;
    }

    protected PersistentResourceXMLDescription getMapperParser() {
        return new MapperParser(MapperParser.Version.VERSION_1_1).getParser();
    }


}
