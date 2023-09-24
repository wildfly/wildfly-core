/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.management.cli;

import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author tmiyar
 */
public class CLIDiscoveryOptionsTestCase extends AbstractCliTestBase {

    @BeforeClass
    public static void beforeClass() throws Exception {
        CLITestSuite.createSupport(CLIDiscoveryOptionsTestCase.class.getSimpleName());
        AbstractCliTestBase.initCLI(DomainTestSupport.primaryAddress);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        AbstractCliTestBase.closeCLI();
        CLITestSuite.stopSupport();
    }

    /**
     * Test that discovery options change gets persisted
     */
    @Test
    public void discoveryOptionsTest() {
        String cmd = "/host=secondary/core-service=discovery-options/static-discovery=test:add(host=host,port=3333)";
        cli.sendLine(cmd);
        String cliOutput = cli.readOutput();

        assertTrue("Add discovery options CLI command failed " + cliOutput, cliOutput.contains("success"));

        cmd = "/host=secondary/core-service=discovery-options/static-discovery=test:write-attribute(name=port,value=2222)";
        cli.sendLine(cmd);
        cliOutput = cli.readOutput();

        assertTrue("Modify discovery options CLI command failed " + cliOutput, cliOutput.contains("success"));

        cmd = "/host=secondary/core-service=discovery-options/static-discovery=test:read-resource";
        cli.sendLine(cmd);

        cliOutput = cli.readOutput();

        assertTrue("Value not updated to 2222 " + cliOutput, cliOutput.contains("2222"));

        cmd = "/host=secondary/core-service=discovery-options/static-discovery=test:remove";
        cli.sendLine(cmd);

        cliOutput = cli.readOutput();

        assertTrue("Remove discovery options CLI command failed " + cliOutput, cliOutput.contains("success"));
    }

    /**
     * Test that not found is returned if we try to modify a none existing option
     */
    @Test
    public void discoveryOptionsNoOptionTest() {

        String cmd = "/host=secondary/core-service=discovery-options/static-discovery=test:write-attribute(name=port,value=2222)";
        String message = "";

        try {
            cli.sendLine(cmd);
        } catch (Throwable t) {
            message = t.getMessage();
        }

       assertTrue("Did not throw expected exception " + message, message.contains("not found"));
    }


}
