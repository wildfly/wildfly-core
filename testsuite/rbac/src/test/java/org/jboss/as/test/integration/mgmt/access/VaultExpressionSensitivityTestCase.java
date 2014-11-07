/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.mgmt.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_CONTROL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONFIGURED_REQUIRES_READ;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONFIGURED_REQUIRES_WRITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOW_RESOURCE_SERVICE_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VAULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VAULT_OPTIONS;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.rbac.Outcome;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.test.security.VaultHandler;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
@RunWith(WildflyTestRunner.class)
@ServerSetup({StandardExtensionSetupTask.class, VaultExpressionSensitivityTestCase.VaultSetupTask.class, StandardUsersSetupTask.class})
public class VaultExpressionSensitivityTestCase extends AbstractRbacTestCase {
    private static final String VAULT_EXPRESSION_SENSITIVITY = "core-service=management/access=authorization/constraint=vault-expression";

    private static final String MASKED_DS = "subsystem=rbac/rbac-constrained=MaskedDS";

    private static String vaultPassword;

    static class VaultSetupTask implements ServerSetupTask {

        private VaultHandler vaultHandler;
        private static final String RESOURCE_LOCATION = VaultExpressionSensitivityTestCase.class.getProtectionDomain().getCodeSource().getLocation().getFile()
                + "vault-masked/";
        private static String DATA_SOURCE = "MaskedDS";

        @Override
        public void setup(ManagementClient managementClient) throws Exception {

            VaultHandler.cleanFilesystem(RESOURCE_LOCATION, true);

            ModelNode op;

            // create new vault
            vaultHandler = new VaultHandler(RESOURCE_LOCATION);

            // create security attributes
            String vaultBlock = "ds_ExampleDS";
            String attributeName = "password";
            String vaultPasswordString = vaultHandler.addSecuredAttribute(vaultBlock, attributeName,
               "sa".toCharArray());

            vaultPassword = "${" +vaultPasswordString + "}";

            // create new vault setting in standalone
            op = new ModelNode();
            op.get(OP).set(ADD);
            op.get(OP_ADDR).add(CORE_SERVICE, VAULT);
            ModelNode vaultOption = op.get(VAULT_OPTIONS);
            vaultOption.get("KEYSTORE_URL").set(vaultHandler.getKeyStore());
            vaultOption.get("KEYSTORE_PASSWORD").set(vaultHandler.getMaskedKeyStorePassword());
            vaultOption.get("KEYSTORE_ALIAS").set(vaultHandler.getAlias());
            vaultOption.get("SALT").set(vaultHandler.getSalt());
            vaultOption.get("ITERATION_COUNT").set(vaultHandler.getIterationCountAsString());
            vaultOption.get("ENC_FILE_DIR").set(vaultHandler.getEncodedVaultFileDirectory());
            managementClient.getControllerClient().execute(new OperationBuilder(op).build());


            // create new datasource with right password
            ModelNode address = new ModelNode();
            address.add(SUBSYSTEM, "rbac");
            address.add("rbac-constrained", DATA_SOURCE);
            address.protect();
            op = new ModelNode();
            op.get(OP).set(ADD);
            op.get(OP_ADDR).set(address);
            op.get("jndi-name").set("java:jboss/datasources/" + DATA_SOURCE);
            op.get("new-connection-sql").set(vaultPassword);
            op.get("password").set(vaultPassword);
            op.get(OPERATION_HEADERS).get(ALLOW_RESOURCE_SERVICE_RESTART).set(true);
            managementClient.getControllerClient().execute(new OperationBuilder(op).build());

            op = createOpNode(MASKED_DS, WRITE_ATTRIBUTE_OPERATION);
            op.get(NAME).set("new-connection-sql");
            op.get(VALUE).set(vaultPassword);
            op.get(OPERATION_HEADERS).get(ALLOW_RESOURCE_SERVICE_RESTART).set(true);
            managementClient.getControllerClient().execute(new OperationBuilder(op).build());
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {

            ModelNode op;

            // remove created datasources
            op = new ModelNode();
            op.get(OP).set(REMOVE);
            op.get(OP_ADDR).add(SUBSYSTEM, "rbac");
            op.get(OP_ADDR).add("rbac-constrained", DATA_SOURCE);
            managementClient.getControllerClient().execute(new OperationBuilder(op).build());

            // remove created vault
            op = new ModelNode();
            op.get(OP).set(REMOVE);
            op.get(OP_ADDR).add(CORE_SERVICE, VAULT);
            managementClient.getControllerClient().execute(new OperationBuilder(op).build());

            // remove temporary files
            vaultHandler.cleanUp();

        }

    }
    @Test
    public void testReadSensitiveWriteSensitive() throws Exception {
        changeVaultExpressionSensitivity(true, true);
        try {
            test(RbacUtil.MONITOR_USER, false, false);
            test(RbacUtil.OPERATOR_USER, false, false);
            test(RbacUtil.MAINTAINER_USER, false, false);
            test(RbacUtil.DEPLOYER_USER, false, false);
            test(RbacUtil.ADMINISTRATOR_USER, true, true);
            test(RbacUtil.AUDITOR_USER, true, false);
            test(RbacUtil.SUPERUSER_USER, true, true);
        } finally {
            changeVaultExpressionSensitivity(null, null);
        }
    }

    @Test
    public void testReadNonSensitiveWriteSensitive() throws Exception {
        changeVaultExpressionSensitivity(false, true);
        try {
            test(RbacUtil.MONITOR_USER, true, false);
            test(RbacUtil.OPERATOR_USER, true, false);
            test(RbacUtil.MAINTAINER_USER, true, false);
            test(RbacUtil.DEPLOYER_USER, true, false);
            test(RbacUtil.ADMINISTRATOR_USER, true, true);
            test(RbacUtil.AUDITOR_USER, true, false);
            test(RbacUtil.SUPERUSER_USER, true, true);
        } finally {
            changeVaultExpressionSensitivity(null, null);
        }
    }

    @Test
    public void testReadSensitiveWriteNonSensitive() throws Exception {
        // read sensitive and write non-sensitive together makes very little, but still
        // note that the expectations are the same as in testReadSensitiveWriteSensitive

        changeVaultExpressionSensitivity(true, false);
        try {
            test(RbacUtil.MONITOR_USER, false, false);
            test(RbacUtil.OPERATOR_USER, false, false);
            test(RbacUtil.MAINTAINER_USER, false, false);
            test(RbacUtil.DEPLOYER_USER, false, false);
            test(RbacUtil.ADMINISTRATOR_USER, true, true);
            test(RbacUtil.AUDITOR_USER, true, false);
            test(RbacUtil.SUPERUSER_USER, true, true);
        } finally {
            changeVaultExpressionSensitivity(null, null);
        }
    }

    @Test
    public void testReadNonSensitiveWriteNonSensitive() throws Exception {
        changeVaultExpressionSensitivity(false, false);
        try {
            test(RbacUtil.MONITOR_USER, true, false);
            test(RbacUtil.OPERATOR_USER, true, false); // operator NO, it's a change in persistent configuration
            test(RbacUtil.MAINTAINER_USER, true, true);
            test(RbacUtil.DEPLOYER_USER, true, false);
            test(RbacUtil.ADMINISTRATOR_USER, true, true);
            test(RbacUtil.AUDITOR_USER, true, false);
            test(RbacUtil.SUPERUSER_USER, true, true);
        } finally {
            changeVaultExpressionSensitivity(null, null);
        }
    }

    private void test(String userName, boolean canRead, boolean canWrite) throws Exception {
        ModelControllerClient client = getClientForUser(userName);

        // read-resource
        ModelNode operation = createOpNode(MASKED_DS, READ_RESOURCE_OPERATION);
        ModelNode result = RbacUtil.executeOperation(client, operation, Outcome.SUCCESS);
        assertEquals(userName + " should " + (canRead ? "" : "NOT") + " be able to read", canRead, result.get(RESULT).hasDefined("new-connection-sql"));
        if (!canRead) {
            assertTrue(result.hasDefined(RESPONSE_HEADERS));
            List<ModelNode> filteredAttributes = result.get(RESPONSE_HEADERS, ACCESS_CONTROL).get(0).get("filtered-attributes").asList();
            assertTrue(filteredAttributes.contains(new ModelNode("new-connection-sql")));
        }

        // read-attribute
        operation = createOpNode(MASKED_DS, READ_ATTRIBUTE_OPERATION);
        operation.get(NAME).set("new-connection-sql");
        RbacUtil.executeOperation(client, operation, canRead ? Outcome.SUCCESS : Outcome.UNAUTHORIZED);

        // write-attribute
        operation = createOpNode(MASKED_DS, WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set("new-connection-sql");
        operation.get(VALUE).set(vaultPassword);
        RbacUtil.executeOperation(client, operation, canWrite ? Outcome.SUCCESS : Outcome.UNAUTHORIZED);
    }

    // test utils

    private void changeVaultExpressionSensitivity(Boolean readSensitive, Boolean writeSensitive) throws IOException {
        ModelControllerClient client = getManagementClient().getControllerClient();

        ModelNode operation;

        if (readSensitive != null) {
            operation = createOpNode(VAULT_EXPRESSION_SENSITIVITY, WRITE_ATTRIBUTE_OPERATION);
            operation.get(NAME).set(CONFIGURED_REQUIRES_READ);
            operation.get(VALUE).set(readSensitive);
        } else {
            operation = createOpNode(VAULT_EXPRESSION_SENSITIVITY, UNDEFINE_ATTRIBUTE_OPERATION);
            operation.get(NAME).set(CONFIGURED_REQUIRES_READ);
        }
        client.execute(operation);

        if (writeSensitive != null) {
            operation = createOpNode(VAULT_EXPRESSION_SENSITIVITY, WRITE_ATTRIBUTE_OPERATION);
            operation.get(NAME).set(CONFIGURED_REQUIRES_WRITE);
            operation.get(VALUE).set(writeSensitive);
        } else {
            operation = createOpNode(VAULT_EXPRESSION_SENSITIVITY, UNDEFINE_ATTRIBUTE_OPERATION);
            operation.get(NAME).set(CONFIGURED_REQUIRES_WRITE);
        }
        client.execute(operation);
    }
}
