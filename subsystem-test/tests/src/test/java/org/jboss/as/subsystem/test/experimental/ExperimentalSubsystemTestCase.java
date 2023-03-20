/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.experimental;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Locale;

import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.AdditionalInitialization.ManagementAdditionalInitialization;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@RunWith(Parameterized.class)
public class ExperimentalSubsystemTestCase extends AbstractSubsystemBaseTest {
    @Parameters
    public static Iterable<ExperimentalSubsystemSchema> getParameters() {
        return EnumSet.allOf(ExperimentalSubsystemSchema.class);
    }

    private final ExperimentalSubsystemSchema schema;

    public ExperimentalSubsystemTestCase(ExperimentalSubsystemSchema schema) {
        super(ExperimentalSubsystemExtension.SUBSYSTEM_NAME, new ExperimentalSubsystemExtension());
        this.schema = schema;
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource(String.format(Locale.ROOT, "test-%d.%d-%s.xml", this.schema.getVersion().major(), this.schema.getVersion().minor(), this.schema.getFeatureStream()));
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return new ManagementAdditionalInitialization(this.schema);
    }
}
