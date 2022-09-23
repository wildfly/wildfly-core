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

package org.jboss.as.domain.management.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACTIVE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_ROLLOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXCLUSIVE_RUNNING_TIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXECUTION_STATUS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_TIME;
import static org.jboss.as.domain.management.controller.FindNonProgressingOperationHandler.findNonProgressingOp;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * Unit tests of {@link org.jboss.as.domain.management.controller.FindNonProgressingOperationHandler}.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public class FindNonProgressingOpUnitTestCase {


    @Test
    public void testBasic() throws OperationFailedException {
        Resource res = assemble(getSecondaryResource());
        assertEquals("0", findNonProgressingOp(res, false, 50));
        assertEquals("0", findNonProgressingOp(res, true, 50));
    }


    @Test
    public void testServer() throws OperationFailedException {
        // The domain getDelayedServerResource stuff is irrelevant to a server
        Resource res = assemble(getSecondaryResource(), getDelayedServerResource());
        assertEquals("0", findNonProgressingOp(res, true, 50));
    }

    @Test
    public void testOriginatingNode() throws OperationFailedException {
        // The server op is ignored when the local node is the origin
        Resource res = assemble(getMasterResource(), getDelayedServerResource());
        assertEquals("0", findNonProgressingOp(res, false, 50));
    }

    @Test
    public void testSingleDelayedServer() throws OperationFailedException {
        // WFCORE-263 case
        Resource res = assemble(getSecondaryResource(), getDelayedServerResource());
        assertEquals("1", findNonProgressingOp(res, false, 50));
    }

    @Test
    public void testMultipleDelayedServers() {
        // WFCORE-263 case
        Resource res = assemble(getSecondaryResource(), getDelayedServerResource(), getDelayedServerResource());
        try {
            findNonProgressingOp(res, false, 50);
            fail("multiple servers should fail");
        } catch (OperationFailedException good) {
            OperationFailedException expected = DomainManagementLogger.ROOT_LOGGER.domainRolloutNotProgressing("0", 50, "uuid", Arrays.asList("1", "2"));
            assertTrue(good.getLocalizedMessage().contains(expected.getLocalizedMessage()));
        }
    }

    @Test
    public void testHealthyServer() throws OperationFailedException {
        // Healthy server op does not overrule the secondary op
        Resource res = assemble(getSecondaryResource(), getHealthyServerResource());
        assertEquals("0", findNonProgressingOp(res, false, 50));
    }

    private static Resource assemble(Resource... ops) {
        Resource result = Resource.Factory.create(true);
        for (int i = 0; i < ops.length; i++) {
            result.registerChild(PathElement.pathElement(ACTIVE_OPERATION, String.valueOf(i)), ops[i]);
        }
        return result;

    }

    private static Resource getMasterResource() {
        return getHCResource(false);
    }

    private static Resource getSecondaryResource() {
        return getHCResource(true);
    }

    private static Resource getHCResource(boolean domainRollout) {
        Resource host = Resource.Factory.create(true);
        ModelNode model = new ModelNode();
        model.get(EXCLUSIVE_RUNNING_TIME).set(100);
        model.get(DOMAIN_ROLLOUT).set(domainRollout);
        model.get(EXECUTION_STATUS).set(OperationContext.ExecutionStatus.COMPLETING.toString());
        model.get(DOMAIN_UUID).set("uuid");
        host.writeModel(model);
        return host;
    }

    private static Resource getDelayedServerResource() {
        return getServerResource(100);
    }

    private static Resource getHealthyServerResource() {
        return getServerResource(1);
    }

    private static Resource getServerResource(long runningTime) {
        Resource server = Resource.Factory.create(true);
        ModelNode model = new ModelNode();
        model.get(RUNNING_TIME).set(runningTime);
        model.get(DOMAIN_ROLLOUT).set(true);
        model.get(EXECUTION_STATUS).set(OperationContext.ExecutionStatus.EXECUTING.toString());
        model.get(DOMAIN_UUID).set("uuid");
        server.writeModel(model);
        return server;
    }
}
