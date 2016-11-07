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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.core.launcher.Arguments.Argument;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CommandBuilderTest {

    private static final Path WILDFLY_HOME;
    private static final Path JAVA_HOME;

    static {
        WILDFLY_HOME = Paths.get(System.getProperty("wildfly.launcher.home")).toAbsolutePath().normalize();

        // Create some default directories
        try {
            Files.createDirectories(WILDFLY_HOME.resolve("modules"));
            Files.createDirectories(WILDFLY_HOME.resolve("configuration"));
            Files.createDirectories(WILDFLY_HOME.resolve("data"));
        } catch (IOException ignore) {
        }

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            javaHome = System.getProperty("java.home");
        }
        JAVA_HOME = Paths.get(javaHome).toAbsolutePath().normalize();
    }

    @Test
    public void testStandaloneBuilder() {
        // Set up a standalone command builder
        final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(WILDFLY_HOME)
                .setAdminOnly()
                .setBindAddressHint("0.0.0.0")
                .setDebug(true, 5005)
                .setServerConfiguration("standalone-full.xml")
                .addJavaOption("-Djava.security.manager")
                .addJavaOption("-Djava.net.preferIPv4Stack=true")
                .addJavaOption("-Djava.net.preferIPv4Stack=false")
                .addJavaOption("--add-exports=jdk.unsupported/sun.reflect=ALL-UNNAMED")
                .addJavaOption("--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED")
                .addJavaOption("--add-modules=java.base, java.annotation.common")
                .setBindAddressHint("management", "0.0.0.0");

        // Get all the commands
        List<String> commands = commandBuilder.buildArguments();

        Assert.assertTrue("--admin-only is missing", commands.contains("--admin-only"));

        Assert.assertTrue("Missing -b=0.0.0.0", commands.contains("-b=0.0.0.0"));

        Assert.assertTrue("Missing -b=0.0.0.0", commands.contains("-bmanagement=0.0.0.0"));

        Assert.assertTrue("Missing debug argument", commands.contains(String.format(StandaloneCommandBuilder.DEBUG_FORMAT, "y", 5005)));

        Assert.assertTrue("Missing server configuration file override", commands.contains("-c=standalone-full.xml"));

        Assert.assertTrue("Missing -secmgr option", commands.contains("-secmgr"));

        Assert.assertTrue("Missing --add-exports option", commands.contains("--add-exports=jdk.unsupported/sun.reflect=ALL-UNNAMED"));


        // A system property should only be added ones
        Assert.assertEquals("There should be only one java.net.preferIPv4Stack system property", 1, commandBuilder.getJavaOptions().stream()
                .filter(s -> s.contains("java.net.preferIPv4Stack"))
                .count());

        // The value saved should be the last value added
        Assert.assertTrue("java.net.preferIPv4Stack should be set to false", commandBuilder.getJavaOptions().contains("-Djava.net.preferIPv4Stack=false"));

        // Rename the binding address
        commandBuilder.setBindAddressHint(null);
        commands = commandBuilder.buildArguments();
        Assert.assertFalse("Binding address should have been removed", commands.contains("-b=0.0.0.0"));
    }

    @Test
    public void testDomainBuilder() {
        // Set up a standalone command builder
        final DomainCommandBuilder commandBuilder = DomainCommandBuilder.of(WILDFLY_HOME)
                .setAdminOnly()
                .setBindAddressHint("0.0.0.0")
                .setMasterAddressHint("0.0.0.0")
                .setDomainConfiguration("domain.xml")
                .setHostConfiguration("host.xml")
                .addProcessControllerJavaOption("-Djava.security.manager")
                .setBindAddressHint("management", "0.0.0.0");

        // Get all the commands
        List<String> commands = commandBuilder.buildArguments();

        Assert.assertTrue("--admin-only is missing", commands.contains("--admin-only"));

        Assert.assertTrue("Missing -b=0.0.0.0", commands.contains("-b=0.0.0.0"));

        Assert.assertTrue("Missing -b=0.0.0.0", commands.contains("--master-address=0.0.0.0"));

        Assert.assertTrue("Missing -b=0.0.0.0", commands.contains("-bmanagement=0.0.0.0"));

        Assert.assertTrue("Missing server configuration file override", commands.contains("-c=domain.xml"));

        Assert.assertTrue("Missing -secmgr option", commands.contains("-secmgr"));

        // Rename the binding address
        commandBuilder.setBindAddressHint(null);
        commands = commandBuilder.buildArguments();
        Assert.assertFalse("Binding address should have been removed", commands.contains("-b=0.0.0.0"));
    }

    @Test
    public void testArguments() {
        final Arguments arguments = new Arguments();
        arguments.add("-Dkey=value");
        arguments.add("-X");
        arguments.add("-X");
        arguments.set("single-key", "single-value");
        arguments.set("single-key", "single-value");
        arguments.addAll("-Dprop1=value1", "-Dprop2=value2", "-Dprop3=value3");

        // Validate the arguments
        Iterator<Argument> iter = arguments.getArguments("key").iterator();
        Assert.assertTrue("Missing 'key' entry", iter.hasNext());
        Assert.assertEquals("value", arguments.get("key"));
        Assert.assertEquals("-Dkey=value", iter.next().asCommandLineArgument());

        // -X should have been added twice
        Assert.assertEquals(2, arguments.getArguments("-X").size());

        // Using set should only add the value once
        Assert.assertEquals("Should not be more than one 'single-key' argument", 1, arguments.getArguments("single-key").size());

        // Convert the arguments to a list and ensure each entry has been added in the format expected
        final List<String> stringArgs = arguments.asList();
        Assert.assertEquals(7, stringArgs.size());
        Assert.assertTrue("Missing -Dkey=value", stringArgs.contains("-Dkey=value"));
        Assert.assertTrue("Missing -X", stringArgs.contains("-X"));
        Assert.assertTrue("Missing single-key=single-value", stringArgs.contains("single-key=single-value"));
        Assert.assertTrue("Missing -Dprop1=value1", stringArgs.contains("-Dprop1=value1"));
        Assert.assertTrue("Missing -Dprop2=value2", stringArgs.contains("-Dprop2=value2"));
        Assert.assertTrue("Missing -Dprop3=value3", stringArgs.contains("-Dprop3=value3"));
    }

}
