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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.persistence.NullConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.junit.After;
import org.junit.Before;

/**
 * Base class for tests of capability resolution.
 *
 * @author Brian Stansberry
 */
abstract class AbstractCapabilityResolutionTestCase {

    protected static final String GLOBAL = "global";
    protected static final PathElement PROFILE_A = PathElement.pathElement(PROFILE, "a");
    protected static final PathElement PROFILE_B = PathElement.pathElement(PROFILE, "b");
    protected static final PathElement SBG_A = PathElement.pathElement(SOCKET_BINDING_GROUP, "a");
    protected static final PathElement SBG_B = PathElement.pathElement(SOCKET_BINDING_GROUP, "b");
    protected static final PathElement SBG_C = PathElement.pathElement(SOCKET_BINDING_GROUP, "c");
    protected static final PathElement SBG_D = PathElement.pathElement(SOCKET_BINDING_GROUP, "d");
    protected static final PathAddress GLOBAL_A = PathAddress.pathAddress(PathElement.pathElement(GLOBAL, "a"));
    protected static final PathAddress SUBSYSTEM_A_1 = PathAddress.pathAddress(PROFILE_A, PathElement.pathElement(SUBSYSTEM, "1"));
    protected static final PathAddress SUBSYSTEM_A_2 = PathAddress.pathAddress(PROFILE_A, PathElement.pathElement(SUBSYSTEM, "2"));
    protected static final PathAddress SUBSYSTEM_B_1 = PathAddress.pathAddress(PROFILE_B, PathElement.pathElement(SUBSYSTEM, "1"));
    protected static final PathAddress SUBSYSTEM_B_2 = PathAddress.pathAddress(PROFILE_B, PathElement.pathElement(SUBSYSTEM, "2"));
    protected static final PathAddress SOCKET_A_1 = PathAddress.pathAddress(SBG_A, PathElement.pathElement(SOCKET_BINDING, "1"));
    protected static final PathAddress SOCKET_A_2 = PathAddress.pathAddress(SBG_A, PathElement.pathElement(SOCKET_BINDING, "2"));
    protected static final PathAddress SOCKET_B_1 = PathAddress.pathAddress(SBG_B, PathElement.pathElement(SOCKET_BINDING, "1"));
    protected static final PathAddress SOCKET_B_2 = PathAddress.pathAddress(SBG_B, PathElement.pathElement(SOCKET_BINDING, "2"));
    protected static final PathAddress SOCKET_C_3 = PathAddress.pathAddress(SBG_C, PathElement.pathElement(SOCKET_BINDING, "3"));

    private static final String CAPABILITY = "capability";
    private static final String REQUIREMENT = "requirement";

    protected ModelController controller;
    private ServiceContainer container;

    @Before
    public void setupController() throws InterruptedException {
        System.out.println("=========  New Test \n");

        container = ServiceContainer.Factory.create("test");
        ServiceTarget target = container.subTarget();
        ModelControllerService svc = new ModelControllerService();
        ServiceBuilder<ModelController> builder = target.addService(ServiceName.of("ModelController"), svc);
        builder.install();
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

    protected static ModelNode getCapabilityOperation(PathAddress pathAddress, String capability) {
        return getCapabilityOperation(pathAddress, capability, null);
    }

    protected static ModelNode getCapabilityOperation(PathAddress pathAddress, String capability, String requirement) {

        ModelNode op = Util.createEmptyOperation(CAPABILITY, pathAddress);
        if (capability != null) {
            op.get(CAPABILITY).set(capability);
        }
        if (requirement != null) {
            op.get(REQUIREMENT).set(requirement);
        }
        return op;
    }

    protected static ModelNode getParentIncludeOperation(PathAddress pathAddress, String... includes) {

        ModelNode op = Util.createEmptyOperation("include", pathAddress);
        ModelNode includesNode = op.get(INCLUDES);
        for (String include : includes) {
            includesNode.add(include);
        }
        return op;
    }

    protected static ModelNode getCompositeOperation(ModelNode... steps) {

        ModelNode op = new ModelNode();
        op.get(OP).set(COMPOSITE);
        op.get(OP_ADDR).setEmptyList();
        for (ModelNode step : steps) {
            op.get("steps").add(step);
        }
        return op;
    }

    protected static void validateMissingFailureDesc(ModelNode response, String step, String cap, String context) {
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
        lastLoc = loc;
        loc = failDesc.indexOf(cap);
        assertTrue(response.toString(), loc > lastLoc);
        lastLoc = loc;
        loc = failDesc.indexOf(context);
        assertTrue(response.toString(), loc > lastLoc);

    }

    protected static void validateInconsistentFailureDesc(ModelNode response, String step, String req, String cap, String context) {

        assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.hasDefined(FAILURE_DESCRIPTION));
        String failDesc = response.get(RESULT, step, FAILURE_DESCRIPTION).asString();
        int lastLoc = -1;
        int loc = failDesc.indexOf("WFLYCTL0399");
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

    private static ResourceDefinition createResourceDefinition(String key) {
        PathElement pe = key == null ? null : PathElement.pathElement(key);
        return ResourceBuilder.Factory.create(pe, new NonResolvingResourceDescriptionResolver()).build();
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
            ManagementResourceRegistration profile = rootRegistration.registerSubModel(createResourceDefinition(PROFILE));
            OperationDefinition od = new SimpleOperationDefinitionBuilder("include", new NonResolvingResourceDescriptionResolver()).build();
            OperationStepHandler includeHandler = new ParentIncludeHandler();
            profile.registerOperationHandler(od, includeHandler);
            profile.registerSubModel(createResourceDefinition(SUBSYSTEM));
            ManagementResourceRegistration sbg = rootRegistration.registerSubModel(createResourceDefinition(SOCKET_BINDING_GROUP));
            sbg.registerOperationHandler(od, includeHandler);
            sbg.registerSubModel(createResourceDefinition(SOCKET_BINDING));
            rootRegistration.registerSubModel(createResourceDefinition(SERVER_GROUP));

            // Add the expected parent resources
            Resource rootResource = managementModel.getRootResource();
            rootResource.registerChild(PROFILE_A, Resource.Factory.create());
            rootResource.registerChild(PROFILE_B, Resource.Factory.create());
            rootResource.registerChild(SBG_A, Resource.Factory.create());
            rootResource.registerChild(SBG_B, Resource.Factory.create());
            rootResource.registerChild(SBG_C, Resource.Factory.create());
            rootResource.registerChild(SBG_D, Resource.Factory.create());
        }
    }

    private static class CapabilityOSH implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

            String capName = operation.hasDefined(CAPABILITY) ? operation.require(CAPABILITY).asString() : null;
            RuntimeCapability.Builder rcb = capName == null ? null : RuntimeCapability.Builder.of(capName);
            if (operation.hasDefined(REQUIREMENT)) {
                final String reqName = operation.get(REQUIREMENT).asString();
                if (capName != null) {
                    rcb.addRequirements(reqName);
                }
                context.addStep(new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        context.getResult().set(context.hasOptionalCapability(reqName, capName, null));
                    }
                }, OperationContext.Stage.RUNTIME);
            }  else {
                context.getResult().set(true);
            }
            if (capName != null) {
                context.registerCapability(rcb.build(), null);
            }
        }
    }

    private static class ParentIncludeHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
            resource.getModel().get(INCLUDES).set(operation.get(INCLUDES));
        }
    }
}
