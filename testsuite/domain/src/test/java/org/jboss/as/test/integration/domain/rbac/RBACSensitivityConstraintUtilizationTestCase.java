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

package org.jboss.as.test.integration.domain.rbac;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MONITOR_USER;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;

import java.io.IOException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.domain.suites.FullRbacProviderTestSuite;
import org.jboss.as.test.integration.management.rbac.Outcome;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.as.test.integration.management.rbac.UserRolesMappingServerSetupTask;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
//import org.junit.Ignore;
import org.junit.Test;

/**
 * Test to check that the sensitivity constraint is correctly propagated to a managed server and is kept after a restart of said server.
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
//@Ignore("[WFCORE-1958] Clean up testsuite Elytron registration.")
public class RBACSensitivityConstraintUtilizationTestCase extends AbstractRbacTestCase {

    @BeforeClass
    public static void setupDomain() throws Exception {
        // Launch the domain
        testSupport = FullRbacProviderTestSuite.createSupport(RBACSensitivityConstraintUtilizationTestCase.class.getSimpleName());
        masterClientConfig = testSupport.getDomainMasterConfiguration();
        DomainClient domainClient = testSupport.getDomainMasterLifecycleUtil().getDomainClient();
        UserRolesMappingServerSetupTask.StandardUsersSetup.INSTANCE.setup(domainClient);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        try {
            UserRolesMappingServerSetupTask.StandardUsersSetup.INSTANCE.tearDown(testSupport.getDomainMasterLifecycleUtil().getDomainClient());
        } finally {
            FullRbacProviderTestSuite.stopSupport();
            testSupport = null;
        }
    }

    @Override
    protected void configureRoles(ModelNode op, String[] roles) {
        RbacUtil.addRoleHeader(op, roles);
    }

    @Test
    public void testConstraintReplication() throws Exception {
        ModelControllerClient client = testSupport.getDomainMasterLifecycleUtil().getDomainClient();
        String path = "core-service=management/access=authorization/constraint=sensitivity-classification/type=core/classification=socket-config";
        try {
            checkRequiresRead(path, false, client);
            readSocketBinding(Outcome.SUCCESS, client);
            ModelControllerClient monitor = getClientForUser(MONITOR_USER, false, masterClientConfig);
            readSocketBinding(Outcome.SUCCESS, monitor);
            setRequiresRead(path, true, client);
            checkRequiresRead(path, true, client);
            readSocketBinding(Outcome.SUCCESS, client);
            readSocketBinding(Outcome.UNAUTHORIZED, monitor);
            ModelNode op = createOpNode("host=primary/server-config=primary-a", RESTART);
            op.get(BLOCKING).set(true);
            RbacUtil.executeOperation(client, op, Outcome.SUCCESS);
            readSocketBinding(Outcome.SUCCESS, client);
            readSocketBinding(Outcome.UNAUTHORIZED, monitor);
        } finally {
            setRequiresRead(path, false, client);
        }
    }


    private static void setRequiresRead(String path, boolean requiresRead, ModelControllerClient client) throws IOException {
        ModelNode operation;
        if(requiresRead) {
            operation = createOpNode(path, WRITE_ATTRIBUTE_OPERATION);
            operation.get(VALUE).set(requiresRead);
        } else {
            operation = createOpNode(path, UNDEFINE_ATTRIBUTE_OPERATION);
        }
        operation.get(NAME).set(ModelDescriptionConstants.CONFIGURED_REQUIRES_READ);
        RbacUtil.executeOperation(client, operation, Outcome.SUCCESS);
    }

    private static void checkRequiresRead(String path, boolean requiresRead, ModelControllerClient client) throws IOException {
        ModelNode operation = createOpNode(path, READ_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(ModelDescriptionConstants.CONFIGURED_REQUIRES_READ);
        String message = requiresRead ? path + " should be an application" : path + " shouldn't be an application";
        ModelNode result = RbacUtil.executeOperation(client, operation, Outcome.SUCCESS).get(ModelDescriptionConstants.RESULT);
        if(requiresRead) {
            Assert.assertTrue(message, result.asBoolean());
        } else {
            Assert.assertFalse(message, result.isDefined());
        }
    }

    private static void readSocketBinding(Outcome expectedOutcome, ModelControllerClient client) throws IOException {
        ModelNode operation = createOpNode("host=primary/server=primary-a/socket-binding-group=sockets-a/socket-binding=http", READ_RESOURCE_OPERATION);
        RbacUtil.executeOperation(client, operation, expectedOutcome);
    }
}
