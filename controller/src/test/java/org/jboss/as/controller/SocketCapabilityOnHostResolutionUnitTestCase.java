/*
 * JBoss, Home of Professional Open Source.
 * Copyright ${year}, Red Hat, Inc., and individual contributors
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.persistence.NullConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test of the specialized capability resolution logic for socket-bindings that
 * is necessary in a managed domain.
 * <p/>
 * Similar to {@link SocketCapabilityResolutionUnitTestCase}, but for host sockets and subsystems
 *
 * @author Brian Stansberry
 */
public class SocketCapabilityOnHostResolutionUnitTestCase {

    private static final String CAPABILITY = "capability";
    private static final String REQUIREMENT = "requirement";
    private static final String GLOBAL = "global";

    private static final PathAddress HOST_ADDRESS = PathAddress.pathAddress(PathElement.pathElement(HOST, "a"));

    private static final PathAddress GLOBAL_A = PathAddress.pathAddress(PathElement.pathElement(GLOBAL, "a"));
    private static final PathAddress SUBSYSTEM_A_1 = HOST_ADDRESS.append(PathElement.pathElement(SUBSYSTEM, "1"));
    private static final PathAddress SUBSYSTEM_A_2 = HOST_ADDRESS.append(PathElement.pathElement(SUBSYSTEM, "2"));
    private static final PathAddress SOCKET_A_1 = HOST_ADDRESS.append(PathElement.pathElement(SOCKET_BINDING_GROUP, "a"), PathElement.pathElement(SOCKET_BINDING, "1"));
    private static final PathAddress SOCKET_A_2 = HOST_ADDRESS.append(PathElement.pathElement(SOCKET_BINDING_GROUP, "a"), PathElement.pathElement(SOCKET_BINDING, "2"));

    private ServiceContainer container;
    private ModelController controller;
    @Before
    public void setupController() throws InterruptedException {
        container = ServiceContainer.Factory.create("test");
        ServiceTarget target = container.subTarget();
        ModelControllerService svc = new ModelControllerService();
        target.addService(ServiceName.of("ModelController")).setInstance(svc).install();
        svc.awaitStartup(30, TimeUnit.SECONDS);
        controller = svc.getValue();

        assertEquals(ControlledProcessState.State.RUNNING, svc.getCurrentProcessState());
    }

    @After
    public void shutdownServiceContainer() {
        if (container != null) {
            container.shutdown();
            try {
                container.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finally {
                container = null;
            }
        }
        System.out.println("======================");
    }

    @Test
    public void testSimpleGlobalRef() {
        ModelNode op = getCompositeOperation(
                getCapabilityOperation(SOCKET_A_1, "cap_a"),
                getCapabilityOperation(GLOBAL_A, "dep_a", "cap_a")
        );
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-2", RESULT).asBoolean());
    }

    @Test
    public void testSimpleRef() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_a"), getCapabilityOperation(SUBSYSTEM_A_1, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.get(RESULT, "step-2", RESULT).asBoolean());
    }

    /** Like testSimpleProfileRef but the order of ops is switched. Shouldn't make any difference. */
    @Test
    public void testReversedOrderSimpleRef() {
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
    public void testMissingRef() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_b"), getCapabilityOperation(SUBSYSTEM_A_1, "dep_a", "cap_a"));
        ModelNode response = controller.execute(op, null, null, null);
        validateMissingFailureDesc(response, "step-2", "cap_a", "host");
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
        validateMissingFailureDesc(response, "step-2", "cap_a", "host");
    }

    @Test
    public void testInconsistentInSocketBindingGroup() {
        ModelNode op = getCompositeOperation(getCapabilityOperation(SOCKET_A_1, "cap_a"),
                getCapabilityOperation(SOCKET_A_2, "dep_a", "cap_a"), getCapabilityOperation(SOCKET_A_2, "dep_b", "cap_b"));
        ModelNode response = controller.execute(op, null, null, null);
        assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        assertFalse(response.toString(), response.hasDefined(RESULT, "step-2", FAILURE_DESCRIPTION));
        validateMissingFailureDesc(response, "step-3", "cap_b", "host");
    }

    private static class ModelControllerService extends TestModelControllerService {

        ModelControllerService() {
            super(ProcessType.HOST_CONTROLLER, new NullConfigurationPersister(), new ControlledProcessState(true),
                    createResourceDefinition(null));
        }

        @Override
        protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {

            ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
            rootRegistration.registerOperationHandler(getOD(COMPOSITE), CompositeOperationHandler.INSTANCE, false);

            // Add a global handler that records capabilities and requirements
            rootRegistration.registerOperationHandler(getOD(CAPABILITY), new CapabilityOSH(), true);

            // Create resources defs representing something outside of profiles/socket-binding-group and then
            // a tree for profile and s-b-g. Note these aren't the real resource defs, they just follow the
            // real address pattern a bit, as those patterns are what drive the WFCORE-750 capability resolution logic
            rootRegistration.registerSubModel(createResourceDefinition(GLOBAL));

            ManagementResourceRegistration hostRegistration = rootRegistration.registerSubModel(createResourceDefinition(HOST));
            hostRegistration.registerSubModel(createResourceDefinition(SUBSYSTEM));
            ManagementResourceRegistration sbg = hostRegistration.registerSubModel(createResourceDefinition(SOCKET_BINDING_GROUP));
            sbg.registerSubModel(createResourceDefinition(SOCKET_BINDING));
        }
    }

    private static ResourceDefinition createResourceDefinition(String key) {
        PathElement pe = key == null ? null : PathElement.pathElement(key);
        return ResourceBuilder.Factory.create(pe, new NonResolvingResourceDescriptionResolver()).build();
    }

    private static class CapabilityOSH implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

            String capName = operation.require(CAPABILITY).asString();
            RuntimeCapability.Builder rcb = RuntimeCapability.Builder.of(capName);
            if (operation.hasDefined(REQUIREMENT)) {
                final String reqName = operation.get(REQUIREMENT).asString();
                rcb.addRequirements(reqName);
                context.addStep(new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        context.getResult().set(context.hasOptionalCapability(reqName, capName, null));
                    }
                }, OperationContext.Stage.RUNTIME);
            }  else {
                context.getResult().set(true);
            }
            context.registerCapability(rcb.build());
        }
    }

    private static ModelNode getCapabilityOperation(PathAddress pathAddress, String capability) {
        return getCapabilityOperation(pathAddress, capability, null);
    }

    private static ModelNode getCapabilityOperation(PathAddress pathAddress, String capability, String requirement) {

        ModelNode op = Util.createEmptyOperation(CAPABILITY, pathAddress);
        op.get(CAPABILITY).set(capability);
        if (requirement != null) {
            op.get(REQUIREMENT).set(requirement);
        }
        return op;
    }

    private static ModelNode getCompositeOperation(ModelNode... steps) {

        ModelNode op = new ModelNode();
        op.get(OP).set(COMPOSITE);
        op.get(OP_ADDR).setEmptyList();
        for (ModelNode step : steps) {
            op.get("steps").add(step);
        }
        return op;
    }

    private static void validateMissingFailureDesc(ModelNode response, String step, String cap, String context) {
        assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.hasDefined(FAILURE_DESCRIPTION));
        String failDesc = response.get(FAILURE_DESCRIPTION).asString();
        int loc = failDesc.indexOf(step);
        assertTrue(response.toString(), loc > 0);
        int lastLoc = loc;
        loc = failDesc.indexOf("WFLYCTL0369");
        assertTrue(response.toString(), loc > lastLoc);
        lastLoc = loc;
        loc = failDesc.indexOf(cap);
        assertTrue(response.toString(), loc > lastLoc);
        lastLoc = loc;
        loc = failDesc.indexOf(context);
        assertTrue(response.toString(), loc > lastLoc);

    }

    private static void validateInconsistentFailureDesc(ModelNode response, String step, String req, String cap, String context) {

        assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.hasDefined(FAILURE_DESCRIPTION));
        String failDesc = response.get(RESULT, step, FAILURE_DESCRIPTION).asString();
        int lastLoc = -1;
        int loc = failDesc.indexOf("WFLYCTL0405");
        assertTrue(response.toString(), loc > lastLoc);
        lastLoc = loc;
        loc = failDesc.indexOf(req);
        assertTrue(response.toString(), loc > lastLoc);
        lastLoc = loc;
        loc = failDesc.indexOf(cap);
        assertTrue(response.toString(), loc > lastLoc);
        lastLoc = loc;
        loc = failDesc.indexOf(context);
        assertTrue(response.toString(), loc > lastLoc);
    }
}
