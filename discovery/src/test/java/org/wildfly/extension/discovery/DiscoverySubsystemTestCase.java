/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.discovery;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Locale;

import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:paul.ferraro@redhat.com">Paul Ferraro</a>
 */
@RunWith(Parameterized.class)
public class DiscoverySubsystemTestCase extends AbstractSubsystemBaseTest {

    @Parameters
    public static Iterable<DiscoverySchema> parameters() {
        return EnumSet.allOf(DiscoverySchema.class);
    }

    private final DiscoverySchema schema;

    public DiscoverySubsystemTestCase(DiscoverySchema schema) {
        super(DiscoveryExtension.SUBSYSTEM_NAME, new DiscoveryExtension());
        this.schema = schema;
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource(String.format(Locale.ROOT, "discovery-%d.%d.xml", this.schema.major(), this.schema.minor()));
    }

    @Override
    protected String getSubsystemXsdPath() throws Exception {
        return String.format(Locale.ROOT, "schema/wildfly-discovery_%d_%d.xsd", this.schema.major(), this.schema.minor());
    }

    @Override
    protected String[] getSubsystemTemplatePaths() throws IOException {
        return new String[] { "/subsystem-templates/discovery.xml" };
    }

    @Test
    @Override
    public void testSchemaOfSubsystemTemplates() throws Exception {
        if (this.schema == DiscoverySchema.CURRENT) {
            super.testSchemaOfSubsystemTemplates();
        }
    }
}
