/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INET_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOOPBACK;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_REQUIRES_RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_REF;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOURCE_PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.extension.ExtensionUtils;
import org.jboss.as.test.integration.management.extension.dependent.DependentExtension;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test of WFCORE-1106 behavior.
 *
 * @author Brian Stansberry
 */
public class CapabilityReloadRequiredUnitTestCase {

    private static final String WFCORE1106 = "WFCORE-1106";

    private static final String WFCORE1106_OSB = WFCORE1106 + "-osb";

    private static final String WFCORE1106_OSB2 = WFCORE1106_OSB + "-2";
    private static final PathAddress EXT = PathAddress.pathAddress(EXTENSION, WFCORE1106);
    private static final PathAddress IFACE = PathAddress.pathAddress(INTERFACE, WFCORE1106);
    private static final PathAddress SB = PathAddress.pathAddress(SOCKET_BINDING_GROUP, "other-sockets").append(SOCKET_BINDING, WFCORE1106);
    private static final PathAddress OSB = PathAddress.pathAddress(SOCKET_BINDING_GROUP, "other-sockets").append(LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING, WFCORE1106_OSB);
    private static final PathAddress OSB_2 = PathAddress.pathAddress(SOCKET_BINDING_GROUP, "other-sockets").append(LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING, WFCORE1106_OSB2);
    private static final PathAddress SUB = PathAddress.pathAddress(PROFILE, "other").append(SUBSYSTEM, DependentExtension.SUBSYSTEM_NAME);

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        ExtensionUtils.createExtensionModule(WFCORE1106, DependentExtension.class);
        testSupport = DomainTestSuite.createSupport(CapabilityReloadRequiredUnitTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        try {
            testSupport = null;
            domainPrimaryLifecycleUtil = null;
            DomainTestSuite.stopSupport();
        } finally {
            ExtensionUtils.deleteExtensionModule(WFCORE1106);
        }
    }

    @Before
    public void setup() throws IOException {

        ModelNode addOp = Util.createAddOperation(IFACE);
        addOp.get(LOOPBACK).set(true);
        executeOp(addOp);

        addOp = Util.createAddOperation(SB);
        addOp.get(INTERFACE).set(WFCORE1106);
        addOp.get(PORT).set(12345);
        executeOp(addOp);

        addOp = Util.createAddOperation(OSB);
        addOp.get(SOCKET_BINDING_REF).set(WFCORE1106);
        executeOp(addOp);

        addOp = Util.createAddOperation(OSB_2);
        addOp.get(SOCKET_BINDING_REF).set(WFCORE1106);
        executeOp(addOp);

        addOp = Util.createAddOperation(EXT);
        executeOp(addOp);
    }

    @After
    public void tearDown() throws IOException {

        IOException ioe = null;
        AssertionError ae = null;
        RuntimeException re = null;

        PathAddress[] cleanUp = {SUB, EXT, OSB, OSB_2, SB};
        for (int i = 0; i < cleanUp.length; i++) {
            try {
                executeOp(Util.createRemoveOperation(cleanUp[i]));
            } catch (IOException e) {
                if (ioe == null) {
                    ioe = e;
                }
            } catch (AssertionError e) {
                if (i > 0 && i < cleanUp.length - 1 && ae == null) {
                    ae = e;
                } // else ignore because in a failed test SUB may not exist, causing remove to fail
                  // and because removing the interface will definitely fail
            } catch (RuntimeException e) {
                if (re == null) {
                    re = e;
                }
            }
        }

        try {
            ModelNode op = Util.createEmptyOperation(RELOAD_SERVERS, PathAddress.pathAddress(SERVER_GROUP, "other-server-group"));
            op.get(BLOCKING).set(true);
            executeOp(op);
        } catch (IOException e) {
            if (ioe == null) {
                ioe = e;
            }
        } catch (AssertionError e) {
            if (ae == null) {
                ae = e;
            }
        } catch (RuntimeException e) {
            if (re == null) {
                re = e;
            }
        }

        try {
            executeOp(Util.createRemoveOperation(IFACE));
        } catch (IOException e) {
            if (ioe == null) {
                ioe = e;
            }
        } catch (AssertionError e) {
            if (ae == null) {
                ae = e;
            }
        } catch (RuntimeException e) {
            if (re == null) {
                re = e;
            }
        }

        if (ioe != null) {
            throw ioe;
        }

        if (ae != null) {
            throw ae;
        }

        if (re != null) {
            throw re;
        }
    }

    @Test
    public void testCapabilityReloadEffects() throws IOException {
        // Change the interface to put its capability into r-r
        ModelNode op = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode steps = op.get(STEPS);
        steps.add(Util.getUndefineAttributeOperation(IFACE, LOOPBACK));
        steps.add(Util.getWriteAttributeOperation(IFACE, INET_ADDRESS, "${jboss.bind.address:127.0.0.1}"));
        assertStepRequiresReload(op, "main-server-group", "other-server-group");

        // Add the loc -- should get a restart-required header
        op = Util.createAddOperation(SUB);
        op.get("osb").set(WFCORE1106_OSB);
        assertStepRequiresReload(op);

        // reload
        op = Util.createEmptyOperation(RELOAD_SERVERS, PathAddress.pathAddress(SERVER_GROUP, "other-server-group"));
        op.get(BLOCKING).set(true);
        executeOp(op);

        // Change the l-o-c -- should *not* get a restart-required header
        op = Util.getWriteAttributeOperation(SUB, "osb", WFCORE1106_OSB2);

        assertStepDoesNotRequireReload(op);

        // Change the o-s-b to put its capability into r-r

        op = Util.getWriteAttributeOperation(OSB, SOURCE_PORT, 54321);
        assertStepRequiresReload(op);

        // Change the l-o-c -- should get a restart-required header
        op = Util.getWriteAttributeOperation(SUB, "osb", WFCORE1106_OSB);
        assertStepRequiresReload(op);

        // Remove the subsystem and osb
        op = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        steps = op.get(STEPS);
        steps.add(Util.createRemoveOperation(SUB));
        steps.add(Util.createRemoveOperation(OSB));
        assertStepRequiresReload(op);

        // Re-add  -- should still get a restart-required header on SUB
        // because removing the o-s-b capability doesn't flush the need to reload
        // We also get a r-r header on the o-s-b add
        op = Util.createAddOperation(OSB);
        op.get(SOCKET_BINDING_REF).set(WFCORE1106);
        assertStepRequiresReload(op);
        op = Util.createAddOperation(SUB);
        op.get("osb").set(WFCORE1106_OSB);
        assertStepRequiresReload(op);

    }

    private void assertStepRequiresReload(ModelNode op) throws IOException {
        assertRequiresReload(op, true, "other-server-group");
    }

    private void assertStepRequiresReload(ModelNode op, String... serverGroups) throws IOException {
        assertRequiresReload(op, true, serverGroups);
    }

    private void assertStepDoesNotRequireReload(ModelNode op) throws IOException {
        assertRequiresReload(op, false, "other-server-group");
    }

    private void assertRequiresReload(ModelNode op, boolean expectRequires, String... serverGroups) throws IOException {
        ModelNode origResp = executeOp(op);
        for (String sg : serverGroups) {
            for (Property prop : origResp.get(SERVER_GROUPS, sg, HOST).asPropertyList()) {
                String host = prop.getName();
                for (Property prop2 : prop.getValue().asPropertyList()) {
                    String server = prop2.getName();
                    ModelNode response = prop2.getValue().get(RESPONSE);
                    // Hack for composites
                    if (COMPOSITE.equals(op.get(OP).asString())) {
                        response = response.get(RESULT, "step-1");
                    }
                    String target = " " + sg + "/" + host + "/" + server;
                    if (expectRequires) {
                        assertTrue(origResp.toString() + target, response.hasDefined(RESPONSE_HEADERS, OPERATION_REQUIRES_RELOAD));
                        assertTrue(origResp.toString() + target, response.get(RESPONSE_HEADERS, OPERATION_REQUIRES_RELOAD).asBoolean());
                    } else {
                        assertFalse(origResp.toString() + target, response.hasDefined(RESPONSE_HEADERS, OPERATION_REQUIRES_RELOAD));
                    }
                }
            }
        }
    }

    private ModelNode executeOp(ModelNode op) throws IOException {
        ModelNode response = domainPrimaryLifecycleUtil.getDomainClient().execute(op);
        assertTrue(response.toString(), response.hasDefined(OUTCOME));
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        return response;
    }
}
