/*
 * JBoss, Home of Professional Open Source
 * Copyright 2019, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
