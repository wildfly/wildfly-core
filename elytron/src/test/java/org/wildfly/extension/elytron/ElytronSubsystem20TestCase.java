/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import java.io.IOException;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public class ElytronSubsystem20TestCase extends AbstractElytronSubsystemBaseTest {

    public ElytronSubsystem20TestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    public void testSchemaOfSubsystemTemplates() throws Exception {
        //
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("legacy-elytron-subsystem-2.0.xml");
    }

    @Override
    protected void compareXml(String configId, String original, String marshalled) throws Exception {
        //
    }
}
