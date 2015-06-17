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

import org.jboss.as.cli.CommandHandlerProvider;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.test.integration.management.cli.CliScriptTestBase;
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

import javax.inject.Inject;
import java.io.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Petr Kremensky pkremens@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class DuplicateExtCommandTestCase extends CliScriptTestBase {

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
            testModule.remove();
        }
    }

    @Test
    public void testExtensionCommandCollision() throws Exception {
        assertEquals(0,
                execute(client.getMgmtAddress(),
                        client.getMgmtPort(),
                        true,
                        "extension-commands --errors",
                        true));
        assertTrue(getLastCommandOutput().trim().endsWith(DuplicateExtCommandHandler.NAME));
    }

    private static void createTestModule() throws Exception {
        final File moduleXml = new File(DuplicateExtCommandTestCase.class.getResource(DuplicateExtCommandTestCase.class.getSimpleName() + "-module.xml").toURI());
        testModule = new TestModule(MODULE_NAME, moduleXml);

        final JavaArchive archive = testModule.addResource("test-cli-duplicate-commands-module.jar")
                .addClass(DuplicateExtCommandHandler.class)
                .addClass(DuplicateExtCommandHandlerProvider.class)
                .addClass(CliExtCommandsExtension.class)
                .addClass(CliExtCommandsParser.class)
                .addClass(CliExtCommandsSubsystemResourceDescription.class);

        ArchivePath services = ArchivePaths.create("/");
        services = ArchivePaths.create(services, "services");

        final ArchivePath extService = ArchivePaths.create(services, Extension.class.getName());
        archive.addAsManifestResource(CliExtCommandHandler.class.getPackage(), Extension.class.getName(), extService);

        final ArchivePath cliCmdService = ArchivePaths.create(services, CommandHandlerProvider.class.getName());
        archive.addAsManifestResource(new Asset() {
            @Override
            public InputStream openStream() {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    baos.write(DuplicateExtCommandHandlerProvider.class.getName().getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return new ByteArrayInputStream(baos.toByteArray());
            }
        }, cliCmdService);
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
