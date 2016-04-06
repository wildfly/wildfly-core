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

import static org.jboss.as.controller.PathAddress.pathAddress;
import static org.jboss.as.controller.PathElement.pathElement;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.APPLICATION_CLASSIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLASSIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONFIGURED_APPLICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONSTRAINT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import java.io.IOException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.rbac.Outcome;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
@RunWith(WildflyTestRunner.class)
@ServerSetup({StandardUsersSetupTask.class, StandardExtensionSetupTask.class})
public class ApplicationTypeTestCase extends AbstractRbacTestCase {

    @Test
    public void testMonitor() throws Exception {
        test(false, false, getClientForUser(RbacUtil.MONITOR_USER));
    }

    @Test
    public void testOperator() throws Exception {
        test(false, false, getClientForUser(RbacUtil.OPERATOR_USER));
    }

    @Test
    public void testMaintainer() throws Exception {
        test(true, true, getClientForUser(RbacUtil.MAINTAINER_USER));
    }

    @Test
    public void testDeployer() throws Exception {
        test(true, false, getClientForUser(RbacUtil.DEPLOYER_USER));
    }

    @Test
    public void testAdministrator() throws Exception {
        test(true, true, getClientForUser(RbacUtil.ADMINISTRATOR_USER));
    }

    @Test
    public void testAuditor() throws Exception {
        test(false, false, getClientForUser(RbacUtil.AUDITOR_USER));
    }

    @Test
    public void testSuperUser() throws Exception {
        test(true, true, getClientForUser(RbacUtil.SUPERUSER_USER));
    }

    private void test(boolean canWriteWhenApplicationSet, boolean canWriteWhenApplicationNotSet, ModelControllerClient client) throws IOException {
        testDataSource(canWriteWhenApplicationNotSet, client);

        setDataSourceAsApplicationType(true, getManagementClient().getControllerClient());
        testDataSource(canWriteWhenApplicationSet, client);

        setDataSourceAsApplicationType(false, getManagementClient().getControllerClient());
        testDataSource(canWriteWhenApplicationNotSet, client);

        setDataSourceAsApplicationType(null, getManagementClient().getControllerClient());
        testDataSource(canWriteWhenApplicationNotSet, client);
    }

    private void testDataSource(boolean canWrite, ModelControllerClient client) throws IOException {
        ModelNode operation = Util.createOperation(WRITE_ATTRIBUTE_OPERATION, pathAddress(
                pathElement(SUBSYSTEM, "rbac"),
                pathElement("rbac-constrained", "default")
        ));
        operation.get(NAME).set("new-connection-sql");
        operation.get(VALUE).set("java:jboss/datasources/ExampleDS_XXX");
        try {
            RbacUtil.executeOperation(client, operation, canWrite ? Outcome.SUCCESS : Outcome.UNAUTHORIZED);
        } finally {
            operation.get(VALUE).set("java:jboss/datasources/ExampleDS");
            RbacUtil.executeOperation(getManagementClient().getControllerClient(), operation, Outcome.SUCCESS);
        }
    }

    private static void setDataSourceAsApplicationType(Boolean isApplication, ModelControllerClient client) throws IOException {
        setApplicationType("rbac", "datasource", isApplication, client);
    }

    private static void setApplicationType(String type, String classification, Boolean isApplication, ModelControllerClient client) throws IOException {
        ModelNode readClassification = Util.createOperation(ModelDescriptionConstants.READ_RESOURCE_OPERATION, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(CONSTRAINT, APPLICATION_CLASSIFICATION)));
        readClassification.get(ModelDescriptionConstants.RECURSIVE).set(true);
        readClassification.get(ModelDescriptionConstants.INCLUDE_RUNTIME).set(true);
        ModelNode classifications = client.execute(readClassification);
        System.out.println("Calssifications " + classifications);
        String operationName = isApplication != null ? WRITE_ATTRIBUTE_OPERATION : UNDEFINE_ATTRIBUTE_OPERATION;
        ModelNode operation = Util.createOperation(operationName, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(CONSTRAINT, APPLICATION_CLASSIFICATION),
                pathElement(TYPE, type),
                pathElement(CLASSIFICATION, classification)
        ));
        operation.get(NAME).set(CONFIGURED_APPLICATION);
        if (isApplication != null) {
            operation.get(VALUE).set(isApplication);
        }
        RbacUtil.executeOperation(client, operation, Outcome.SUCCESS);
    }
}
