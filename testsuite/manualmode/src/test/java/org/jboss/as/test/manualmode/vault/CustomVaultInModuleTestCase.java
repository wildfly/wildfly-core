/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.manualmode.vault;

import java.io.File;

import javax.inject.Inject;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.server.controller.resources.VaultResourceDefinition;
import org.jboss.as.test.manualmode.vault.module.CustomSecurityVault;
import org.jboss.as.test.manualmode.vault.module.TestVaultExtension;
import org.jboss.as.test.manualmode.vault.module.TestVaultParser;
import org.jboss.as.test.manualmode.vault.module.TestVaultRemoveHandler;
import org.jboss.as.test.manualmode.vault.module.TestVaultResolveExpressionHandler;
import org.jboss.as.test.manualmode.vault.module.TestVaultSubsystemResourceDescription;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ValueExpression;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.xnio.IoUtils;


/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class CustomVaultInModuleTestCase {
    private static final String MODULE_NAME = "test.custom.vault.in.module";

    @Inject
    private static ServerController containerController;

    private static TestModule testModule;

    @Test
    public void testCustomVault() throws Exception {
        ModelControllerClient client = containerController.getClient().getControllerClient();

        ModelNode op = createResolveExpressionOp("${VAULT::Testing::Stuff::thing}");
        ModelNode result = client.execute(op);
        Assert.assertEquals("123_Testing_Stuff_thing", ModelTestUtils.checkResultAndGetContents(result).asString());

        op = createResolveExpressionOp("${VAULT::Another::Something::whatever}");
        result = client.execute(op);
        Assert.assertEquals("Hello_Another_Something_whatever", ModelTestUtils.checkResultAndGetContents(result).asString());

        op = createResolveExpressionOp("${VAULT::Nothing::is::here}");
        ModelTestUtils.checkFailed(client.execute(op));
    }

    @BeforeClass
    public static void setupServer() throws Exception {
        createTestModule();
        setupServerWithVault();
    }

    @AfterClass
    public static void tearDownServer() throws Exception {
        ModelControllerClient client = null;
        try {
            client = containerController.getClient().getControllerClient();
            ModelNode vaultResult = client.execute(Util.createRemoveOperation(PathAddress.pathAddress(VaultResourceDefinition.PATH)));
            ModelNode subsystemResult = client.execute(Util.createRemoveOperation(PathAddress.pathAddress(TestVaultSubsystemResourceDescription.PATH)));
            ModelNode extensionResult = client.execute(Util.createRemoveOperation(PathAddress.pathAddress(ModelDescriptionConstants.EXTENSION, MODULE_NAME)));
            ModelTestUtils.checkOutcome(vaultResult);
            ModelTestUtils.checkOutcome(subsystemResult);
            ModelTestUtils.checkOutcome(extensionResult);

        } finally {
            containerController.stop();
            testModule.remove();
            IoUtils.safeClose(client);
        }
        containerController.stop();
    }

    private static void createTestModule() throws Exception {
        File moduleXml = new File(CustomSecurityVault.class.getResource(CustomVaultInModuleTestCase.class.getSimpleName() + "-module.xml").toURI());
        testModule = new TestModule(MODULE_NAME, moduleXml);

        JavaArchive archive = testModule.addResource("test-custom-vault-in-module.jar")
                .addClass(CustomSecurityVault.class)
                .addClass(TestVaultExtension.class)
                .addClass(TestVaultParser.class)
                .addClass(TestVaultRemoveHandler.class)
                .addClass(TestVaultResolveExpressionHandler.class)
                .addClass(TestVaultSubsystemResourceDescription.class);

        ArchivePath path = ArchivePaths.create("/");
        path = ArchivePaths.create(path, "services");
        path = ArchivePaths.create(path, Extension.class.getName());
        archive.addAsManifestResource(CustomSecurityVault.class.getPackage(), Extension.class.getName(), path);
        testModule.create(true);
    }

    private static void setupServerWithVault() throws Exception {
        containerController.start();
        ManagementClient managementClient = containerController.getClient();
        ModelControllerClient client = managementClient.getControllerClient();

        //Add the vault
        final ModelNode addVault = Util.createAddOperation(PathAddress.pathAddress(VaultResourceDefinition.PATH));
        addVault.get(ModelDescriptionConstants.MODULE).set(MODULE_NAME);
        addVault.get(ModelDescriptionConstants.CODE).set(CustomSecurityVault.class.getName());
        final ModelNode options = new ModelNode();
        options.get("Testing").set("123");
        options.get("Another").set("Hello");
        addVault.get(ModelDescriptionConstants.VAULT_OPTIONS).set(options);
        ModelTestUtils.checkOutcome(client.execute(addVault));

        //Add the extension
        final ModelNode addExtension = Util.createAddOperation(PathAddress.pathAddress(ModelDescriptionConstants.EXTENSION, MODULE_NAME));
        ModelTestUtils.checkOutcome(client.execute(addExtension));

        final ModelNode addSubsystem = Util.createAddOperation(PathAddress.pathAddress(TestVaultSubsystemResourceDescription.PATH));
        ModelTestUtils.checkOutcome(client.execute(addSubsystem));
    }

    private ModelNode createResolveExpressionOp(String expression) {
        ModelNode op = Util.createOperation(TestVaultResolveExpressionHandler.RESOLVE.getName(), PathAddress.pathAddress(TestVaultSubsystemResourceDescription.PATH));
        op.get(TestVaultResolveExpressionHandler.PARAM_EXPRESSION.getName()).set(new ValueExpression(expression));
        return op;
    }
}
