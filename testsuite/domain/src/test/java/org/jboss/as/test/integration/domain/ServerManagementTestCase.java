/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTO_START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLONE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MASTER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT_OFFSET;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_PORT_OFFSET;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TO_PROFILE;
import static org.jboss.as.server.controller.descriptions.ServerDescriptionConstants.LAUNCH_TYPE;
import static org.jboss.as.server.controller.descriptions.ServerDescriptionConstants.PROCESS_STATE;
import static org.jboss.as.server.controller.descriptions.ServerDescriptionConstants.RUNTIME_CONFIGURATION_STATE;
import static org.jboss.as.test.integration.domain.management.util.DomainTestUtils.checkState;
import static org.jboss.as.test.integration.domain.management.util.DomainTestUtils.getServerConfigAddress;
import static org.jboss.as.test.integration.domain.management.util.DomainTestUtils.startServer;
import static org.jboss.as.test.integration.domain.management.util.DomainTestUtils.waitUntilState;

import java.io.IOException;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.extension.ExtensionSetup;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Emanuel Muckenhuber
 */
//@Ignore("[WFCORE-1958] Clean up testsuite Elytron registration.")
public class ServerManagementTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;

    private static final ModelNode slave = new ModelNode();
    private static final ModelNode mainOne = new ModelNode();
    // (host=slave),(server-config=new-server)
    private static final PathAddress newServerConfigAddress = PathAddress.pathAddress("host", "slave").append("server-config", "new-server");
    // (host=slave),(server=new-server)
    private static final PathAddress newRunningServerAddress = PathAddress.pathAddress("host", "slave").append("server", "new-server");
    private static final String EXCLUDED_FROM_HOST_PROP = "WFCORE-2526.from.host";
    private static final String EXCLUDED_FROM_CONFIG_PROP = "WFCORE-2526.from.config";
    private static final String INCLUDED_FROM_HOST_PROP = "WFCORE-2526.ok.from.host";

    private static final ModelNode existentServer = new ModelNode();
    private static final String MAIN_ONE = "main-one";
    private static final ModelNode nonExistentServer = new ModelNode();
    private static final String NON_EXISTENT = "non-existent";

    static {
        // (host=slave)
        slave.add(HOST, "slave");
        // (host=master),(server-config=main-one)
        mainOne.add(HOST, MASTER);
        mainOne.add(SERVER_CONFIG, MAIN_ONE);

        // (host=master),(server=main-one)
        existentServer.add(HOST, MASTER);
        existentServer.add(SERVER, MAIN_ONE);

        // (host=master),(server=non-existent)
        nonExistentServer.add(HOST, MASTER);
        nonExistentServer.add(SERVER, NON_EXISTENT);
    }

    @BeforeClass
    public static void setupDomain() throws Exception {
        DomainTestSupport.Configuration config =  DomainTestSupport.Configuration.create(ServerManagementTestCase.class.getSimpleName(),
                "domain-configs/domain-minimal.xml", "host-configs/host-master.xml", "host-configs/host-minimal.xml");
        // Props for WFCORE-2526 testing
        config.getSlaveConfiguration().addHostCommandLineProperty("-Djboss.host.server-excluded-properties="+EXCLUDED_FROM_HOST_PROP+"," +EXCLUDED_FROM_CONFIG_PROP);
        config.getSlaveConfiguration().addHostCommandLineProperty("-D" + EXCLUDED_FROM_HOST_PROP + "=host");
        config.getSlaveConfiguration().addHostCommandLineProperty("-D" + INCLUDED_FROM_HOST_PROP + "=ok");
        testSupport = DomainTestSupport.createAndStartSupport(config);

        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
        ExtensionSetup.initialiseProfileIncludesExtension(testSupport);
        final DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();
        DomainTestUtils.executeForResult(Util.createAddOperation(
                PathAddress.pathAddress(EXTENSION, "org.wildfly.extension.profile-includes-test")), masterClient);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        if (domainMasterLifecycleUtil != null) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(
                    PathAddress.pathAddress(EXTENSION, "org.wildfly.extension.profile-includes-test")), domainMasterLifecycleUtil.getDomainClient());
        }

        testSupport.stop();
        testSupport = null;
        domainMasterLifecycleUtil = null;
        domainSlaveLifecycleUtil = null;
    }

    @Test
    public void testCloneProfile() throws Exception {
        final DomainClient client = domainMasterLifecycleUtil.getDomainClient();
        try {
            final ModelNode composite = new ModelNode();
            composite.get(OP).set(COMPOSITE);
            composite.get(OP_ADDR).setEmptyList();

            final ModelNode steps = composite.get(STEPS);

            final ModelNode op = steps.add();
            op.get(OP).set(CLONE);
            op.get(OP_ADDR).add(PROFILE, "default");
            op.get(TO_PROFILE).set("test");

            final ModelNode rr = steps.add();
            rr.get(OP).set(READ_RESOURCE_OPERATION);
            rr.get(OP_ADDR).add("profile", "test");
            rr.get(RECURSIVE).set(true);

            executeForResult(client, composite);

            final ModelNode master = executeForResult(domainMasterLifecycleUtil.getDomainClient(), rr);
            final ModelNode slave = executeForResult(domainSlaveLifecycleUtil.getDomainClient(), rr);
            Assert.assertEquals(master, slave);
        } finally {
            removeProfileAndSubsystems(client, "test");
        }
    }

    @Test
    public void testRemoveStartedServer() throws Exception {
        final DomainClient client = domainMasterLifecycleUtil.getDomainClient();

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).set(mainOne);
        operation.get(NAME).set("status");

        final ModelNode status = DomainTestSupport.validateResponse(client.execute(operation));
        Assert.assertEquals("STARTED", status.asString());

        final ModelNode remove = new ModelNode();
        remove.get(OP).set(REMOVE);
        remove.get(OP_ADDR).set(mainOne);

        final ModelNode result = client.execute(remove);
        // Removing a started server should fail
        Assert.assertEquals(FAILED, result.get(OUTCOME).asString());

    }

    @Test
    public void testAddAndRemoveServer() throws Exception {
        final DomainClient client = domainSlaveLifecycleUtil.getDomainClient();

        Assert.assertFalse(exists(client, newServerConfigAddress));
        Assert.assertFalse(exists(client, newRunningServerAddress));

        final ModelNode addServer = new ModelNode();
        addServer.get(OP).set(ADD);
        addServer.get(OP_ADDR).set(newServerConfigAddress.toModelNode());
        addServer.get(GROUP).set("minimal");
        addServer.get(SOCKET_BINDING_GROUP).set("standard-sockets");
        addServer.get(SOCKET_BINDING_PORT_OFFSET).set(650);
        addServer.get(AUTO_START).set(false);

        ModelNode result = client.execute(addServer);
        client.execute(Util.createAddOperation(newServerConfigAddress.append("jvm", "default")));

        DomainTestSupport.validateResponse(result, false);

        // Stick a WFCORE-2526 test in here since we are starting a server
        // Setup bits ---
        ModelNode sysPropAdd = Util.createAddOperation(newServerConfigAddress.append("system-property", EXCLUDED_FROM_CONFIG_PROP));
        sysPropAdd.get("value").set("config");
        result = client.execute(sysPropAdd);
        DomainTestSupport.validateResponse(result, false);
        // -- end setup bits

        Assert.assertTrue(exists(client, newServerConfigAddress));
        Assert.assertTrue(exists(client, newRunningServerAddress));

        startServer(client, "slave", "new-server");
        Assert.assertTrue(checkState(client, newServerConfigAddress, "STARTED"));

        Assert.assertTrue(exists(client, newServerConfigAddress));
        Assert.assertTrue(exists(client, newRunningServerAddress));

        // Stick a WFCORE-2526 test in here since we have started a server
        // test bits ---
        // The HC VM has IN/EXCLUDED_FROM_HOST_PROP since we passed them in setupDomain but it doesn't have EXCLUDED_FROM_CONFIG_PROP
        // The server sees EXCLUDED_FROM_CONFIG_PROP because it's in its server-config and WFCORE-2526 doesn't override that
        // The server sees INCLUDED_FROM_HOST_PROP because setupDomain didn't configure it as excluded
        ModelNode hostProperties = readSystemProperties(client, newRunningServerAddress.getParent());
        ModelNode serverProperties = readSystemProperties(client, newRunningServerAddress);
        checkProcessSystemProperty(hostProperties, EXCLUDED_FROM_HOST_PROP, "host");
        checkProcessSystemProperty(hostProperties, INCLUDED_FROM_HOST_PROP, "ok");
        checkProcessSystemProperty(hostProperties, EXCLUDED_FROM_CONFIG_PROP, null);
        checkProcessSystemProperty(serverProperties, EXCLUDED_FROM_HOST_PROP, null);
        checkProcessSystemProperty(serverProperties, INCLUDED_FROM_HOST_PROP, "ok");
        checkProcessSystemProperty(serverProperties, EXCLUDED_FROM_CONFIG_PROP, "config");
        // -- end test bits

        final ModelNode stopServer = new ModelNode();
        stopServer.get(OP).set("stop");
        stopServer.get(OP_ADDR).set(newServerConfigAddress.toModelNode());
        stopServer.get("blocking").set(true);
        result = client.execute(stopServer);
        DomainTestSupport.validateResponse(result);
        Assert.assertTrue(checkState(client, newServerConfigAddress, "DISABLED"));

        Assert.assertTrue(exists(client, newServerConfigAddress));
        Assert.assertTrue(exists(client, newRunningServerAddress));

        final ModelNode removeServer = new ModelNode();
        removeServer.get(OP).set(REMOVE);
        removeServer.get(OP_ADDR).set(newServerConfigAddress.toModelNode());

        result = client.execute(removeServer);
        DomainTestSupport.validateResponse(result);

        Assert.assertFalse(exists(client, newServerConfigAddress));
        Assert.assertFalse(exists(client, newRunningServerAddress));
    }

    private ModelNode readSystemProperties(DomainClient client, PathAddress parent) throws IOException {
        PathAddress addr = parent.append("core-service", "platform-mbean").append("type", "runtime");
        ModelNode op = Util.getReadAttributeOperation(addr, "system-properties");
        ModelNode response = client.execute(op);
        ModelNode result = DomainTestSupport.validateResponse(response, true);
        Assert.assertNotNull(result);
        Assert.assertEquals(response.toString(), ModelType.OBJECT, result.getType());
        return result;
    }

    private void checkProcessSystemProperty(ModelNode properties, String prop, String expectedValue) {
        if (expectedValue == null) {
            Assert.assertFalse(prop + " should not be defined", properties.hasDefined(prop));
        } else {
            Assert.assertEquals("Wrong value for " + prop, expectedValue, properties.get(prop).asString());
        }
    }

    @Test
    public void testReloadServer() throws Exception {
        final DomainClient client = domainSlaveLifecycleUtil.getDomainClient();
        final PathAddress address = getServerConfigAddress("slave", "main-three");

        final ModelNode operation = new ModelNode();
        operation.get(OP).set("reload");
        operation.get(OP_ADDR).set(address.toModelNode());
        operation.get(BLOCKING).set(true);

        executeForResult(client, operation);

        Assert.assertTrue(checkState(client, address, "STARTED"));
    }

    @Test
    public void testBZ1015098() throws Exception {

        ModelNode compositeOp = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode steps = compositeOp.get(STEPS);
        steps.add(Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(PROFILE, "BZ1015098"))));
        steps.add(Util.createEmptyOperation(RESTART, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "slave"),
                PathElement.pathElement(SERVER_CONFIG, "other-two")
        )));

        ModelControllerClient client = testSupport.getDomainMasterLifecycleUtil().getDomainClient();
        DomainTestSupport.validateResponse(client.execute(compositeOp));

        final ModelNode read = new ModelNode();
        read.get(OP).set(READ_RESOURCE_OPERATION);
        read.get(OP_ADDR).set(PathAddress.pathAddress(PathElement.pathElement(PROFILE, "BZ1015098")).toModelNode());

        DomainTestSupport.validateResponse(client.execute(read));

    }

    @Test
    public void testPoorHandlingOfInvalidRoles() throws IOException {
        ModelNode op = Util.createOperation(READ_RESOURCE_OPERATION, PathAddress.pathAddress(PathElement.pathElement("host")));
        RbacUtil.addRoleHeader(op, "slave-monitor");
        ModelControllerClient client = testSupport.getDomainMasterLifecycleUtil().getDomainClient();
        ModelNode failureDescription = DomainTestSupport.validateFailedResponse(client.execute(op));
        Assert.assertTrue(failureDescription.asString(), failureDescription.asString().contains("WFLYCTL0327"));
    }

    @Test
    public void testDomainLifecycleMethods() throws Throwable {

        final DomainClient client = domainMasterLifecycleUtil.getDomainClient();
        try {
            executeLifecycleOperation(client, START_SERVERS);
            waitUntilState(client, "master", "main-one", "STARTED");
            waitUntilState(client, "master", "main-two", "STARTED");
            waitUntilState(client, "master", "other-one", "STARTED");
            waitUntilState(client, "slave", "main-three", "STARTED");
            waitUntilState(client, "slave", "main-four", "STARTED");
            waitUntilState(client, "slave", "other-two", "STARTED");

            executeLifecycleOperation(client, STOP_SERVERS);
            //When stopped auto-start=true -> STOPPED, auto-start=false -> DISABLED
            waitUntilState(client, "master", "main-one", "STOPPED");
            waitUntilState(client, "master", "main-two", "DISABLED");
            waitUntilState(client, "master", "other-one", "DISABLED");
            waitUntilState(client, "slave", "main-three", "STOPPED");
            waitUntilState(client, "slave", "main-four", "DISABLED");
            waitUntilState(client, "slave", "other-two", "STOPPED");

            executeLifecycleOperation(client, "other-server-group", START_SERVERS);
            //Check the affected servers have been started
            waitUntilState(client, "master", "other-one", "STARTED");
            waitUntilState(client, "slave", "other-two", "STARTED");
            //And that the remaining ones are still stopped
            waitUntilState(client, "master", "main-one", "STOPPED");
            waitUntilState(client, "master", "main-two", "DISABLED");
            waitUntilState(client, "slave", "main-three", "STOPPED");
            waitUntilState(client, "slave", "main-four", "DISABLED");

            executeLifecycleOperation(client, "other-server-group", RESTART_SERVERS);
            //Check the affected servers have been started
            waitUntilState(client, "master", "other-one", "STARTED");
            waitUntilState(client, "slave", "other-two", "STARTED");
            //And that the remaining ones are still stopped
            waitUntilState(client, "master", "main-one", "STOPPED");
            waitUntilState(client, "master", "main-two", "DISABLED");
            waitUntilState(client, "slave", "main-three", "STOPPED");
            waitUntilState(client, "slave", "main-four", "DISABLED");

            executeLifecycleOperation(client, "other-server-group", RESTART_SERVERS);
            //Check the affected servers have been started
            waitUntilState(client, "master", "other-one", "STARTED");
            waitUntilState(client, "slave", "other-two", "STARTED");
            //And that the remaining ones are still stopped
            waitUntilState(client, "master", "main-one", "STOPPED");
            waitUntilState(client, "master", "main-two", "DISABLED");
            waitUntilState(client, "slave", "main-three", "STOPPED");
            waitUntilState(client, "slave", "main-four", "DISABLED");

            executeLifecycleOperation(client, "other-server-group", STOP_SERVERS);
            //When stopped auto-start=true -> STOPPED, auto-start=false -> DISABLED
            waitUntilState(client, "master", "main-one", "STOPPED");
            waitUntilState(client, "master", "main-two", "DISABLED");
            waitUntilState(client, "master", "other-one", "DISABLED");
            waitUntilState(client, "slave", "main-three", "STOPPED");
            waitUntilState(client, "slave", "main-four", "DISABLED");
            waitUntilState(client, "slave", "other-two", "STOPPED");
        } finally {
            //Set everything back to how it was:
            try {
                resetServerToExpectedState(client, "master", "main-one", "STARTED");
                resetServerToExpectedState(client, "master", "main-two", "DISABLED");
                resetServerToExpectedState(client, "master", "other-one", "DISABLED");
                resetServerToExpectedState(client, "slave", "main-three", "STARTED");
                resetServerToExpectedState(client, "slave", "main-four", "DISABLED");
                resetServerToExpectedState(client, "slave", "other-two", "STARTED");
                waitUntilState(client, "master", "main-one", "STARTED");
                waitUntilState(client, "slave", "main-three", "STARTED");
                waitUntilState(client, "slave", "other-two", "STARTED");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    @Test
    public void testIncludes() throws Exception {
        final DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();

        //The 'one' and 'two' subsystems used in this test come from the profile-includes-test extension added by the
        //setup
        PathAddress rootProfileAddr = PathAddress.pathAddress(PROFILE, "root");
        DomainTestUtils.executeForResult(
                Util.createAddOperation(rootProfileAddr), masterClient);
        try {
            PathAddress child1ProfileAddr = PathAddress.pathAddress(PROFILE, "child1");
            ModelNode child1Add = Util.createAddOperation(child1ProfileAddr);
            child1Add.get(INCLUDES).add("root");
            DomainTestUtils.executeForResult(child1Add, masterClient);
            try {
                //A clone _with_ includes (unlike testCloneProfile())
                ModelNode clone = Util.createEmptyOperation(CLONE, child1ProfileAddr);
                clone.get(TO_PROFILE).set("child2");
                DomainTestUtils.executeForResult(clone, masterClient);
                try {
                    ModelNode includes = DomainTestUtils.executeForResult(
                            Util.createOperation(READ_RESOURCE_OPERATION, child1ProfileAddr), masterClient)
                            .get(INCLUDES);
                    Assert.assertTrue(includes.isDefined());
                    Assert.assertEquals(ModelType.LIST, includes.getType());
                    Assert.assertEquals(1, includes.asList().size());
                    Assert.assertEquals("root", includes.get(0).asString());
                } finally {
                    removeProfileAndSubsystems(masterClient, "child2");
                }

                //Now let's try to add some subsystems to check that overriding does not work
                //We only do adds here, changing the includes attribute is handled by ProfileIncludesHandlerTestCase
                DomainTestUtils.executeForResult(
                        Util.createAddOperation(child1ProfileAddr.append(SUBSYSTEM, "one")),
                        masterClient);
                DomainTestUtils.executeForFailure(
                        Util.createAddOperation(rootProfileAddr.append(SUBSYSTEM, "one")),
                        masterClient);
                DomainTestUtils.executeForResult(
                        Util.createAddOperation(rootProfileAddr.append(SUBSYSTEM, "two")),
                        masterClient);
                DomainTestUtils.executeForFailure(
                        Util.createAddOperation(child1ProfileAddr.append(SUBSYSTEM, "two")),
                        masterClient);

                //Check that both the master and slave have the same profiles
                ModelNode masterProfiles =
                        getProfiles(readResource(masterClient, PathAddress.EMPTY_ADDRESS), "root", "child1");
                ModelNode slaveProfiles = getProfiles(
                        readResource(domainSlaveLifecycleUtil.getDomainClient(), PathAddress.EMPTY_ADDRESS), "root", "child1");
                Assert.assertEquals(masterProfiles, slaveProfiles);
                Assert.assertTrue(masterProfiles.get("root", SUBSYSTEM).isDefined());
                Set<String> masterSubsystems = masterProfiles.get("root", SUBSYSTEM).keys();
                Assert.assertEquals(1, masterSubsystems.size());
                Assert.assertEquals("two", masterSubsystems.iterator().next());
                Assert.assertTrue(masterProfiles.get("child1", SUBSYSTEM).isDefined());
                Set<String> slaveSubsystems = slaveProfiles.get("child1", SUBSYSTEM).keys();
                Assert.assertEquals(1, slaveSubsystems.size());
                Assert.assertEquals("one", slaveSubsystems.iterator().next());

                //Now add a server group
                final PathAddress groupAddr = PathAddress.pathAddress(SERVER_GROUP, "test-group");
                ModelNode group = Util.createAddOperation(groupAddr);
                group.get(PROFILE).set("child1");
                group.get(SOCKET_BINDING_GROUP).set("standard-sockets");
                DomainTestUtils.executeForResult(group, masterClient);
                try {
                    //Add a server and make sure it has all the subsystems
                    PathAddress serverConfAddr =
                            PathAddress.pathAddress(HOST, "slave").append(SERVER_CONFIG, "includes-server");
                    ModelNode add = Util.createAddOperation(serverConfAddr);
                    add.get(GROUP).set("test-group");
                    add.get(PORT_OFFSET).set("550");
                    DomainTestUtils.executeForResult(add, masterClient);
                    DomainTestUtils.executeForResult(Util.createAddOperation(serverConfAddr.append("jvm","default")), masterClient);

                    try {
                        ModelNode start = Util.createEmptyOperation(START, serverConfAddr);
                        start.get(BLOCKING).set(true);
                        DomainTestUtils.executeForResult(start, masterClient);
                        try {
                            PathAddress serverAddr =
                                    PathAddress.pathAddress(HOST, "slave").append(SERVER, "includes-server");
                            //Check we have the same subsystems
                            ModelNode model = readResource(masterClient, serverAddr);
                            Set<String> subsystems = model.get(SUBSYSTEM).keys();
                            Assert.assertEquals(2, subsystems.size());
                            Assert.assertTrue(subsystems.contains("one"));
                            Assert.assertTrue(subsystems.contains("two"));

                            //Add a subsystem to the root (included) profile and make sure it gets propagated to the server
                            DomainTestUtils.executeForResult(
                                    Util.createAddOperation(rootProfileAddr.append(SUBSYSTEM, "three")),
                                    masterClient);
                            model = readResource(masterClient, serverAddr);
                            subsystems = model.get(SUBSYSTEM).keys();
                            Assert.assertEquals(3, subsystems.size());
                            Assert.assertTrue(subsystems.contains("one"));
                            Assert.assertTrue(subsystems.contains("two"));
                            Assert.assertTrue(subsystems.contains("three"));

                            //Remove a subsystem from the root (included) profile and make sure it gets propagated to the server
                            DomainTestUtils.executeForResult(
                                    Util.createRemoveOperation(rootProfileAddr.append(SUBSYSTEM, "two")),
                                    masterClient);
                            model = readResource(masterClient, serverAddr);
                            subsystems = model.get(SUBSYSTEM).keys();
                            Assert.assertEquals(2, subsystems.size());
                            Assert.assertTrue(subsystems.contains("one"));
                            Assert.assertTrue(subsystems.contains("three"));
                        } finally {
                            ModelNode stop = Util.createEmptyOperation(STOP, serverConfAddr);
                            stop.get(BLOCKING).set(true);
                            DomainTestUtils.executeForResult(stop, masterClient);
                        }
                    } finally {
                        DomainTestUtils.executeForResult(Util.createRemoveOperation(serverConfAddr), masterClient);
                    }
                } finally {
                    DomainTestUtils.executeForResult(Util.createRemoveOperation(groupAddr), masterClient);
                }
            } finally {
                removeProfileAndSubsystems(masterClient, "child1");
            }
        } finally {
            removeProfileAndSubsystems(masterClient, "root");
        }
    }

    @Test
    public void testSocketBindingGroupIncludes() throws Exception {
        final DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();
        final PathAddress root1 = PathAddress.pathAddress(SOCKET_BINDING_GROUP, "root1");
        DomainTestUtils.executeForResult(createSocketBindingGroupAddOperation(root1), masterClient);
        try {
            final PathAddress root2 = PathAddress.pathAddress(SOCKET_BINDING_GROUP, "root2");
            DomainTestUtils.executeForResult(createSocketBindingGroupAddOperation(root2), masterClient);
            try {
                final PathAddress child = PathAddress.pathAddress(SOCKET_BINDING_GROUP, "child");
                ModelNode childAdd = createSocketBindingGroupAddOperation(child, "root1");
                DomainTestUtils.executeForResult(childAdd, masterClient);
                try {
                    DomainTestUtils.executeForResult(createSocketBindingAddOperation(root1, "rootA", 123), masterClient);
                    DomainTestUtils.executeForResult(createSocketBindingAddOperation(root2, "rootB", 234), masterClient);
                    DomainTestUtils.executeForResult(createSocketBindingAddOperation(child, "child", 456), masterClient);

                    final PathAddress groupAddr = PathAddress.pathAddress(SERVER_GROUP, "test-group");
                    ModelNode group = Util.createAddOperation(groupAddr);
                    group.get(PROFILE).set("default");
                    group.get(SOCKET_BINDING_GROUP).set("child");
                    DomainTestUtils.executeForResult(group, masterClient);
                    try {
                        final PathAddress serverConfAddr =
                                PathAddress.pathAddress(HOST, "slave").append(SERVER_CONFIG, "includes-server");
                        ModelNode add = Util.createAddOperation(serverConfAddr);
                        add.get(GROUP).set("test-group");
                        add.get(PORT_OFFSET).set("550");
                        DomainTestUtils.executeForResult(add, masterClient);
                        DomainTestUtils.executeForResult(Util.createAddOperation(serverConfAddr.append("jvm","default")), masterClient);
                        try {
                            final ModelNode start = Util.createEmptyOperation(START, serverConfAddr);
                            start.get(BLOCKING).set(true);
                            DomainTestUtils.executeForResult(start, masterClient);
                            try {
                                final PathAddress serverAddr =
                                        PathAddress.pathAddress(HOST, "slave").append(SERVER, "includes-server");

                                ModelNode model = readResource(masterClient, serverAddr.append(SOCKET_BINDING_GROUP, "child"));
                                Assert.assertEquals(2, model.get(SOCKET_BINDING).keys().size());
                                Assert.assertEquals(123, model.get(SOCKET_BINDING, "rootA", PORT).asInt());
                                Assert.assertEquals(456, model.get(SOCKET_BINDING, "child", PORT).asInt());

                                DomainTestUtils.executeForResult(createSocketBindingAddOperation(root1, "rootA1", 567), masterClient);
                                model = readResource(masterClient, serverAddr.append(SOCKET_BINDING_GROUP, "child"));
                                Assert.assertEquals(3, model.get(SOCKET_BINDING).keys().size());
                                Assert.assertEquals(123, model.get(SOCKET_BINDING, "rootA", PORT).asInt());
                                Assert.assertEquals(456, model.get(SOCKET_BINDING, "child", PORT).asInt());
                                Assert.assertEquals(567, model.get(SOCKET_BINDING, "rootA1", PORT).asInt());

                                DomainTestUtils.executeForResult(createSocketBindingAddOperation(child, "child1", 678), masterClient);
                                model = readResource(masterClient, serverAddr.append(SOCKET_BINDING_GROUP, "child"));
                                Assert.assertEquals(4, model.get(SOCKET_BINDING).keys().size());
                                Assert.assertEquals(123, model.get(SOCKET_BINDING, "rootA", PORT).asInt());
                                Assert.assertEquals(456, model.get(SOCKET_BINDING, "child", PORT).asInt());
                                Assert.assertEquals(567, model.get(SOCKET_BINDING, "rootA1", PORT).asInt());
                                Assert.assertEquals(678, model.get(SOCKET_BINDING, "child1", PORT).asInt());

                                DomainTestUtils.executeForResult(
                                        Util.getWriteAttributeOperation(child.append(SOCKET_BINDING, "child1"), PORT, 6789)
                                        , masterClient);
                                model = readResource(masterClient, serverAddr.append(SOCKET_BINDING_GROUP, "child"));
                                Assert.assertEquals(4, model.get(SOCKET_BINDING).keys().size());
                                Assert.assertEquals(123, model.get(SOCKET_BINDING, "rootA", PORT).asInt());
                                Assert.assertEquals(456, model.get(SOCKET_BINDING, "child", PORT).asInt());
                                Assert.assertEquals(567, model.get(SOCKET_BINDING, "rootA1", PORT).asInt());
                                Assert.assertEquals(6789, model.get(SOCKET_BINDING, "child1", PORT).asInt());

                                DomainTestUtils.executeForResult(
                                        Util.createRemoveOperation(child.append(SOCKET_BINDING, "child1")) , masterClient);
                                model = readResource(masterClient, serverAddr.append(SOCKET_BINDING_GROUP, "child"));
                                Assert.assertEquals(3, model.get(SOCKET_BINDING).keys().size());
                                Assert.assertEquals(123, model.get(SOCKET_BINDING, "rootA", PORT).asInt());
                                Assert.assertEquals(456, model.get(SOCKET_BINDING, "child", PORT).asInt());
                                Assert.assertEquals(567, model.get(SOCKET_BINDING, "rootA1", PORT).asInt());
                            } finally {
                                final ModelNode stop = Util.createEmptyOperation(STOP, serverConfAddr);
                                stop.get(BLOCKING).set(true);
                                DomainTestUtils.executeForResult(stop, masterClient);
                            }
                        } finally {
                            DomainTestUtils.executeForResult(Util.createRemoveOperation(serverConfAddr), masterClient);
                        }

                    } finally {
                        DomainTestUtils.executeForResult(Util.createRemoveOperation(groupAddr), masterClient);
                    }
                } finally {
                    DomainTestUtils.executeForResult(Util.createRemoveOperation(child), masterClient);
                }
            } finally {
                DomainTestUtils.executeForResult(Util.createRemoveOperation(root2), masterClient);
            }
        } finally {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(root1), masterClient);
        }
    }

    @Test
    public void testServerBootState() throws IOException {
        final DomainClient client = domainMasterLifecycleUtil.getDomainClient();
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        // (host=master),(server=main-one)
        operation.get(OP_ADDR).set(existentServer);

        operation.get(NAME).set(LAUNCH_TYPE);
        ModelNode status = DomainTestSupport.validateResponse(client.execute(operation));
        Assert.assertEquals("DOMAIN", status.asString());
        operation.get(NAME).set(PROCESS_STATE);
        status = DomainTestSupport.validateResponse(client.execute(operation));
        Assert.assertEquals("running", status.asString());
        operation.get(NAME).set(RUNTIME_CONFIGURATION_STATE);
        status = DomainTestSupport.validateResponse(client.execute(operation));
        Assert.assertEquals("ok", status.asString());

        // (host=master),(server=non-existent)
        operation.get(OP_ADDR).set(nonExistentServer);

        operation.get(NAME).set(LAUNCH_TYPE);
        ModelNode result = client.execute(operation);
        Assert.assertEquals(result.get(FAILURE_DESCRIPTION).asString(), FAILED, result.get(OUTCOME).asString());
        operation.get(NAME).set(PROCESS_STATE);
        result = client.execute(operation);
        Assert.assertEquals(result.get(FAILURE_DESCRIPTION).asString(), FAILED, result.get(OUTCOME).asString());
        operation.get(NAME).set(RUNTIME_CONFIGURATION_STATE);
        result = client.execute(operation);
        Assert.assertEquals(result.get(FAILURE_DESCRIPTION).asString(), FAILED, result.get(OUTCOME).asString());
    }

    private void removeProfileAndSubsystems(final DomainClient client, String profileName) throws Exception {
        PathAddress profileAddr = PathAddress.pathAddress(PROFILE, profileName);
        ModelNode op = Util.createEmptyOperation(READ_CHILDREN_NAMES_OPERATION,
                profileAddr);
        op.get(CHILD_TYPE).set(SUBSYSTEM);
        ModelNode subsystems = DomainTestUtils.executeForResult(op, client);
        for (ModelNode subsystem : subsystems.asList()) {
            executeForResult(client, Util.createRemoveOperation(
                    profileAddr.append(SUBSYSTEM, subsystem.asString())));
        }
        executeForResult(client, Util.createRemoveOperation(profileAddr));
    }

    private void executeLifecycleOperation(final ModelControllerClient client, String opName) throws IOException {
        executeLifecycleOperation(client, null, opName);
    }

    private void executeLifecycleOperation(final ModelControllerClient client, String groupName, String opName) throws IOException {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(opName);
        if (groupName == null) {
            operation.get(OP_ADDR).setEmptyList();
        } else {
            operation.get(OP_ADDR).add(SERVER_GROUP, groupName);
        }
        DomainTestSupport.validateResponse(client.execute(operation));
    }

    private ModelNode executeForResult(final ModelControllerClient client, final ModelNode operation) throws IOException {
        final ModelNode result = client.execute(operation);
        return DomainTestSupport.validateResponse(result);
    }

    private boolean exists(final ModelControllerClient client, final PathAddress address) throws IOException {

        final ModelNode childrenNamesOp = new ModelNode();
        childrenNamesOp.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        childrenNamesOp.get(OP_ADDR).set(address.getParent().toModelNode());
        childrenNamesOp.get(CHILD_TYPE).set(address.getLastElement().getKey());

        final ModelNode result = executeForResult(client, childrenNamesOp);
        return result.asList().contains(new ModelNode(address.getLastElement().getValue()));
    }

    private void resetServerToExpectedState(final ModelControllerClient client, final String hostName, final String serverName, final String state) throws IOException {
        final PathAddress serverConfigAddress = PathAddress.pathAddress(HOST, hostName).append(SERVER_CONFIG, serverName);
        if (!checkState(client, serverConfigAddress, state)) {
            final ModelNode operation = new ModelNode();
            operation.get(OP_ADDR).set(serverConfigAddress.toModelNode());
            if (state.equals("STARTED")) {
                //start server
                operation.get(OP).set(START);
            } else if (state.equals("STOPPED") || state.equals("DISABLED")) {
                //stop server
                operation.get(OP).set(STOP);
            }
            client.execute(operation);
        }
    }

    private ModelNode readResource(DomainClient client, PathAddress addr) throws Exception {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, addr);
        op.get(RECURSIVE).set(true);
        return DomainTestUtils.executeForResult(op, client);
    }

    private ModelNode getProfiles(ModelNode model, String...profiles) {
        ModelNode result = new ModelNode();
        for (String name : profiles) {
            Assert.assertTrue(model.hasDefined(PROFILE, name));
            ModelNode profile = model.get(PROFILE, name);
            result.get(name).set(profile);
        }
        return result;
    }

    private ModelNode createSocketBindingGroupAddOperation(PathAddress groupAddr, String...includes) {
        ModelNode add = Util.createAddOperation(groupAddr);
        add.get(DEFAULT_INTERFACE).set("public");
        for (String include : includes) {
            add.get(INCLUDES).add(include);
        }
        return add;
    }

    private ModelNode createSocketBindingAddOperation(PathAddress groupAddr, String name, int port) {
        ModelNode add = Util.createAddOperation(groupAddr.append(SOCKET_BINDING, name));
        add.get(PORT).set(port);
        return add;
    }

}
