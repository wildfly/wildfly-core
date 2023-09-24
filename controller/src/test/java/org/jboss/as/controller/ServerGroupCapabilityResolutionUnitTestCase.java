/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * Tests resolution of capabilities related to server groups.
 *
 * @author Brian Stansberry
 */
public class ServerGroupCapabilityResolutionUnitTestCase extends AbstractCapabilityResolutionTestCase {

    private static final PathAddress PROFILE_A_ADDR = PathAddress.pathAddress(PROFILE_A);
    private static final PathAddress SERVER_GROUP_A = PathAddress.pathAddress(SERVER_GROUP, "a");

    @Test
    public void testGoodProfileRef() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(PROFILE_A_ADDR, "cap_a"), getCapabilityOperation(SERVER_GROUP_A, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-2", RESULT).asBoolean());
    }

    /** subsystem=* resources can't provide stuff for server-group=* */
    @Test
    public void testInvalidSubsystemSourceOfProfileRef() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SUBSYSTEM_A_1, "cap_a"), getCapabilityOperation(SERVER_GROUP_A, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        validateMissingFailureDesc(response, "step-2", "cap_a", "server-group");
    }

    /** socket-binding=* resources can't provide stuff for server-group=* */
    @Test
    public void testInvalidSocketBindingSourceOfProfileRef() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_a"), getCapabilityOperation(SERVER_GROUP_A, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        validateMissingFailureDesc(response, "step-2", "cap_a", "server-group");
    }

}
