/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * Test of the specialized capability resolution logic for socket-bindings that
 * is necessary in a managed domain.
 *
 * @author Tomaz Cerar
 */
public class CapabilityResolutionErrorTestCase extends AbstractCapabilityResolutionTestCase {

    @Test
    public void testMissingGlobalRef() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_b"), getCapabilityOperation(GLOBAL_A, "dep_a", "subsystem.only-registered"));
        ModelNode response = controller.execute(op, null, null, null);
        validateMissingFailureDesc(response, "step-2", "cap_a", "global","/subsystem=only-registered");
    }

    @Test
    public void testMissingGlobalSimpleRef() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_b"), getCapabilityOperation(GLOBAL_A, "dep_a", "socket-binding-group.a.subsystem.only-registered"));
        ModelNode response = controller.execute(op, null, null, null);
        validateMissingFailureDesc(response, "step-2", "cap_a", "global", "/socket-binding-group=a");
        op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_b"), getCapabilityOperation(GLOBAL_A, "dep_b", "socket-binding-group.x.subsystem.only-registered"));
        response = controller.execute(op, null, null, null);
        validateMissingFailureDesc(response, "step-2", "cap_a", "global", null);
    }

    @Test
    public void testMissingProfileRef() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_b"), getCapabilityOperation(SUBSYSTEM_A_1, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        validateMissingFailureDesc(response, "step-2", "cap_a", "profile=a", null);
    }

    protected static void validateMissingFailureDesc(ModelNode response, String step, String cap, String context, String possibleRegistrationPoint) {
        assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.hasDefined(FAILURE_DESCRIPTION));
        String failDesc = response.get(FAILURE_DESCRIPTION).asString();
        int loc = -1;
        if (step != null) {
            loc = failDesc.indexOf(step);
            assertTrue(response.toString(), loc > 0);
        }
        int lastLoc = loc;
        loc = failDesc.indexOf("WFLYCTL0369");
        assertTrue(response.toString(), loc > lastLoc);
        if (possibleRegistrationPoint != null) {
            assertTrue(failDesc, failDesc.contains("Possible registration points for this"));
            assertTrue(failDesc, failDesc.contains(possibleRegistrationPoint));
        } else {
            assertTrue(failDesc, failDesc.contains("There are no known registration points"));
        }

    }


}
