/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import java.io.IOException;

/**
 * Tests of use of the wildfly-elytron_1_2.xsd.
 *
 * @author Brian Stansberry
 */
public class ElytronSubsystem1_2TestCase extends AbstractElytronSubsystemBaseTest {

    public ElytronSubsystem1_2TestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("legacy-elytron-subsystem-1.2.xml");
    }

    @Override
    protected void compareXml(String configId, String original, String marshalled) throws Exception {
        //super.compareXml(configId, original, marshalled);
    }
}
