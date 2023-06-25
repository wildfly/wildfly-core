/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.extension;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.dmr.ModelType;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class VersionedExtensionCommon implements Extension {

    public static final String EXTENSION_NAME = "org.jboss.as.test.transformers";

    public static final String SUBSYSTEM_NAME = "test-subsystem";
    public static final String IGNORED_SUBSYSTEM_NAME = "ignored-test-subsystem";


    static final AttributeDefinition TEST_ATTRIBUTE = SimpleAttributeDefinitionBuilder.create("test-attribute", ModelType.STRING, true).build();

    protected static ResourceDefinition createResourceDefinition(final PathElement element) {
        return new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(element, NonResolvingResourceDescriptionResolver.INSTANCE)
        .setAddHandler(ModelOnlyAddStepHandler.INSTANCE)
        .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
    }

    static OperationDefinition getOperationDefinition(String name) {
        return new SimpleOperationDefinitionBuilder(name, NonResolvingResourceDescriptionResolver.INSTANCE)
                .setReadOnly()
                .build();
    }

}
