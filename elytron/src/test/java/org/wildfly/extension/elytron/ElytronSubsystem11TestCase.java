/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import java.io.IOException;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public class ElytronSubsystem11TestCase extends AbstractElytronSubsystemBaseTest {

    public ElytronSubsystem11TestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("legacy-elytron-subsystem-1.1.xml");
    }

    @Override
    protected void compareXml(String configId, String original, String marshalled) throws Exception {
        //super.compareXml(configId, original, marshalled);
    }
}
