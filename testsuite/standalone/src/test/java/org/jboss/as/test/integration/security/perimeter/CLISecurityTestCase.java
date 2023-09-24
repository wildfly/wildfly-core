/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.security.perimeter;

import static org.junit.Assert.assertFalse;

import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.logging.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * This class contains a check that CLI access is secured.
 *
 * @author <a href="mailto:jlanik@redhat.com">Jan Lanik</a>.
 */
@RunWith(WildFlyRunner.class)
@ServerSetup(DisableLocalAuthServerSetupTask.class)
public class CLISecurityTestCase {

    private static final Logger logger = Logger.getLogger(CLISecurityTestCase.class);

    /**
     * Auxiliary class which extends CLIWrapper for specific purposes of this test case.
     */
    public static class UnauthentizedCLI extends CLIWrapper {

        public UnauthentizedCLI() throws Exception {
            super(false);
        }

        @Override
        protected String getUsername() {
            return null;
        }
    }

    /**
     * This test checks that CLI access is secured.
     *
     * @throws Exception
     */
    @Test
    public void testConnect() throws Exception {

        UnauthentizedCLI cli = new UnauthentizedCLI();

        assertFalse(cli.isConnected());
        assertFalse(cli.sendLine("connect " + TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort(), true));
        final String line = cli.readOutput();
        logger.tracef("cli response: ", line);
        assertFalse("CLI should not be connected: " + line, cli.isConnected());

        cli.quit();
    }
}
