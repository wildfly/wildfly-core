/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.experimental;

import java.util.EnumSet;

import org.jboss.as.subsystem.test.AbstractSubsystemSchemaTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@RunWith(Parameterized.class)
public class ExperimentalSubsystemTestCase extends AbstractSubsystemSchemaTest<FooSubsystemSchema> {
    @Parameters
    public static Iterable<FooSubsystemSchema> getParameters() {
        return EnumSet.allOf(FooSubsystemSchema.class);
    }

    public ExperimentalSubsystemTestCase(FooSubsystemSchema schema) {
        super(FooSubsystemResourceDefinition.SUBSYSTEM_NAME, new FooSubsystemExtension(), schema, FooSubsystemSchema.CURRENT.get(schema.getFeatureStream()));
    }
}
