/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import java.io.IOException;

import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;

/**
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ElytronSubsystem70TestCase extends AbstractSubsystemBaseTest {

    public ElytronSubsystem70TestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    protected String getSubsystemXsdPath() throws Exception {
        return "schema/wildfly-elytron_7_0.xsd";
    }

    @Override
    protected String[] getSubsystemTemplatePaths() throws IOException {
        return new String[]{
                "/subsystem-templates/elytron.xml",
                "/subsystem-templates/elytron-empty.xml"
        };
    }

    @Override
    public void testSchemaOfSubsystemTemplates() throws Exception {
        //
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("legacy-elytron-subsystem-7.0.xml");
    }

    @Override
    protected void compareXml(String configId, String original, String marshalled) throws Exception {
        //
    }
}
