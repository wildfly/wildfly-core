/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.stability;

import java.util.EnumSet;

import org.jboss.as.subsystem.test.AbstractSubsystemSchemaTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * A test subsystem providing features of various quality.
 */
@RunWith(Parameterized.class)
public class MixedStabilitySubsystemTestCase extends AbstractSubsystemSchemaTest<FooSubsystemSchema> {
    @Parameters(name = "{0}")
    public static Iterable<FooSubsystemSchema> getParameters() {
        return EnumSet.allOf(FooSubsystemSchema.class);
    }

    public MixedStabilitySubsystemTestCase(FooSubsystemSchema schema) {
        super(FooSubsystemResourceDefinition.REGISTRATION.getName(), new FooSubsystemExtension(), schema, FooSubsystemSchema.CURRENT.get(schema.getStability()));
    }
}
