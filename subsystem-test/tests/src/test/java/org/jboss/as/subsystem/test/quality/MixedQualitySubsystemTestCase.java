/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.quality;

import java.util.EnumSet;

import org.jboss.as.subsystem.test.AbstractSubsystemSchemaTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * A test subsystem providing features of various quality.
 */
@RunWith(Parameterized.class)
public class MixedQualitySubsystemTestCase extends AbstractSubsystemSchemaTest<FooSubsystemSchema> {
    @Parameters
    public static Iterable<FooSubsystemSchema> getParameters() {
        return EnumSet.allOf(FooSubsystemSchema.class);
    }

    public MixedQualitySubsystemTestCase(FooSubsystemSchema schema) {
        super(FooSubsystemResourceDefinition.SUBSYSTEM_NAME, new FooSubsystemExtension(), schema, FooSubsystemSchema.CURRENT.get(schema.getQuality()));
    }
}
