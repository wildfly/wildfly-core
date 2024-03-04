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
public class CoreManagementSubsystem_1_0_TestCase extends AbstractSubsystemSchemaTest<CoreManagementSubsystemSchema_1_0> {

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<CoreManagementSubsystemSchema_1_0> getParameters() {
        return EnumSet.allOf(CoreManagementSubsystemSchema_1_0.class);
    }

    public CoreManagementSubsystem_1_0_TestCase(CoreManagementSubsystemSchema_1_0 schema) {
        super(CoreManagementExtension.SUBSYSTEM_NAME, new CoreManagementExtension(), schema, CoreManagementSubsystemSchema_1_0.ALL.get(schema.getStability()));
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return new AdditionalInitialization.AdminOnlyHostControllerAdditionalInitialization(getSubsystemSchema());
    }

}
