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

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_CONTROL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXECUTE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_GROUP_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.rbac.Outcome;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Test to check RABC access on reading attributes per group name.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a>  (c) 2015 Red Hat, inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerSetup({StandardUsersSetupTask.class, BasicExtensionSetupTask.class})
public class ReadAttributeGroupTestCase extends AbstractRbacTestCase {

    private static final String TEST_DS = "subsystem=rbac/rbac-constrained=default";

    @Test
    public void testMonitor() throws Exception {
        test(RbacUtil.MONITOR_USER, false);
    }

    @Test
    public void testOperator() throws Exception {
        test(RbacUtil.OPERATOR_USER, false);
    }

    @Test
    public void testMaintainer() throws Exception {
        test(RbacUtil.MAINTAINER_USER, false);
    }

    @Test
    public void testDeployer() throws Exception {
        test(RbacUtil.DEPLOYER_USER, false);
    }

    @Test
    public void testAdministrator() throws Exception {
        test(RbacUtil.ADMINISTRATOR_USER, true);
    }

    @Test
    public void testAuditor() throws Exception {
        test(RbacUtil.AUDITOR_USER, true);
    }

    @Test
    public void testSuperUser() throws Exception {
        test(RbacUtil.SUPERUSER_USER, true);
    }

    private void test(String userName, boolean canRead) throws IOException {
        ModelControllerClient client = getClientForUser(userName);
        assertTrue(canExecuteOperation(client, READ_ATTRIBUTE_GROUP_OPERATION, TEST_DS));
        ModelNode operation = createOpNode(TEST_DS, READ_ATTRIBUTE_GROUP_OPERATION);
        operation.get(NAME).set("security");
        ModelNode attributesNode = RbacUtil.executeOperation(client, operation, Outcome.SUCCESS).get(RESULT);
        assertThat(attributesNode.isDefined(), is(true));
        List<Property> attributes = attributesNode.asPropertyList();
        List<String> attributeNames = new ArrayList<>();
        for (Property attribute : attributes) {
            if(attribute.getValue().isDefined()) {
                attributeNames.add(attribute.getName());
            }
        }
        assertThat("We have found " + attributesNode + " for " + userName, attributeNames.size(), is(canRead ? 2 : 0));
    }

    // test utils
    private boolean canExecuteOperation(ModelControllerClient client, String opName, String path) throws IOException {
        ModelNode operation = createOpNode(path, READ_RESOURCE_DESCRIPTION_OPERATION);
        operation.get(OPERATIONS).set(true);
        operation.get(ACCESS_CONTROL).set("trim-descriptions");
        ModelNode result = RbacUtil.executeOperation(client, operation, Outcome.SUCCESS);

        ModelNode clone = result.clone();
        ModelNode allowExecute = clone.get(RESULT, ACCESS_CONTROL, DEFAULT, OPERATIONS, opName, EXECUTE);
        assertTrue(result.toString(), allowExecute.isDefined());
        return allowExecute.asBoolean();
    }

    @Before
    public void createResource() throws IOException {
        ModelControllerClient client = getManagementClient().getControllerClient();
        ModelNode op = createOpNode(TEST_DS, ADD);
        op.get("connection-url").set("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        op.get("jndi-name").set("java:jboss/datasources/TestDS");
        op.get("driver-name").set("h2");
        op.get("password").set("sa");
        op.get("security-domain").set("other");
        ModelNode result = client.execute(op);
        assertEquals(result.asString(), SUCCESS, result.get(OUTCOME).asString());
    }

    @After
    public void removeResource() throws IOException {
        ModelControllerClient client = getManagementClient().getControllerClient();

        ModelNode op = createOpNode(TEST_DS, READ_RESOURCE_OPERATION);
        ModelNode result = client.execute(op);
        if (SUCCESS.equals(result.get(OUTCOME).asString())) {
            op = createOpNode(TEST_DS, REMOVE);
            result = client.execute(op);
            assertEquals(result.asString(), SUCCESS, result.get(OUTCOME).asString());
        }
    }
}
