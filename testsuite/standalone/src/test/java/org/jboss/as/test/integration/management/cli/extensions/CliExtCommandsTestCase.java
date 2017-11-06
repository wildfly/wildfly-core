/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.management.cli.extensions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;
import org.aesh.command.Command;

import org.jboss.as.cli.CommandHandlerProvider;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.test.integration.management.cli.CliProcessWrapper;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Testing commands loaded from the available management model extensions.
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildflyTestRunner.class)
public class CliExtCommandsTestCase {

    private static final String MODULE_NAME = "test.cli.extension.commands";

    @Inject
    protected static ManagementClient client;

    @Inject
    protected static ModelControllerClient controllerClient;

    private static TestModule testModule;

    @BeforeClass
    public static void setupServer() throws Exception {
        createTestModule();
        setupServerWithExtension();
    }

    @AfterClass
    public static void tearDownServer() throws Exception {
        try {
            ModelNode subsystemResult = controllerClient.execute(Util.createRemoveOperation(PathAddress.pathAddress(CliExtCommandsSubsystemResourceDescription.PATH)));
            ModelNode extensionResult = controllerClient.execute(Util.createRemoveOperation(PathAddress.pathAddress(ModelDescriptionConstants.EXTENSION, MODULE_NAME)));
            ModelTestUtils.checkOutcome(subsystemResult);
            ModelTestUtils.checkOutcome(extensionResult);
        } finally {
            // cannot remove test module on running server on Windows due to file locks
            testModule.remove();
        }
    }

    @Test
    public void testExtensionCommand() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--controller=" + client.getMgmtAddress() + ":" + client.getMgmtPort())
                .addCliArgument(CliExtCommandHandler.NAME);
        cli.executeNonInteractive();
        assertNotEquals(0, cli.getProcessExitValue());

        cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + client.getMgmtAddress() + ":" + client.getMgmtPort())
                .addCliArgument(CliExtCommandHandler.NAME);
        cli.executeNonInteractive();
        assertEquals(0, cli.getProcessExitValue());

        // the output may contain other logs from the cli initialization
        assertTrue("Output: '" + cli.getOutput() + "'", cli.getOutput().trim().endsWith(CliExtCommandHandler.OUTPUT));
    }

    @Test
    public void testExtensionCommand2() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--controller=" + client.getMgmtAddress() + ":" + client.getMgmtPort())
                .addCliArgument(CliExtCommand.NAME);
        cli.executeNonInteractive();
        assertNotEquals(0, cli.getProcessExitValue());

        cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + client.getMgmtAddress() + ":" + client.getMgmtPort())
                .addCliArgument(CliExtCommand.NAME);
        cli.executeNonInteractive();
        assertEquals(0, cli.getProcessExitValue());

        // the output may contain other logs from the cli initialization
        assertTrue("Output: '" + cli.getOutput() + "'", cli.getOutput().trim().endsWith(CliExtCommand.OUTPUT));
    }

    @Test
    public void testExtensionCommandHelp() throws IOException {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + client.getMgmtAddress() + ":" + client.getMgmtPort())
                .addCliArgument(String.format("%s %s", CliExtCommandHandler.NAME, "--help"));
        cli.executeNonInteractive();

        // the output may contain other logs from the cli initialization
        assertTrue("Output: '" + cli.getOutput() + "'",cli.getOutput().trim().endsWith(CliExtCommandHandler.NAME + "--help"));
    }

    @Test
    public void testExtensionCommandHelp2() throws IOException {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + client.getMgmtAddress() + ":" + client.getMgmtPort())
                .addCliArgument(String.format("%s %s", "help", CliExtCommand.NAME));
        cli.executeNonInteractive();

        // the output may contain other logs from the cli initialization
        assertTrue("Output: '" + cli.getOutput() + "'", cli.getOutput().trim().contains("THIS IS A USELESS DESCRIPTION"));
        assertTrue("Output: '" + cli.getOutput() + "'", cli.getOutput().trim().contains("THIS IS A USELESS OPTION DESCRIPTION"));
    }

    private static void createTestModule() throws Exception {
        final File moduleXml = new File(CliExtCommandsTestCase.class.getResource(CliExtCommandsTestCase.class.getSimpleName() + "-module.xml").toURI());
        testModule = new TestModule(MODULE_NAME, moduleXml);
        final JavaArchive archive = testModule.addResource("test-cli-ext-commands-module.jar")
                .addClass(CliExtCommandHandler.class)
                .addClass(CliExtCommand.class)
                .addClass(CliExtCommandHandlerProvider.class)
                .addClass(CliExtCommandsExtension.class)
                .addClass(CliExtCommandsParser.class)
                .addClass(CliExtCommandsSubsystemResourceDescription.class);

        ArchivePath services = ArchivePaths.create("/");
        services = ArchivePaths.create(services, "services");

        ArchivePath help = ArchivePaths.create("/");
        help = ArchivePaths.create(help, "help");

        final ArchivePath extService = ArchivePaths.create(services, Extension.class.getName());
        archive.addAsManifestResource(CliExtCommandHandler.class.getPackage(), Extension.class.getName(), extService);

        final ArchivePath cliCmdService = ArchivePaths.create(services, CommandHandlerProvider.class.getName());
        archive.addAsManifestResource(CliExtCommandHandler.class.getPackage(), CommandHandlerProvider.class.getName(), cliCmdService);

        final ArchivePath cliAeshCmdService = ArchivePaths.create(services, Command.class.getName());
        archive.addAsManifestResource(CliExtCommand.class.getPackage(), Command.class.getName(), cliAeshCmdService);

        final ArchivePath helpService = ArchivePaths.create(help, CliExtCommandHandler.NAME + ".txt");
        archive.addAsResource(CliExtCommandHandler.class.getPackage(), CliExtCommandHandler.NAME + ".txt", helpService);

        final ArchivePath help2Service = ArchivePaths.create("/"
                + CliExtCommand.class.getPackage().getName().replaceAll("\\.", "/"), "command_resources.properties");
        archive.addAsResource(CliExtCommand.class.getPackage(), "command_resources.properties", help2Service);

        testModule.create(true);
    }

    private static void setupServerWithExtension() throws Exception {
        //Add the extension
        final ModelNode addExtension = Util.createAddOperation(PathAddress.pathAddress(ModelDescriptionConstants.EXTENSION, MODULE_NAME));
        ModelTestUtils.checkOutcome(controllerClient.execute(addExtension));

        final ModelNode addSubsystem = Util.createAddOperation(PathAddress.pathAddress(CliExtCommandsSubsystemResourceDescription.PATH));
        ModelTestUtils.checkOutcome(controllerClient.execute(addSubsystem));
    }
}
