/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PrimitiveListAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Static methods for creating domain management API descriptions for platform mbean resources.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
class CommonAttributes {

    private CommonAttributes() {
    }

    static final SimpleAttributeDefinition LOGGER_NAME = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.LOGGER_NAME, ModelType.STRING, false)
            .setMaxSize(1)
            .build();
    static final SimpleAttributeDefinition LEVEL_NAME = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.LOGGER_NAME, ModelType.STRING, false)
            .setMaxSize(1)
            .build();


    static final SimpleAttributeDefinition ID = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.ID, ModelType.LONG, false)
            .setMinSize(1)
            .build();

    static final PrimitiveListAttributeDefinition IDS = new PrimitiveListAttributeDefinition.Builder(PlatformMBeanConstants.IDS, ModelType.LONG)
            .setRequired(true)
            .build();

    static final SimpleAttributeDefinition MAX_DEPTH = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.MAX_DEPTH, ModelType.INT)
            .setRequired(false)
            .setDefaultValue(ModelNode.ZERO)
            .setMinSize(1)
            .build();


    static final SimpleAttributeDefinition LOCKED_MONITORS_FLAG = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.LOCKED_MONITORS, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    static final SimpleAttributeDefinition LOCKED_SYNCHRONIZERS_FLAG = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.LOCKED_SYNCHRONIZERS, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    static final SimpleAttributeDefinition THREAD_ID = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.THREAD_ID, ModelType.LONG, true)
            .build();

    static final SimpleAttributeDefinition THREAD_NAME = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.THREAD_NAME, ModelType.STRING, true)
            .build();

    static final SimpleAttributeDefinition THREAD_STATE = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.THREAD_STATE, ModelType.STRING, true)
            .setValidator(new EnumValidator<Thread.State>(Thread.State.class))
            .build();

    static final SimpleAttributeDefinition BLOCKED_TIME = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.BLOCKED_TIME, ModelType.LONG, false)
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .build();

    static final SimpleAttributeDefinition BLOCKED_COUNT = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.BLOCKED_COUNT, ModelType.LONG, true)
            .build();

    static final SimpleAttributeDefinition WAITED_TIME = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.WAITED_TIME, ModelType.LONG, false)
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .build();
    static final SimpleAttributeDefinition WAITED_COUNT = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.WAITED_COUNT, ModelType.LONG, true)
            .build();


    static final SimpleAttributeDefinition LOCK_NAME = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.LOCK_NAME, ModelType.STRING, true)
            .build();

    static final SimpleAttributeDefinition LOCK_OWNER_ID = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.LOCK_OWNER_ID, ModelType.LONG, true)
            .build();
    static final SimpleAttributeDefinition LOCK_OWNER_NAME = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.LOCK_OWNER_NAME, ModelType.STRING, true)
            .build();

    static final SimpleAttributeDefinition SUSPENDED = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.SUSPENDED, ModelType.BOOLEAN, true)
            .build();
    static final SimpleAttributeDefinition IN_NATIVE = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.IN_NATIVE, ModelType.BOOLEAN, true)
            .build();
    /* thread attributes */

    static final SimpleAttributeDefinition FILE_NAME = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.FILE_NAME, ModelType.STRING, true)
            .build();
    static final SimpleAttributeDefinition LINE_NUMBER = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.LINE_NUMBER, ModelType.INT, true)
            .build();
    static final SimpleAttributeDefinition CLASS_NAME = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.CLASS_NAME, ModelType.STRING, true)
            .build();
    static final SimpleAttributeDefinition METHOD_NAME = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.METHOD_NAME, ModelType.STRING, true)
            .build();
    static final SimpleAttributeDefinition NATIVE_METHOD = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.NATIVE_METHOD, ModelType.STRING, true)
            .build();


    static final SimpleAttributeDefinition IDENTITY_HASH_CODE = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.IDENTITY_HASH_CODE, ModelType.INT, true)
            .build();


    static final ObjectTypeAttributeDefinition STACK_TRACE_ELEMENT = new ObjectTypeAttributeDefinition.Builder("stack-trace-element", FILE_NAME, LINE_NUMBER, CLASS_NAME, METHOD_NAME, NATIVE_METHOD)
            .build();

    static final AttributeDefinition STACK_TRACE = new ObjectListAttributeDefinition.Builder(PlatformMBeanConstants.STACK_TRACE, STACK_TRACE_ELEMENT)
            .setRequired(false)
            .build();


    static final ObjectTypeAttributeDefinition LOCK_INFO = new ObjectTypeAttributeDefinition.Builder(PlatformMBeanConstants.LOCK_INFO, CLASS_NAME, IDENTITY_HASH_CODE)
            .build();


    //monitor info
    static final SimpleAttributeDefinition LOCKED_STACK_DEPTH = new SimpleAttributeDefinitionBuilder(PlatformMBeanConstants.LOCKED_STACK_DEPTH, ModelType.INT, true)
            .build();

    static final SimpleAttributeDefinition LOCKED_STACK_FRAME = new ObjectTypeAttributeDefinition.Builder(PlatformMBeanConstants.LOCKED_STACK_FRAME, FILE_NAME, LINE_NUMBER, CLASS_NAME, METHOD_NAME, NATIVE_METHOD)
            .build();
    static final ObjectTypeAttributeDefinition LOCKED_MONITOR_ELEMENT = new ObjectTypeAttributeDefinition.Builder("locked-monitor-element", LOCKED_STACK_DEPTH, LOCKED_STACK_FRAME)
            .build();


    static final AttributeDefinition LOCKED_MONITORS = new ObjectListAttributeDefinition.Builder(PlatformMBeanConstants.LOCKED_MONITORS, LOCKED_MONITOR_ELEMENT)
            .setRequired(false)
            .build();


    static final AttributeDefinition LOCKED_SYNCHRONIZERS = new ObjectListAttributeDefinition.Builder(PlatformMBeanConstants.LOCKED_SYNCHRONIZERS, LOCK_INFO)
            .setRequired(false)
            .build();
    static final AttributeDefinition[] THREAD_INFO_ATTRIBUTES = {THREAD_ID, THREAD_NAME, THREAD_STATE, BLOCKED_TIME, BLOCKED_COUNT,
            WAITED_TIME, WAITED_COUNT, LOCK_INFO, LOCK_NAME, LOCK_OWNER_ID, LOCK_OWNER_NAME, STACK_TRACE, SUSPENDED, IN_NATIVE,
            LOCKED_MONITORS, LOCKED_SYNCHRONIZERS
    };
}
