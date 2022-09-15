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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BASE_ROLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOSTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROXIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE_DEPTH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;

import java.io.IOException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.rbac.Outcome;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Abstract superclass of access control provider test cases covering host scoped roles.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public abstract class AbstractHostScopedRolesTestCase extends AbstractRbacTestCase implements RbacDomainRolesTests {

    public static final String MONITOR_USER = "HostMasterMonitor";
    public static final String OPERATOR_USER = "HostMasterOperator";
    public static final String MAINTAINER_USER = "HostMasterMaintainer";
    public static final String DEPLOYER_USER = "HostMasterDeployer";
    public static final String ADMINISTRATOR_USER = "HostMasterAdministrator";
    public static final String AUDITOR_USER = "HostMasterAuditor";
    public static final String SUPERUSER_USER = "HostMasterSuperUser";
    public static final String SECONDARY_MONITOR_USER = "HostSlaveMonitor";
    public static final String SECONDARY_OPERATOR_USER = "HostSlaveOperator";
    public static final String SECONDARY_MAINTAINER_USER = "HostSlaveMaintainer";
    public static final String SECONDARY_DEPLOYER_USER = "HostSlaveDeployer";
    public static final String SECONDARY_ADMINISTRATOR_USER = "HostSlaveAdministrator";
    public static final String SECONDARY_AUDITOR_USER = "HostSlaveAuditor";
    public static final String SECONDARY_SUPERUSER_USER = "HostSecondarySuperUser";

    public static final String[] USERS = { MONITOR_USER, OPERATOR_USER, MAINTAINER_USER, DEPLOYER_USER,
            ADMINISTRATOR_USER, AUDITOR_USER, SUPERUSER_USER };
    static final String[] SECONDARY_USERS = {SECONDARY_MONITOR_USER, SECONDARY_OPERATOR_USER, SECONDARY_MAINTAINER_USER,
            SECONDARY_DEPLOYER_USER, SECONDARY_ADMINISTRATOR_USER, SECONDARY_AUDITOR_USER, SECONDARY_SUPERUSER_USER};
    private static final String[] BASES = { RbacUtil.MONITOR_USER, RbacUtil.OPERATOR_USER, RbacUtil.MAINTAINER_USER,
            RbacUtil.DEPLOYER_USER, RbacUtil.ADMINISTRATOR_USER, RbacUtil.AUDITOR_USER,
            RbacUtil.SUPERUSER_USER };

    protected static final String SCOPED_ROLE = "core-service=management/access=authorization/host-scoped-role=";

    protected static void setupRoles(DomainClient domainClient) throws IOException {
        for (int i = 0; i < USERS.length; i++) {
            ModelNode op = createOpNode(SCOPED_ROLE + USERS[i], ADD);
            op.get(BASE_ROLE).set(BASES[i]);
            op.get(HOSTS).add(PRIMARY);
            RbacUtil.executeOperation(domainClient, op, Outcome.SUCCESS);
        }
        for (int i = 0; i < SECONDARY_USERS.length; i++) {
            ModelNode op = createOpNode(SCOPED_ROLE + SECONDARY_USERS[i], ADD);
            op.get(BASE_ROLE).set(BASES[i]);
            op.get(HOSTS).add(SECONDARY);
            RbacUtil.executeOperation(domainClient, op, Outcome.SUCCESS);
        }
    }

    protected static void tearDownRoles(DomainClient domainClient) throws IOException {
        for (String role : USERS) {
            ModelNode op = createOpNode(SCOPED_ROLE + role, REMOVE);
            RbacUtil.executeOperation(domainClient, op, Outcome.SUCCESS);
        }
        for (String role : SECONDARY_USERS) {
            ModelNode op = createOpNode(SCOPED_ROLE + role, REMOVE);
            RbacUtil.executeOperation(domainClient, op, Outcome.SUCCESS);
        }
    }

    @After
    public void tearDown() throws IOException {
        AssertionError assertionError = null;
        String[] toRemove = {DEPLOYMENT_2, TEST_PATH, getPrefixedAddress(HOST, PRIMARY, SMALL_JVM),
                getPrefixedAddress(HOST, SECONDARY, SMALL_JVM),
                getPrefixedAddress(HOST, SECONDARY, SCOPED_ROLE_SERVER),
                getPrefixedAddress(HOST, PRIMARY, SCOPED_ROLE_SERVER)};
        for (String address : toRemove) {
            try {
                removeResource(address);
            } catch (AssertionError e) {
                if (assertionError == null) {
                    assertionError = e;
                }
            }
        }


        if (assertionError != null) {
            throw assertionError;
        }
    }

    protected abstract boolean isAllowLocalAuth();

    protected abstract void configureRoles(ModelNode op, String[] roles);

    @Test
    public void testMonitor() throws Exception {
        ModelControllerClient client = getClientForUser(MONITOR_USER, isAllowLocalAuth(), primaryClientConfig);
        readWholeConfig(client, Outcome.UNAUTHORIZED, MONITOR_USER);
        checkStandardReads(client, null, null, MONITOR_USER);
        checkRootRead(client, null, null, Outcome.SUCCESS, MONITOR_USER);
        checkRootRead(client, PRIMARY, null, Outcome.SUCCESS, MONITOR_USER);
        checkRootRead(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, MONITOR_USER);
        checkRootRead(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, MONITOR_USER);
        readResource(client, AUTHORIZATION, null, null, Outcome.HIDDEN, MONITOR_USER);
        readResource(client, AUTHORIZATION, PRIMARY, PRIMARY_A, Outcome.HIDDEN, MONITOR_USER);
        readResource(client, AUTHORIZATION, SECONDARY, SECONDARY_B, Outcome.HIDDEN, MONITOR_USER);
        checkSecurityDomainRead(client, null, null, Outcome.HIDDEN, MONITOR_USER);
        checkSecurityDomainRead(client, PRIMARY, PRIMARY_A, Outcome.HIDDEN, MONITOR_USER);
        checkSecurityDomainRead(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, MONITOR_USER);
        checkSensitiveAttribute(client, null, null, false, MONITOR_USER);
        checkSensitiveAttribute(client, PRIMARY, PRIMARY_A, false, MONITOR_USER);
        testHostScopedRoleCanReadHostChildResources(client, MONITOR_USER);

        if (readOnly) return;

        runGC(client, PRIMARY, null, Outcome.UNAUTHORIZED, MONITOR_USER);
        runGC(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, MONITOR_USER);
        runGC(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, MONITOR_USER);
        addDeployment2(client, Outcome.UNAUTHORIZED, MONITOR_USER);
        addPath(client, Outcome.UNAUTHORIZED, MONITOR_USER);
        addJvm(client, HOST, PRIMARY, Outcome.UNAUTHORIZED, MONITOR_USER);
        addJvm(client, HOST, SECONDARY, Outcome.HIDDEN, MONITOR_USER);

        testWLFY2299(client, Outcome.UNAUTHORIZED, MONITOR_USER);
        restartServer(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, MONITOR_USER);

        stopServer(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, MONITOR_USER);
        killServer(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, MONITOR_USER);
        destroyServer(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, MONITOR_USER);
        killServersInGroup(client, Outcome.UNAUTHORIZED, MONITOR_USER);
        destroyServersInGroup(client, Outcome.UNAUTHORIZED, MONITOR_USER);
    }

    @Test
    public void testOperator() throws Exception {
        ModelControllerClient client = getClientForUser(OPERATOR_USER, isAllowLocalAuth(), primaryClientConfig);
        readWholeConfig(client, Outcome.UNAUTHORIZED, OPERATOR_USER);
        checkStandardReads(client, null, null, OPERATOR_USER);
        checkRootRead(client, null, null, Outcome.SUCCESS, OPERATOR_USER);
        checkRootRead(client, PRIMARY, null, Outcome.SUCCESS, OPERATOR_USER);
        checkRootRead(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, OPERATOR_USER);
        checkRootRead(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, OPERATOR_USER);
        readResource(client, AUTHORIZATION, null, null, Outcome.HIDDEN, OPERATOR_USER);
        readResource(client, AUTHORIZATION, PRIMARY, PRIMARY_A, Outcome.HIDDEN, OPERATOR_USER);
        readResource(client, AUTHORIZATION, SECONDARY, SECONDARY_B, Outcome.HIDDEN, OPERATOR_USER);
        checkSecurityDomainRead(client, null, null, Outcome.HIDDEN, OPERATOR_USER);
        checkSecurityDomainRead(client, PRIMARY, PRIMARY_A, Outcome.HIDDEN, OPERATOR_USER);
        checkSecurityDomainRead(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, OPERATOR_USER);
        checkSensitiveAttribute(client, null, null, false, OPERATOR_USER);
        checkSensitiveAttribute(client, PRIMARY, PRIMARY_A, false, OPERATOR_USER);
        testHostScopedRoleCanReadHostChildResources(client, OPERATOR_USER);

        if (readOnly) return;

        runGC(client, PRIMARY, null, Outcome.SUCCESS, OPERATOR_USER);
        runGC(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, OPERATOR_USER);
        runGC(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, OPERATOR_USER);
        addDeployment2(client, Outcome.UNAUTHORIZED, OPERATOR_USER);
        addPath(client, Outcome.UNAUTHORIZED, OPERATOR_USER);
        addJvm(client, HOST, PRIMARY, Outcome.UNAUTHORIZED, OPERATOR_USER);
        addJvm(client, HOST, SECONDARY, Outcome.HIDDEN, OPERATOR_USER);

        testWLFY2299(client, Outcome.UNAUTHORIZED, OPERATOR_USER);
        restartServer(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, OPERATOR_USER);
    }

    @Test
    public void testMaintainer() throws Exception {
        ModelControllerClient client = getClientForUser(MAINTAINER_USER, isAllowLocalAuth(), primaryClientConfig);
        readWholeConfig(client, Outcome.UNAUTHORIZED, MAINTAINER_USER);
        checkStandardReads(client, null, null, MAINTAINER_USER);
        checkRootRead(client, null, null, Outcome.SUCCESS, MAINTAINER_USER);
        checkRootRead(client, PRIMARY, null, Outcome.SUCCESS, MAINTAINER_USER);
        checkRootRead(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, MAINTAINER_USER);
        checkRootRead(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, MAINTAINER_USER);
        readResource(client, AUTHORIZATION, null, null, Outcome.HIDDEN, MAINTAINER_USER);
        readResource(client, AUTHORIZATION, PRIMARY, PRIMARY_A, Outcome.HIDDEN, MAINTAINER_USER);
        readResource(client, AUTHORIZATION, SECONDARY, SECONDARY_B, Outcome.HIDDEN, MAINTAINER_USER);
        checkSecurityDomainRead(client, null, null, Outcome.HIDDEN, MAINTAINER_USER);
        checkSecurityDomainRead(client, PRIMARY, PRIMARY_A, Outcome.HIDDEN, MAINTAINER_USER);
        checkSecurityDomainRead(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, MAINTAINER_USER);
        checkSensitiveAttribute(client, null, null, false, MAINTAINER_USER);
        checkSensitiveAttribute(client, PRIMARY, PRIMARY_A, false, MAINTAINER_USER);
        testHostScopedRoleCanReadHostChildResources(client, MAINTAINER_USER);

        if (readOnly) return;

        runGC(client, PRIMARY, null, Outcome.SUCCESS, MAINTAINER_USER);
        runGC(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, MAINTAINER_USER);
        runGC(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, MAINTAINER_USER);
        addDeployment2(client, Outcome.UNAUTHORIZED, MAINTAINER_USER);
        addPath(client, Outcome.UNAUTHORIZED, MAINTAINER_USER);
        addJvm(client, HOST, PRIMARY, Outcome.SUCCESS, MAINTAINER_USER);
        addJvm(client, HOST, SECONDARY, Outcome.HIDDEN, MAINTAINER_USER);

        testWLFY2299(client, Outcome.SUCCESS, MAINTAINER_USER);
    }

    @Test
    public void testDeployer() throws Exception {
        ModelControllerClient client = getClientForUser(DEPLOYER_USER, isAllowLocalAuth(), primaryClientConfig);
        readWholeConfig(client, Outcome.UNAUTHORIZED, DEPLOYER_USER);
        checkStandardReads(client, null, null, DEPLOYER_USER);
        checkRootRead(client, null, null, Outcome.SUCCESS, DEPLOYER_USER);
        checkRootRead(client, PRIMARY, null, Outcome.SUCCESS, DEPLOYER_USER);
        checkRootRead(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, DEPLOYER_USER);
        checkRootRead(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, DEPLOYER_USER);
        readResource(client, AUTHORIZATION, null, null, Outcome.HIDDEN, DEPLOYER_USER);
        readResource(client, AUTHORIZATION, PRIMARY, PRIMARY_A, Outcome.HIDDEN, DEPLOYER_USER);
        readResource(client, AUTHORIZATION, SECONDARY, SECONDARY_B, Outcome.HIDDEN, DEPLOYER_USER);
        checkSecurityDomainRead(client, null, null, Outcome.HIDDEN, DEPLOYER_USER);
        checkSecurityDomainRead(client, PRIMARY, PRIMARY_A, Outcome.HIDDEN, DEPLOYER_USER);
        checkSecurityDomainRead(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, DEPLOYER_USER);
        checkSensitiveAttribute(client, null, null, false, DEPLOYER_USER);
        checkSensitiveAttribute(client, PRIMARY, PRIMARY_A, false, DEPLOYER_USER);
        testHostScopedRoleCanReadHostChildResources(client, DEPLOYER_USER);

        if (readOnly) return;

        runGC(client, PRIMARY, null, Outcome.UNAUTHORIZED, DEPLOYER_USER);
        runGC(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, DEPLOYER_USER);
        runGC(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, DEPLOYER_USER);
        addDeployment2(client, Outcome.UNAUTHORIZED, DEPLOYER_USER);
        addPath(client, Outcome.UNAUTHORIZED, DEPLOYER_USER);
        addJvm(client, HOST, PRIMARY, Outcome.UNAUTHORIZED, DEPLOYER_USER);
        addJvm(client, HOST, SECONDARY, Outcome.HIDDEN, DEPLOYER_USER);

        testWLFY2299(client, Outcome.UNAUTHORIZED, DEPLOYER_USER);
        restartServer(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, DEPLOYER_USER);

        stopServer(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, DEPLOYER_USER);
        killServer(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, DEPLOYER_USER);
        destroyServer(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, DEPLOYER_USER);
        killServersInGroup(client, Outcome.UNAUTHORIZED, DEPLOYER_USER);
        destroyServersInGroup(client, Outcome.UNAUTHORIZED, DEPLOYER_USER);
    }

    @Test
    public void testAdministrator() throws Exception {
        ModelControllerClient client = getClientForUser(ADMINISTRATOR_USER, isAllowLocalAuth(), primaryClientConfig);
        readWholeConfig(client, Outcome.UNAUTHORIZED, ADMINISTRATOR_USER);
        checkStandardReads(client, null, null, ADMINISTRATOR_USER);
        checkRootRead(client, null, null, Outcome.SUCCESS, ADMINISTRATOR_USER);
        checkRootRead(client, PRIMARY, null, Outcome.SUCCESS, ADMINISTRATOR_USER);
        checkRootRead(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, ADMINISTRATOR_USER);
        checkRootRead(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, ADMINISTRATOR_USER);
        readResource(client, AUTHORIZATION, null, null, Outcome.HIDDEN, ADMINISTRATOR_USER);
        readResource(client, AUTHORIZATION, PRIMARY, PRIMARY_A, Outcome.SUCCESS, ADMINISTRATOR_USER);
        readResource(client, AUTHORIZATION, SECONDARY, SECONDARY_B, Outcome.HIDDEN, ADMINISTRATOR_USER);
        checkSecurityDomainRead(client, null, null, Outcome.HIDDEN, ADMINISTRATOR_USER);
        checkSecurityDomainRead(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, ADMINISTRATOR_USER);
        checkSecurityDomainRead(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, ADMINISTRATOR_USER);
        checkSensitiveAttribute(client, null, null, false, ADMINISTRATOR_USER);
        checkSensitiveAttribute(client, PRIMARY, PRIMARY_A, true, ADMINISTRATOR_USER);
        testHostScopedRoleCanReadHostChildResources(client, ADMINISTRATOR_USER);

        if (readOnly) return;

        runGC(client, PRIMARY, null, Outcome.SUCCESS, ADMINISTRATOR_USER);
        runGC(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, ADMINISTRATOR_USER);
        runGC(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, ADMINISTRATOR_USER);
        addDeployment2(client, Outcome.UNAUTHORIZED, ADMINISTRATOR_USER);
        addPath(client, Outcome.UNAUTHORIZED, ADMINISTRATOR_USER);
        addJvm(client, HOST, PRIMARY, Outcome.SUCCESS, ADMINISTRATOR_USER);
        addJvm(client, HOST, SECONDARY, Outcome.HIDDEN, ADMINISTRATOR_USER);

        testWLFY2299(client, Outcome.SUCCESS, ADMINISTRATOR_USER);
    }

    @Test
    public void testAuditor() throws Exception {
        ModelControllerClient client = getClientForUser(AUDITOR_USER, isAllowLocalAuth(), primaryClientConfig);
        readWholeConfig(client, Outcome.UNAUTHORIZED, AUDITOR_USER);
        checkStandardReads(client, null, null, AUDITOR_USER);
        checkRootRead(client, null, null, Outcome.SUCCESS, AUDITOR_USER);
        checkRootRead(client, PRIMARY, null, Outcome.SUCCESS, AUDITOR_USER);
        checkRootRead(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, AUDITOR_USER);
        checkRootRead(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, AUDITOR_USER);
        readResource(client, AUTHORIZATION, null, null, Outcome.HIDDEN, AUDITOR_USER);
        readResource(client, AUTHORIZATION, PRIMARY, PRIMARY_A, Outcome.SUCCESS, AUDITOR_USER);
        readResource(client, AUTHORIZATION, SECONDARY, SECONDARY_B, Outcome.HIDDEN, AUDITOR_USER);
        checkSecurityDomainRead(client, null, null, Outcome.HIDDEN, AUDITOR_USER);
        checkSecurityDomainRead(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, AUDITOR_USER);
        checkSecurityDomainRead(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, AUDITOR_USER);
        checkSensitiveAttribute(client, null, null, false, AUDITOR_USER);
        checkSensitiveAttribute(client, PRIMARY, PRIMARY_A, true, AUDITOR_USER);
        testHostScopedRoleCanReadHostChildResources(client, AUDITOR_USER);

        if (readOnly) return;

        runGC(client, PRIMARY, null, Outcome.UNAUTHORIZED, AUDITOR_USER);
        runGC(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, AUDITOR_USER);
        runGC(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, AUDITOR_USER);
        addDeployment2(client, Outcome.UNAUTHORIZED, AUDITOR_USER);
        addPath(client, Outcome.UNAUTHORIZED, AUDITOR_USER);
        addJvm(client, HOST, PRIMARY, Outcome.UNAUTHORIZED, AUDITOR_USER);
        addJvm(client, HOST, SECONDARY, Outcome.HIDDEN, AUDITOR_USER);

        testWLFY2299(client, Outcome.UNAUTHORIZED, AUDITOR_USER);
        restartServer(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, AUDITOR_USER);

        stopServer(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, AUDITOR_USER);
        killServer(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, AUDITOR_USER);
        destroyServer(client, PRIMARY, PRIMARY_A, Outcome.UNAUTHORIZED, AUDITOR_USER);
        killServersInGroup(client, Outcome.UNAUTHORIZED, AUDITOR_USER);
        destroyServersInGroup(client, Outcome.UNAUTHORIZED, AUDITOR_USER);
    }

    @Test
    public void testSuperUser() throws Exception {
        ModelControllerClient client = getClientForUser(SUPERUSER_USER, isAllowLocalAuth(), primaryClientConfig);
        readWholeConfig(client, Outcome.UNAUTHORIZED, SUPERUSER_USER);
        checkStandardReads(client, null, null, SUPERUSER_USER);
        checkRootRead(client, null, null, Outcome.SUCCESS, SUPERUSER_USER);
        checkRootRead(client, PRIMARY, null, Outcome.SUCCESS, SUPERUSER_USER);
        checkRootRead(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, SUPERUSER_USER);
        checkRootRead(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, SUPERUSER_USER);
        readResource(client, AUTHORIZATION, null, null, Outcome.HIDDEN, SUPERUSER_USER);
        readResource(client, AUTHORIZATION, PRIMARY, PRIMARY_A, Outcome.SUCCESS, SUPERUSER_USER);
        readResource(client, AUTHORIZATION, SECONDARY, SECONDARY_B, Outcome.HIDDEN, SUPERUSER_USER);
        checkSecurityDomainRead(client, null, null, Outcome.HIDDEN, SUPERUSER_USER);
        checkSecurityDomainRead(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, SUPERUSER_USER);
        checkSecurityDomainRead(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, SUPERUSER_USER);
        checkSensitiveAttribute(client, null, null, false, SUPERUSER_USER);
        checkSensitiveAttribute(client, PRIMARY, PRIMARY_A, true, SUPERUSER_USER);
        testHostScopedRoleCanReadHostChildResources(client, SUPERUSER_USER);

        if (readOnly) return;

        runGC(client, PRIMARY, null, Outcome.SUCCESS, SUPERUSER_USER);
        runGC(client, PRIMARY, PRIMARY_A, Outcome.SUCCESS, SUPERUSER_USER);
        runGC(client, SECONDARY, SECONDARY_B, Outcome.HIDDEN, SUPERUSER_USER);
        addDeployment2(client, Outcome.UNAUTHORIZED, SUPERUSER_USER);
        addPath(client, Outcome.UNAUTHORIZED, SUPERUSER_USER);
        addJvm(client, HOST, PRIMARY, Outcome.SUCCESS, SUPERUSER_USER);
        addJvm(client, HOST, SECONDARY, Outcome.HIDDEN, SUPERUSER_USER);

        testWLFY2299(client, Outcome.SUCCESS, SUPERUSER_USER);
    }

    private void testHostScopedRoleCanReadHostChildResources(ModelControllerClient client, String... roles) throws Exception {
        ModelNode op = Util.createOperation(READ_CHILDREN_RESOURCES_OPERATION, PathAddress.EMPTY_ADDRESS);
        op.get(CHILD_TYPE).set(HOST);
        configureRoles(op, roles);
        //System.out.println("host scoped read host child resources result for " + roles[0]);
        //System.out.println(RbacUtil.executeOperation(client, op, Outcome.SUCCESS));
        RbacUtil.executeOperation(client, op, Outcome.SUCCESS);


        op = Util.createOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.EMPTY_ADDRESS);
        op.get(RECURSIVE_DEPTH).set(2);
        op.get(PROXIES).set(true);
        configureRoles(op, roles);
        //System.out.println("host scoped :read-resource-description(recursive-depth=1,proxies=true) result for " + roles[0]);
        //System.out.println(RbacUtil.executeOperation(client, op, Outcome.SUCCESS));
        RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

    }

    private void testWLFY2299(ModelControllerClient client, Outcome expected, String... roles) throws IOException {

        addServerConfig(client, SECONDARY, SERVER_GROUP_A, Outcome.HIDDEN, roles);
        addServerConfig(client, PRIMARY, SERVER_GROUP_A, expected, roles);

        ModelNode metadata = getServerConfigAccessControl(client, roles);
        ModelNode add = metadata.get("default", "operations", "add", "execute");
        Assert.assertTrue(add.isDefined());
        Assert.assertEquals(expected == Outcome.SUCCESS, add.asBoolean());

        ModelNode writeConfig = metadata.get("default", "write");
        Assert.assertTrue(writeConfig.isDefined());
        Assert.assertEquals(expected == Outcome.SUCCESS, writeConfig.asBoolean());
    }
}
