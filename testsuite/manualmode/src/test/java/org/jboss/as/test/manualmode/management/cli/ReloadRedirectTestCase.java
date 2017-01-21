/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.jboss.as.test.manualmode.management.cli;

import javax.inject.Inject;
import org.jboss.as.test.integration.management.cli.CliProcessWrapper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class ReloadRedirectTestCase {

    @Inject
    private static ServerController container;

    @BeforeClass
    public static void initServer() throws Exception {
        container.start();
    }

    @AfterClass
    public static void closeServer() throws Exception {
        container.stop();
    }

    /**
     * We should have the same test with "shutdown --restart" but testing framework
     * doesn't allow to restart the server (not launched from server script file).
     * "shutdown --restart" must be tested manually.
     * @throws Exception
     */
    @Test
    public void testReloadwithRedirect() throws Exception {
        CliProcessWrapper cliProc = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort());
        try {
            cliProc.executeInteractive();
            cliProc.clearOutput();
            boolean promptFound = cliProc.
                    pushLineAndWaitForResults("/core-service=management/"
                            + "security-realm=ManagementRealm/"
                            + "server-identity=ssl:add(keystore-path=management.keystore,"
                            + "keystore-relative-to=jboss.server.config.dir,"
                            + "keystore-password=password,alias=server,key-password=password,"
                            + "generate-self-signed-certificate-host=localhost)", null);
            assertTrue("Invalid prompt" + cliProc.getOutput(), promptFound);
            cliProc.clearOutput();
            promptFound = cliProc.pushLineAndWaitForResults("/core-service=management/"
                    + "management-interface=http-interface:"
                    + "write-attribute(name=secure-socket-binding,value=management-https)", null);
            assertTrue("Invalid prompt" + cliProc.getOutput(), promptFound);
            cliProc.clearOutput();
            promptFound = cliProc.pushLineAndWaitForResults("reload", "Accept certificate");
            assertTrue("No certificate prompt " + cliProc.getOutput(), promptFound);
        } finally {
            cliProc.destroyProcess();
        }
    }
}
