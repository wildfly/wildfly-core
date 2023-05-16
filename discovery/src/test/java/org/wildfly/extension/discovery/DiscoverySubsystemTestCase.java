/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import java.util.EnumSet;

import org.jboss.as.subsystem.test.AbstractSubsystemSchemaTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:paul.ferraro@redhat.com">Paul Ferraro</a>
 */
@RunWith(Parameterized.class)
public class DiscoverySubsystemTestCase extends AbstractSubsystemSchemaTest<DiscoverySubsystemSchema> {

    @Parameters
    public static Iterable<DiscoverySubsystemSchema> parameters() {
        return EnumSet.allOf(DiscoverySubsystemSchema.class);
    }

    public DiscoverySubsystemTestCase(DiscoverySubsystemSchema schema) {
        super(DiscoverySubsystemRegistrar.NAME, new DiscoveryExtension(), schema, DiscoverySubsystemSchema.CURRENT);
    }
}
