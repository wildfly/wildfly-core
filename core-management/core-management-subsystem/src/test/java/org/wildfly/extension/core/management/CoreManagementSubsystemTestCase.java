/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management;

import org.jboss.as.subsystem.test.AbstractSubsystemSchemaTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.EnumSet;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat Inc.
 */
@RunWith(Parameterized.class)
public class CoreManagementSubsystemTestCase extends AbstractSubsystemSchemaTest<CoreManagementSubsystemSchema> {

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<CoreManagementSubsystemSchema> getParameters() {
        return EnumSet.allOf(CoreManagementSubsystemSchema.class);
    }

    public CoreManagementSubsystemTestCase(CoreManagementSubsystemSchema schema) {
        super(CoreManagementExtension.SUBSYSTEM_NAME, new CoreManagementExtension(), schema, CoreManagementSubsystemSchema.CURRENT.get(schema.getStability()));
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return new AdditionalInitialization.AdminOnlyHostControllerAdditionalInitialization(getSubsystemSchema());
    }

}
