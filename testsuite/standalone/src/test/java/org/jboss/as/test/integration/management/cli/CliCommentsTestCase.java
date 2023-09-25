/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildFlyRunner.class)
public class CliCommentsTestCase {

    @Rule
    public final TemporaryFolder temporaryUserHome = new TemporaryFolder();

    /**
     * In comments, " and ' are not parsed. Outside comments, they are and the
     * '>' prompt is shown.
     *
     * @throws Exception
     */
    @Test
    public void test() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString());
        cli.executeInteractive();
        cli.clearOutput();
        try {
            assertTrue(cli.pushLineAndWaitForResults("# Hello \" sdcds ", null));
            assertTrue(cli.pushLineAndWaitForResults("# Hello \' sdcds ", null));
            assertTrue(cli.pushLineAndWaitForResults("version", null));
            assertTrue(cli.getOutput().contains("JBOSS_HOME"));
            assertTrue(cli.pushLineAndWaitForResults("ls \"", ">"));
            assertTrue(cli.pushLineAndWaitForResults("-l \"", null));
        } finally {
            cli.destroyProcess();
        }
    }
}
