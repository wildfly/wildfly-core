/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronDescriptionConstants.POLICY;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * The subsystem parser, which uses stax to read and write to and from xml
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a> *
 * @author Tomaz Cerar
 */
class ElytronSubsystemParser1_2 extends ElytronSubsystemParser1_1 {

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_1_2;
    }

    @Override
    PersistentResourceXMLDescription getPolicyParser() {
        return PersistentResourceXMLDescription.builder(PathElement.pathElement(POLICY))
                .addAttribute(PolicyDefinitions.JaccPolicyDefinition.POLICY)
                .addAttribute(PolicyDefinitions.CustomPolicyDefinition.POLICY)
                .build();
    }
}
