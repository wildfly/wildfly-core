/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.security.perimeter;

import static org.junit.Assert.assertFalse;

import java.io.File;

import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.assume.AssumeTestGroupUtil;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * This class contains a check that CLI access is secured.
 *
 * @author <a href="mailto:jlanik@redhat.com">Jan Lanik</a>.
 */
@RunWith(WildflyTestRunner.class)
public class CLISecurityTestCase {

    Logger logger = Logger.getLogger(CLISecurityTestCase.class);

    private static final String JBOSS_INST = TestSuiteEnvironment.getJBossHome();

    private static final File originalTokenDir = new File(JBOSS_INST, "/standalone/tmp/auth");
    private static final File renamedTokenDir = new File(JBOSS_INST, "/standalone/tmp/auth.renamed");

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

        public synchronized void shutdown() {
            this.quit();
        }
    }

    @BeforeClass
    @SuppressWarnings("deprecation")
    public static void beforeClass() {
        AssumeTestGroupUtil.assumeElytronProfileTestsEnabled();
    }

    /**
     * Workaround to disable silent login on localhost.
     */
    @BeforeClass
    public static void renameTokenDir() {
        Assert.assertTrue(originalTokenDir.renameTo(renamedTokenDir));
    }

    /**
     * Enables silent login after the test is completed.
     */
    @AfterClass
    public static void cleanup() {
        Assert.assertTrue(renamedTokenDir.renameTo(originalTokenDir));
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
        cli.sendLine("connect " + TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort(), true);
        final String line = cli.readOutput();
        logger.tracef("cli response: ", line);
        assertFalse("CLI should not be connected: " + line, cli.isConnected());

        cli.shutdown();
    }
}
