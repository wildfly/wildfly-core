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
