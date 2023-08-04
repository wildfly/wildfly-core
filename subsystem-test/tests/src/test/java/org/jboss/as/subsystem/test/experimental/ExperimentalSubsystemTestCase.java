/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.experimental;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Locale;

import org.jboss.as.subsystem.test.AbstractSubsystemSchemaTest;
import org.jboss.as.version.FeatureStream;
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

    @Override
    protected String getSubsystemXml() throws IOException {
        if (FeatureStream.PROCESS_DEFAULT.enables(this.getSubsystemSchema().getFeatureStream())) {
            return super.getSubsystemXml();
        }
        return readResource(String.format(Locale.ROOT, "foo-%s-%d.%d.xml", this.getSubsystemSchema().getFeatureStream(), this.getSubsystemSchema().getVersion().major(), this.getSubsystemSchema().getVersion().minor()));
    }

    @Override
    protected String getSubsystemXsdPath() throws Exception {
        if (FeatureStream.PROCESS_DEFAULT.enables(this.getSubsystemSchema().getFeatureStream())) {
            return super.getSubsystemXsdPath();
        }
        return String.format(Locale.ROOT, "schema/wildfly-foo_%s_%d_%d.xsd", this.getSubsystemSchema().getFeatureStream(), this.getSubsystemSchema().getVersion().major(), this.getSubsystemSchema().getVersion().minor());
    }
}
