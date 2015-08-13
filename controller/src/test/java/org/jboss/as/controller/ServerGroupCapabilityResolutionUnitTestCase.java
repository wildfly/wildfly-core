/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
