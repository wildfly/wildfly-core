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
 * @since 11.0
 */
public class ElytronSubsystemParser9_0 extends ElytronSubsystemParser8_0 {

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_9_0;
    }


    @Override
    PersistentResourceXMLDescription getAuthenticationClientParser() {
        return new AuthenticationClientParser().parser_9_0;
    }


    PersistentResourceXMLDescription getTlsParser() {
        return new TlsParser().tlsParser_9_0;
    }

}

