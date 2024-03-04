/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.unstable.api.annotation.classes.subsystem;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class UnstableApiAnnotationTestSubsystemParser_1_0 extends PersistentResourceXMLParser {

    public static final String NAMESPACE = "urn:wildfly:unstable-api-annotation-test-subsystem:1.0";

    private static final PersistentResourceXMLDescription xmlDescription = builder(UnstableApiAnnotationTestExtension.SUBSYSTEM_PATH, NAMESPACE)
            .build();

    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return xmlDescription;
    }

}
