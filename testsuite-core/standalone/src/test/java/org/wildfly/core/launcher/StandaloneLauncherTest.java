/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.wildfly.core.launcher;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StandaloneLauncherTest {

    static final String JBOSS_HOME = System.getProperty("jboss.home", System.getenv("JBOSS_HOME"));

    @Test
    public void testCommands() {
        // Set up a standalone command builder
        final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(JBOSS_HOME)
                .setAdminOnly()
                .setBindAddress("0.0.0.0")
                .setDebug(true, 5005)
                .setServerConfiguration("standalone-full.xml")
                .setManagementBindAddress("0.0.0.0");

        // Get all the commands
        List<String> commands = commandBuilder.buildArguments();

        Assert.assertTrue("--admin-only is missing", commands.contains("--admin-only"));

        int index = commands.indexOf("-b");
        Assert.assertTrue("Missing -b 0.0.0.0", index != -1 && "0.0.0.0".equals(commands.get(index + 1)));

        index = commands.indexOf("-bmanagement");
        Assert.assertTrue("Missing -b 0.0.0.0", index != -1 && "0.0.0.0".equals(commands.get(index + 1)));

        Assert.assertTrue("Missing debug argument", commands.contains(String.format(StandaloneCommandBuilder.DEBUG_FORMAT, "y", 5005)));

        index = commands.indexOf("-c");
        Assert.assertTrue("Missing server configuration file override", index != -1 && "standalone-full.xml".equals(commands.get(index + 1)));

        // Rename the binding address
        commandBuilder.setBindAddress(null);
        commandBuilder.setManagementBindAddress(null);
        commands = commandBuilder.buildArguments();
        Assert.assertFalse("Binding address should have been removed", commands.contains("-b") || commands.contains("0.0.0.0"));

    }
}