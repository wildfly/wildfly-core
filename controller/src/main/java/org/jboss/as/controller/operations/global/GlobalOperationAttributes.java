/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.global;

import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class GlobalOperationAttributes {

    // WFCORE-76
    static final SimpleAttributeDefinition RECURSIVE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.RECURSIVE, ModelType.BOOLEAN)
    .setRequired(false)
    .build();

    // WFCORE-76
    static final SimpleAttributeDefinition RECURSIVE_DEPTH = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.RECURSIVE_DEPTH, ModelType.INT)
    .setRequired(false)
    .setValidator(new IntRangeValidator(0, true))
    .build();

    static final SimpleAttributeDefinition PROXIES = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PROXIES, ModelType.BOOLEAN)
    .setRequired(false)
    .setDefaultValue(ModelNode.FALSE)
    .build();

    static final SimpleAttributeDefinition INCLUDE_SINGLETONS = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.INCLUDE_SINGLETONS, ModelType.BOOLEAN)
    .setRequired(false)
    .setDefaultValue(ModelNode.FALSE)
    .build();

    static final SimpleAttributeDefinition INCLUDE_RUNTIME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.INCLUDE_RUNTIME, ModelType.BOOLEAN)
    .setRequired(false)
    .setDefaultValue(ModelNode.FALSE)
    .build();

    static final SimpleAttributeDefinition INCLUDE_DEFAULTS = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.INCLUDE_DEFAULTS, ModelType.BOOLEAN)
    .setRequired(false)
    .setDefaultValue(ModelNode.TRUE)
    .build();

    static final SimpleAttributeDefinition INCLUDE_ALIASES = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.INCLUDE_ALIASES, ModelType.BOOLEAN)
    .setRequired(false)
    .setDefaultValue(ModelNode.FALSE)
    .build();

    static final SimpleAttributeDefinition INCLUDE_UNDEFINED_METRIC_VALUES = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.INCLUDE_UNDEFINED_METRIC_VALUES, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME, ModelType.STRING)
    .setValidator(new StringLengthValidator(1))
    .setRequired(true)
    .build();

    static final SimpleAttributeDefinition LOCALE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.LOCALE, ModelType.STRING)
    .setRequired(false)
    .build();

    static final SimpleAttributeDefinition CHILD_TYPE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.CHILD_TYPE, ModelType.STRING)
    .setValidator(new StringLengthValidator(1))
    .setRequired(true)
    .build();

    static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.VALUE, ModelType.STRING)
    .setValidator(new StringLengthValidator(1))
    .setRequired(false)
    .build();

}
