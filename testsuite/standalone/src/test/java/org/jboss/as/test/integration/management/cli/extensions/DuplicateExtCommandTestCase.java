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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * @author Petr Kremensky pkremens@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class DuplicateExtCommandTestCase {

    private static final String MODULE_NAME = "test.cli.extension.duplicate";

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
            ModelNode subsystemResult = controllerClient.execute(Util.createRemoveOperation(PathAddress.pathAddress(DuplicateExtCommandSubsystemResourceDescription.PATH)));
            ModelNode extensionResult = controllerClient.execute(Util.createRemoveOperation(PathAddress.pathAddress(ModelDescriptionConstants.EXTENSION, MODULE_NAME)));
            ModelTestUtils.checkOutcome(subsystemResult);
            ModelTestUtils.checkOutcome(extensionResult);
        } finally {
            // cannot remove test module on running server on Windows due to file locks
            testModule.remove();
        }
    }

    @Test
    public void testExtensionCommandCollision() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + client.getMgmtAddress() + ":" + client.getMgmtPort())
                .addCliArgument("extension-commands --errors");
        cli.executeNonInteractive();

        assertEquals(cli.getOutput().trim(), 0, cli.getProcessExitValue());
        assertTrue(cli.getOutput().trim(), cli.getOutput().trim().contains(DuplicateExtCommandHandler.NAME)
                && cli.getOutput().trim().contains(DuplicateExtCommand.NAME));
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
