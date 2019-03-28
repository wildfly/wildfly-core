/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.integration.domain.management;

import static org.jboss.as.controller.client.helpers.ClientConstants.STEPS;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class LegacySecurityRealmPropagationTestCase {
    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;
    private static final Path masterUsers
            = DomainTestSupport.getHostDir(LegacySecurityRealmPropagationTestCase.class.getSimpleName(), "master").toPath()
            .resolve("configuration")
            .resolve("test-users.properties");
    private static final Path slaveUsers
            = DomainTestSupport.getHostDir(LegacySecurityRealmPropagationTestCase.class.getSimpleName(), "slave").toPath()
            .resolve("configuration")
            .resolve("test-users.properties");

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSupport.createAndStartDefaultSupport(LegacySecurityRealmPropagationTestCase.class.getSimpleName());
        Files.createFile(masterUsers);
        Files.createFile(slaveUsers);
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.close();
        testSupport = null;
        domainMasterLifecycleUtil = null;
        domainSlaveLifecycleUtil = null;
        Files.deleteIfExists(masterUsers);
        Files.deleteIfExists(slaveUsers);
    }

    @Test
    public void test() throws Throwable {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();
        DomainTestSupport.validateResponse(client.execute(addSecurityRealm()), false);
        ModelNode addRemotingOp = Operations.createAddOperation(PathAddress.parseCLIStyleAddress("/profile=other/subsystem=remoting/connector=test").toModelNode());
        addRemotingOp.get("security-realm").set("TestRealm");
        addRemotingOp.get("socket-binding").set("remoting");
        DomainTestSupport.validateResponse(client.execute(addRemotingOp), false);
        addRemotingOp = Operations.createAddOperation(PathAddress.parseCLIStyleAddress("/profile=default/subsystem=remoting/connector=test").toModelNode());
        addRemotingOp.get("security-realm").set("TestRealm");
        addRemotingOp.get("socket-binding").set("remoting");
        DomainTestSupport.validateResponse(client.execute(addRemotingOp), false);
    }

    private ModelNode addSecurityRealm() {
        ModelNode operation = Operations.createCompositeOperation();
        operation.get(STEPS).add(Operations.createAddOperation(PathAddress.parseCLIStyleAddress("/host=master/core-service=management/security-realm=TestRealm").toModelNode()));
        ModelNode authAddOp = Operations.createAddOperation(PathAddress.parseCLIStyleAddress("/host=master/core-service=management/security-realm=TestRealm/authentication=properties").toModelNode());
        authAddOp.get("path").set("test-users.properties");
        authAddOp.get("relative-to").set("jboss.domain.config.dir");
        operation.get(STEPS).add(authAddOp);
        operation.get(STEPS).add(Operations.createAddOperation(PathAddress.parseCLIStyleAddress("/host=slave/core-service=management/security-realm=TestRealm").toModelNode()));
        authAddOp = Operations.createAddOperation(PathAddress.parseCLIStyleAddress("/host=slave/core-service=management/security-realm=TestRealm/authentication=properties").toModelNode());
        authAddOp.get("path").set("test-users.properties");
        authAddOp.get("relative-to").set("jboss.domain.config.dir");
        operation.get(STEPS).add(authAddOp);
        return operation;
    }
}
