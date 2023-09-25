/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

/**
 * The subsystem parser, which uses stax to read and write to and from xml.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @since 8.0
 */
public class ElytronSubsystemParser6_0 extends ElytronSubsystemParser5_0 {

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_6_0;
    }

}
