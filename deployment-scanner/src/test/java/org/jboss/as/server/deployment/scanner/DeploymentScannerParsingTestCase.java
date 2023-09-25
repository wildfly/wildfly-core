/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.scanner;


import java.io.IOException;

import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;

/**
 * @author Tomaz Cerar
 * @created 25.1.12 19:36
 */

public class DeploymentScannerParsingTestCase extends AbstractSubsystemBaseTest {
    private static final String SUBSYSTEM_XML =
            "<subsystem xmlns=\"urn:jboss:domain:deployment-scanner:2.0\">\n" +
            "    <deployment-scanner name=\"myScanner\" path=\"deployments_${custom.system.property:test}\" " +
                   "relative-to=\"jboss.server.base.dir\" scan-enabled=\"false\" scan-interval=\"5000\" " +
                   "auto-deploy-xml=\"true\" deployment-timeout=\"60\" " +
                    "runtime-failure-causes-rollback=\"${runtime-failure-causes-rollback:false}\"/>\n" +
            "    <deployment-scanner path=\"deployments\"  relative-to=\"jboss.server.base.dir\" " +
                   "scan-enabled=\"false\" scan-interval=\"5000\" " +
                   "auto-deploy-xml=\"true\" deployment-timeout=\"30\"/>\n" +
            "</subsystem>";


    public DeploymentScannerParsingTestCase() {
        super(DeploymentScannerExtension.SUBSYSTEM_NAME, new DeploymentScannerExtension());
        System.setProperty("custom.system.property","prop");
    }

    /**
     * Get the subsystem xml as string.
     *
     * @return the subsystem xml
     * @throws java.io.IOException
     */
    @Override
    protected String getSubsystemXml() throws IOException {
        return SUBSYSTEM_XML;
    }
}

