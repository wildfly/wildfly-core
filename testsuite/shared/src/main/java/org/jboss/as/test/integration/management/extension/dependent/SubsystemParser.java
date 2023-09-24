/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.extension.dependent;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
class SubsystemParser extends PersistentResourceXMLParser {

    static final SubsystemParser INSTANCE = new SubsystemParser();

    private final PersistentResourceXMLDescription xmlDescription;

    private SubsystemParser() {
        xmlDescription = builder(RootResourceDefinition.INSTANCE.getPathElement(), DependentExtension.NAMESPACE)
                .addAttributes(RootResourceDefinition.ATTRIBUTE)
                .build();
    }

    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return xmlDescription;
    }
}

