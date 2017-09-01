/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Start an embedded server in the CLI remote process.
 * embedded server updates System I/O in a way that impacts CLI output.
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class CliEmbedServerTestCase {

    @Rule
    public final TemporaryFolder temporaryUserHome = new TemporaryFolder();

    @Test
    public void testEmbedServerInRemoteCliProcess() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString());
        try {
            cli.executeInteractive();
            cli.clearOutput();
            boolean ret = cli.pushLineAndWaitForResults("embed-server", "[standalone@embedded /]");
            assertTrue("Invalid output " + cli.getOutput(), ret);
            ret = cli.pushLineAndWaitForResults("stop-embedded-server", "[disconnected /]");
            assertTrue("Invalid output " + cli.getOutput(), ret);
        } finally {
            cli.destroyProcess();
        }
    }
}
