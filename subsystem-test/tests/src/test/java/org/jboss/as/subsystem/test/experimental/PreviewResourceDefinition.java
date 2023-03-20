/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.experimental;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredAddStepHandler;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;

/**
 * @author Paul Ferraro
 */
public class PreviewResourceDefinition extends SimpleResourceDefinition {
    static final PathElement PATH = PathElement.pathElement("preview");

    PreviewResourceDefinition() {
        super(new Parameters(PATH, NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddHandler(new ReloadRequiredAddStepHandler()).setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
    }
}
