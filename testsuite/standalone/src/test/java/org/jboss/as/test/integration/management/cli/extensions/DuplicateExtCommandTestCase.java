/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.cli.extensions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;
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
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author Petr Kremensky pkremens@redhat.com
 */
@RunWith(WildFlyRunner.class)
public class DuplicateExtCommandTestCase {

    private static final String MODULE_NAME = "test.cli.extension.duplicate";

    @Inject
    protected static ManagementClient client;

    @Inject
    protected static ModelControllerClient controllerClient;


    private static TestModule testModule;

    /**
     * Output of "extension-commands --errors" cli command
     */
    private static String cliErrors;

    @BeforeClass
    public static void setupServer() throws Exception {
        createTestModule();
        setupServerWithExtension();

        // call "extension-commands --errors"
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + client.getMgmtAddress() + ":" + client.getMgmtPort())
                .addCliArgument("extension-commands --errors");
        cli.executeNonInteractive();
        cliErrors = cli.getOutput().trim();
        assertEquals("Wrong CLI return value", 0, cli.getProcessExitValue());
    }

    @AfterClass
    public static void tearDownServer() throws Exception {
        try {
            ModelNode subsystemResult = controllerClient.execute(Util.createRemoveOperation(PathAddress.pathAddress(DuplicateExtCommandSubsystemResourceDescription.PATH)));
            ModelNode extensionResult = controllerClient.execute(Util.createRemoveOperation(PathAddress.pathAddress(ModelDescriptionConstants.EXTENSION, MODULE_NAME)));
            ModelTestUtils.checkOutcome(subsystemResult);
            ModelTestUtils.checkOutcome(extensionResult);
        } finally {
            // cannot remove test module on running server on Windows due to file locks
            testModule.remove();
        }
    }

    /**
     * Checks error message if custom Aesh CLI command has the same name as already registered command.
     */
    @Test
    public void testExtensionAeshCommandCollision() throws Exception {
        assertTrue("Required CLI error was not printed", cliErrors.contains(DuplicateExtCommand.NAME));
    }

    /**
     * Checks error message if custom legacy CLI command has the same name as already registered command.
     */
    @Test
    public void testExtensionLegacyCommandCollision() throws Exception {
        assertTrue("Required CLI error was not printed", cliErrors.contains(DuplicateExtCommandHandler.NAME));
    }

    private static void createTestModule() throws Exception {
        final File moduleXml = new File(DuplicateExtCommandTestCase.class.getResource(DuplicateExtCommandTestCase.class.getSimpleName() + "-module.xml").toURI());
        testModule = new TestModule(MODULE_NAME, moduleXml);

        final JavaArchive archive = testModule.addResource("test-cli-duplicate-commands-module.jar")
                .addClass(DuplicateExtCommandHandler.class)
                .addClass(DuplicateExtCommandHandlerProvider.class)
                .addClass(DuplicateExtCommandsExtension.class)
                .addClass(CliExtCommandsParser.class)
                .addClass(DuplicateExtCommand.class)
                .addClass(DuplicateExtCommandSubsystemResourceDescription.class);

        ArchivePath services = ArchivePaths.create("/");
        services = ArchivePaths.create(services, "services");

        final ArchivePath extService = ArchivePaths.create(services, Extension.class.getName());
        archive.addAsManifestResource(getResource(DuplicateExtCommandsExtension.class), extService);

        final ArchivePath cliCmdService = ArchivePaths.create(services, CommandHandlerProvider.class.getName());
        archive.addAsManifestResource(getResource(DuplicateExtCommandHandlerProvider.class), cliCmdService);

        final ArchivePath cliAeshCmdService = ArchivePaths.create(services, Command.class.getName());
        archive.addAsManifestResource(getResource(DuplicateExtCommand.class), cliAeshCmdService);
        testModule.create(true);
    }

    private static Asset getResource(Class clazz) {
        return new Asset() {
            @Override
            public InputStream openStream() {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    baos.write(clazz.getName().getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return new ByteArrayInputStream(baos.toByteArray());
            }
        };
    }

    private static void setupServerWithExtension() throws Exception {
        //Add the extension
        final ModelNode addExtension = Util.createAddOperation(PathAddress.pathAddress(ModelDescriptionConstants.EXTENSION, MODULE_NAME));
        ModelTestUtils.checkOutcome(controllerClient.execute(addExtension));

        final ModelNode addSubsystem = Util.createAddOperation(PathAddress.pathAddress(DuplicateExtCommandSubsystemResourceDescription.PATH));
        ModelTestUtils.checkOutcome(controllerClient.execute(addSubsystem));
    }
}
