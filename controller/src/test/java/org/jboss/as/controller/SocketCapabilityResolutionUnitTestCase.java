/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * Test of the specialized capability resolution logic for socket-bindings that
 * is necessary in a managed domain.
 *
 * @author Brian Stansberry
 */
public class SocketCapabilityResolutionUnitTestCase extends AbstractCapabilityResolutionTestCase {

    @Test
    public void testSimpleGlobalRef() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_a"), getCapabilityOperation(GLOBAL_A, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-2", RESULT).asBoolean());
    }

    @Test
    public void testSimpleProfileRef() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_a"), getCapabilityOperation(SUBSYSTEM_A_1, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-2", RESULT).asBoolean());
    }

    /** Like testSimpleProfileRef but the order of ops is switched. Shouldn't make any difference. */
    @Test
    public void testReversedOrderProfileRef() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SUBSYSTEM_A_1, "dep_a", "cap_a"), getCapabilityOperation(SOCKET_A_1, "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-2", RESULT).asBoolean());
    }

    @Test
    public void testMissingGlobalRef() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_b"), getCapabilityOperation(GLOBAL_A, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        validateMissingFailureDesc(response, "step-2", "cap_a", "global");
    }

    @Test
    public void testMissingProfileRef() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_b"), getCapabilityOperation(SUBSYSTEM_A_1, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        validateMissingFailureDesc(response, "step-2", "cap_a", "profile=a");
    }

    @Test
    public void testTwoProfileRefs() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_a"), getCapabilityOperation(SUBSYSTEM_A_1, "dep_a", "cap_a"),
                getCapabilityOperation(SUBSYSTEM_B_1, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-2", RESULT).asBoolean());
        assertTrue(response.toString(), response.get(RESULT, "step-3", RESULT).asBoolean());
    }

    @Test
    public void testProfileTwoRefs() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_a"), getCapabilityOperation(SOCKET_A_2, "cap_b"),
                getCapabilityOperation(SUBSYSTEM_A_1, "dep_a", "cap_a"), getCapabilityOperation(SUBSYSTEM_A_2, "dep_b", "cap_b"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-3", RESULT).asBoolean());
        assertTrue(response.toString(), response.get(RESULT, "step-4", RESULT).asBoolean());
    }

    @Test
    public void testProfilesTwoRefs() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_a"), getCapabilityOperation(SOCKET_A_2, "cap_b"),
                getCapabilityOperation(SUBSYSTEM_A_1, "dep_a", "cap_a"), getCapabilityOperation(SUBSYSTEM_A_2, "dep_b", "cap_b"),
                getCapabilityOperation(SUBSYSTEM_B_1, "dep_a", "cap_a"), getCapabilityOperation(SUBSYSTEM_B_2, "dep_b", "cap_b"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-3", RESULT).asBoolean());
        assertTrue(response.toString(), response.get(RESULT, "step-4", RESULT).asBoolean());
        assertTrue(response.toString(), response.get(RESULT, "step-5", RESULT).asBoolean());
        assertTrue(response.toString(), response.get(RESULT, "step-6", RESULT).asBoolean());
    }

    @Test
    public void testInconsistentProfile() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_a"), getCapabilityOperation(SOCKET_B_1, "cap_b"),
                getCapabilityOperation(SUBSYSTEM_B_1, "dep_a", "cap_a"),
                getCapabilityOperation(SUBSYSTEM_A_1, "dep_a", "cap_a"), getCapabilityOperation(SUBSYSTEM_A_2, "dep_b", "cap_b"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        assertFalse(response.toString(), response.hasDefined(RESULT, "step-3", FAILURE_DESCRIPTION));
        validateInconsistentFailureDesc(response, "step-4", "cap_a", "dep_a", "profile=a");
        validateInconsistentFailureDesc(response, "step-5", "cap_b", "dep_b", "profile=a");
    }

    @Test
    public void testRefInSocketBindingGroup() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_a"), getCapabilityOperation(SOCKET_A_2, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-2", RESULT).asBoolean());
    }

    @Test
    public void testMissingInSocketBindingGroup() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_b"), getCapabilityOperation(SOCKET_A_2, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        validateMissingFailureDesc(response, "step-2", "cap_a", "socket-binding-group=a");
    }

    @Test
    public void testInconsistentInSocketBindingGroup() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_a"), getCapabilityOperation(SOCKET_B_1, "cap_b"),
                getCapabilityOperation(SOCKET_B_2, "dep_b", "cap_b"),
                getCapabilityOperation(SOCKET_A_2, "dep_a", "cap_a"), getCapabilityOperation(SOCKET_A_2, "dep_b", "cap_b"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        assertFalse(response.toString(), response.hasDefined(RESULT, "step-3", FAILURE_DESCRIPTION));
        assertFalse(response.toString(), response.hasDefined(RESULT, "step-4", FAILURE_DESCRIPTION));
        validateMissingFailureDesc(response, "step-5", "cap_b", "socket-binding-group=a");
    }

    @Test
    public void testResolveFromParentGroup() {
        // 'b' includes 'a', 'b' requires from 'a'
        ModelNode op = getCompositeOperation(
                getParentIncludeOperation(SOCKET_B_2.getParent(), "a"),
                getCapabilityOperation(SOCKET_A_1, "cap_a"),
                getCapabilityOperation(SOCKET_B_2, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-3", RESULT).asBoolean());
    }

    @Test
    public void testResolveFromGrandParentGroup() {
        // 'c' includes 'b', 'b' includes 'a', 'c' requires from 'a'
        ModelNode op = getCompositeOperation(
                getParentIncludeOperation(SOCKET_B_2.getParent(), "a"),
                getParentIncludeOperation(SOCKET_C_3.getParent(), "b"),
                getCapabilityOperation(SOCKET_A_1, "cap_a"),
                getCapabilityOperation(SOCKET_C_3, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-4", RESULT).asBoolean());
    }

    @Test
    public void testResolveFromChildGroup() {
        // 'b' includes 'a', 'a' requires from 'b'
        ModelNode op = getCompositeOperation(
                getParentIncludeOperation(SOCKET_B_2.getParent(), "a"),
                getCapabilityOperation(SOCKET_B_2, "cap_a"),
                getCapabilityOperation(SOCKET_A_1, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        validateMissingFailureDesc(response, "step-3", "cap_a", "socket-binding-group=a");
    }

    @Test
    public void testResolveFromGrandChildGroup() {
        // 'c' includes 'b', 'b' includes 'a', 'a' requires from 'c'
        ModelNode op = getCompositeOperation(
                getParentIncludeOperation(SOCKET_B_2.getParent(), "a"),
                getParentIncludeOperation(SOCKET_C_3.getParent(), "b"),
                getCapabilityOperation(SOCKET_C_3, "cap_a"),
                getCapabilityOperation(SOCKET_A_1, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        validateMissingFailureDesc(response, "step-4", "cap_a", "socket-binding-group=a");
    }

    @Test
    public void testIncludesChangeBreaksResolution() {
        // 'b' requires from parent 'a', then 'includes' changes so 'a' is no longer a parent
        ModelNode op = getCompositeOperation(
                getParentIncludeOperation(SOCKET_B_2.getParent(), "a"),
                getCapabilityOperation(SOCKET_A_1, "cap_a"),
                getCapabilityOperation(SOCKET_B_2, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());

        // Drop the include
        response = controller.execute(getParentIncludeOperation(SOCKET_B_2.getParent()), null, null, null);
        assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        validateMissingFailureDesc(response, null, "cap_a", "socket-binding-group=b");
    }

    @Test
    public void testProfileRequiresIncludedSockets() {
        // profile requires 'a' and 'b', while 'b' includes 'a'
        ModelNode op = getCompositeOperation(
                getParentIncludeOperation(SOCKET_B_2.getParent(), "a"),
                getCapabilityOperation(SOCKET_A_1, "cap_a"),
                getCapabilityOperation(SOCKET_B_2, "cap_b"),
                getCapabilityOperation(SUBSYSTEM_A_1, "dep_b", "cap_b"),
                getCapabilityOperation(SUBSYSTEM_A_2, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-4", RESULT).asBoolean());
        assertTrue(response.toString(), response.get(RESULT, "step-5", RESULT).asBoolean());
    }

    @Test
    public void testParentProfileRequiresIncludedSockets() {
        // Parent profile requires 'a', child requires 'b' and 'b' includes 'a'
        ModelNode op = getCompositeOperation(
                getParentIncludeOperation(SUBSYSTEM_B_2.getParent(), "a"),
                getParentIncludeOperation(SOCKET_B_2.getParent(), "a"),
                getCapabilityOperation(SOCKET_A_1, "cap_a"),
                getCapabilityOperation(SOCKET_B_2, "cap_b"),
                getCapabilityOperation(SUBSYSTEM_A_1, "dep_a", "cap_a"),
                getCapabilityOperation(SUBSYSTEM_B_2, "dep_b", "cap_b"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-5", RESULT).asBoolean());
        assertTrue(response.toString(), response.get(RESULT, "step-6", RESULT).asBoolean());
    }

    @Test
    public void testInconsistentParentChildProfile() {
        // Parent profile requires 'a', child requires 'b' but 'b' does not include 'a'
        ModelNode op = getCompositeOperation(
                getParentIncludeOperation(SUBSYSTEM_B_2.getParent(), "a"),
                getCapabilityOperation(SOCKET_A_1, "cap_a"),
                getCapabilityOperation(SOCKET_B_2, "cap_b"),
                getCapabilityOperation(SUBSYSTEM_A_1, "dep_a", "cap_a"),
                getCapabilityOperation(SUBSYSTEM_B_2, "dep_b", "cap_b"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
    }

    @Test
    public void testWFCORE900() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_a"), getCapabilityOperation(GLOBAL_A, null, "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        // NOTE: IT IS FINE TO CHANGE THESE ASSERTIONS IF WFCORE-900 SUPPORT IS RESCINDED!
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-2", RESULT).asBoolean());
    }

    @Test
    public void testWFCORE955() {
        // First, establish a profile to s-b dep. This ensures we get into the consistent scope logic
        // that's where the WFCORE-955 issue appeared
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_a"), getCapabilityOperation(SUBSYSTEM_A_1, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-2", RESULT).asBoolean());

        // Then change the include of a different profile to something bogus
        op = getParentIncludeOperation(SUBSYSTEM_B_1.getParent(), "bogus");
        response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
    }

}
