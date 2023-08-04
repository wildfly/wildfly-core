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
package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.resources.ProfileResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceRegistry;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ProfileIncludesHandlerTestCase extends AbstractOperationTestCase {

    @Test
    public void testGoodProfileIncludesAdd() throws Exception {
        PathAddress addr = getProfileAddress("test");
        ModelNode op = Util.createAddOperation(addr);
        op.get(INCLUDES).add("profile-one").add("profile-two");
        MockOperationContext operationContext = getOperationContext(addr);
        ProfileAddHandler.INSTANCE.execute(operationContext, op);
        operationContext.executeNextStep();
    }


    @Test
    public void testBadProfileIncludesAdd() throws Exception {
        PathAddress addr = getProfileAddress("test");
        ModelNode op = Util.createAddOperation(addr);
        op.get(INCLUDES).add("profile-one").add("NOT_THERE");
        MockOperationContext operationContext = getOperationContext(addr);
        ProfileAddHandler.INSTANCE.execute(operationContext, op);
        operationContext.executeNextStep();
    }

    @Test
    public void testGoodProfileIncludesWrite() throws Exception {
        PathAddress addr = getProfileAddress("profile-one");
        ModelNode list = new ModelNode().add("profile-two");
        ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
        MockOperationContext operationContext = getOperationContext(addr);
        ProfileResourceDefinition.createIncludesValidationHandler().execute(operationContext, op);
        operationContext.executeNextStep();
    }

//    // WFCORE-833 Replaced with test in core-model-test ProfileTestCase
//    @Test(expected=OperationFailedException.class)
//    public void testBadProfileIncludesWrite() throws Exception {
//        PathAddress addr = getProfileAddress("profile-one");
//        ModelNode list = new ModelNode().add("bad-profile");
//        ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
//        MockOperationContext operationContext = getOperationContext(addr);
//        ProfileResourceDefinition.createIncludesValidationHandler().execute(operationContext, op);
//        operationContext.executeNextStep();
//    }

    @Test(expected=OperationFailedException.class)
    public void testCyclicProfileIncludesWrite() throws Exception {
        PathAddress addr = getProfileAddress("profile-three");
        ModelNode list = new ModelNode().add("profile-four");
        ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
        MockOperationContext operationContext = getOperationContextWithIncludes(addr);
        ProfileResourceDefinition.createIncludesValidationHandler().execute(operationContext, op);
        operationContext.executeNextStep();
    }

    @Test
    public void testGoodProfileIncludesRemove() throws Exception {
        PathAddress addr = getProfileAddress("profile-four");
        ModelNode op = Util.createRemoveOperation(addr);
        MockOperationContext operationContext = getOperationContextWithIncludes(addr);
        ProfileRemoveHandler.INSTANCE.execute(operationContext, op);
        // WFCORE-833 no next validation step any more
        //operationContext.executeNextStep();
    }

//    // WFCORE-833 Replaced with test in core-model-test ProfileTestCase
//    @Test(expected=OperationFailedException.class)
//    public void testBadProfileIncludesRemove() throws Exception {
//        PathAddress addr = getProfileAddress("profile-three");
//        ModelNode op = Util.createRemoveOperation(addr);
//        MockOperationContext operationContext = getOperationContextWithIncludes(addr);
//        ProfileRemoveHandler.INSTANCE.execute(operationContext, op);
//        operationContext.executeNextStep();
//    }

    @Test
    public void testIncludesWithNoOverriddenSubsystems() throws Exception {
        //Here we test changing the includes attribute value
        //Testing what happens when adding subsystems at runtime becomes a bit too hard to mock up
        //so we test that in ServerManagementTestCase
        PathAddress addr = getProfileAddress("profile-four");
        ModelNode list = new ModelNode().add("profile-three");
        ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
        MockOperationContext operationContext = getOperationContextForSubsystemIncludes(addr, new RootResourceInitializer() {
            @Override
            public void addAdditionalResources(Resource root) {
                Resource subsystemA = Resource.Factory.create();
                root.getChild(PathElement.pathElement(PROFILE, "profile-three"))
                        .registerChild(PathElement.pathElement(SUBSYSTEM, "a"), subsystemA);

                Resource subsystemB = Resource.Factory.create();
                Resource profile4 = root.getChild(PathElement.pathElement(PROFILE, "profile-four"));
                profile4.registerChild(PathElement.pathElement(SUBSYSTEM, "b"), subsystemB);
            }
        });
        ProfileResourceDefinition.createIncludesValidationHandler().execute(operationContext, op);
        operationContext.executeNextStep();
    }

    @Test
    public void testIncludesWithOverriddenSubsystems() throws Exception {
        // Here we test changing the includes attribute value
        // Testing what happens when adding subsystems at runtime becomes a bit too hard to mock up
        // so we test that in ServerManagementTestCase
        PathAddress addr = getProfileAddress("profile-four");
        ModelNode list = new ModelNode().add("profile-three");
        ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
        MockOperationContext operationContext = getOperationContextForSubsystemIncludes(addr, new RootResourceInitializer() {
            @Override
            public void addAdditionalResources(Resource root) {
                Resource subsystemA = Resource.Factory.create();
                root.getChild(PathElement.pathElement(PROFILE, "profile-three"))
                        .registerChild(PathElement.pathElement(SUBSYSTEM, "a"), subsystemA);

                Resource subsystemB = Resource.Factory.create();
                Resource profile4 = root.getChild(PathElement.pathElement(PROFILE, "profile-four"));
                profile4.registerChild(PathElement.pathElement(SUBSYSTEM, "a"), subsystemB);
            }
        });
        ProfileResourceDefinition.createIncludesValidationHandler().execute(operationContext, op);
        try {
            operationContext.executeNextStep();
            Assert.fail("Expected error");
        } catch (OperationFailedException expected) {
            Assert.assertTrue(expected.getMessage().contains("164"));
            Assert.assertTrue(expected.getMessage().contains("'profile-four'"));
            Assert.assertTrue(expected.getMessage().contains("'a'"));
            Assert.assertTrue(expected.getMessage().contains("'profile-three'"));
        }
    }

    @Test
    public void testProfileWithSubsystemsIncludesSameSubsystems() throws Exception {
        // Here we test changing the includes attribute value
        // Testing what happens when adding subsystems at runtime becomes a bit too hard to mock up
        // so we test that in ServerManagementTestCase
        PathAddress addr = getProfileAddress("profile-five");
        ModelNode list = new ModelNode().add("profile-three").add("profile-four");
        ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
        MockOperationContext operationContext = getOperationContextForSubsystemIncludes(addr, new RootResourceInitializer() {
            @Override
            public void addAdditionalResources(Resource root) {
                Resource subsystemA = Resource.Factory.create();
                root.getChild(PathElement.pathElement(PROFILE, "profile-three"))
                        .registerChild(PathElement.pathElement(SUBSYSTEM, "a"), subsystemA);

                Resource subsystemB = Resource.Factory.create();
                Resource profile4 = root.getChild(PathElement.pathElement(PROFILE, "profile-four"));
                profile4.registerChild(PathElement.pathElement(SUBSYSTEM, "a"), subsystemB);

                Resource subsystemC = Resource.Factory.create();
                Resource profile5 = root.getChild(PathElement.pathElement(PROFILE, "profile-five"));
                profile5.registerChild(PathElement.pathElement(SUBSYSTEM, "x"), subsystemC);
            }
        });
        ProfileResourceDefinition.createIncludesValidationHandler().execute(operationContext, op);
        try {
            operationContext.executeNextStep();
            Assert.fail("Expected error");
        } catch (OperationFailedException expected) {
            Assert.assertTrue(expected.getMessage().contains("167"));
            Assert.assertTrue(expected.getMessage().contains("'profile-five'"));
            Assert.assertTrue(expected.getMessage().contains("'profile-four'"));
            Assert.assertTrue(expected.getMessage().contains("'profile-three'"));
            Assert.assertTrue(expected.getMessage().contains("'a'"));
        }
    }

    @Test
    public void testEmptyProfileIncludesSameSubsystems() throws Exception {
        // Here we test changing the includes attribute value
        // Testing what happens when adding subsystems at runtime becomes a bit too hard to mock up
        // so we test that in ServerManagementTestCase
        PathAddress addr = getProfileAddress("profile-five");
        ModelNode list = new ModelNode().add("profile-three").add("profile-four");
        ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
        MockOperationContext operationContext = getOperationContextForSubsystemIncludes(addr, new RootResourceInitializer() {
            @Override
            public void addAdditionalResources(Resource root) {
                Resource subsystemA = Resource.Factory.create();
                root.getChild(PathElement.pathElement(PROFILE, "profile-three"))
                        .registerChild(PathElement.pathElement(SUBSYSTEM, "a"), subsystemA);

                Resource subsystemB = Resource.Factory.create();
                Resource profile4 = root.getChild(PathElement.pathElement(PROFILE, "profile-four"));
                profile4.registerChild(PathElement.pathElement(SUBSYSTEM, "a"), subsystemB);

                // profile-four is empty
            }
        });
        ProfileResourceDefinition.createIncludesValidationHandler().execute(operationContext, op);
        try {
            operationContext.executeNextStep();
            Assert.fail("Expected error");
        } catch (OperationFailedException expected) {
            Assert.assertTrue(expected.getMessage().contains("167"));
            Assert.assertTrue(expected.getMessage().contains("'profile-five'"));
            Assert.assertTrue(expected.getMessage().contains("'profile-four'"));
            Assert.assertTrue(expected.getMessage().contains("'profile-three'"));
            Assert.assertTrue(expected.getMessage().contains("'a'"));
        }
    }

    private PathAddress getProfileAddress(String profileName) {
        return PathAddress.pathAddress(PROFILE, profileName);
    }

    MockOperationContext getOperationContext(final PathAddress operationAddress) {
        final Resource root = createRootResource();
        return new MockOperationContext(root, false, operationAddress, false);
    }


    MockOperationContext getOperationContextWithIncludes(final PathAddress operationAddress) {
        final Resource root = createRootResource();
        Resource profileThree = Resource.Factory.create();
        root.registerChild(PathElement.pathElement(PROFILE, "profile-three"), profileThree);

        Resource profileFour = Resource.Factory.create();
        profileFour.getModel().get(INCLUDES).add("profile-three");
        root.registerChild(PathElement.pathElement(PROFILE, "profile-four"), profileFour);
        return new MockOperationContext(root, false, operationAddress, false);

    }

    MockOperationContext getOperationContextForSubsystemIncludes(final PathAddress operationAddress, RootResourceInitializer initializer) {
        final Resource root = createRootResource();
        Resource profileThree = Resource.Factory.create();
        root.registerChild(PathElement.pathElement(PROFILE, "profile-three"), profileThree);

        Resource profileFour = Resource.Factory.create();
        root.registerChild(PathElement.pathElement(PROFILE, "profile-four"), profileFour);

        Resource profileFive = Resource.Factory.create();
        root.registerChild(PathElement.pathElement(PROFILE, "profile-five"), profileFive);

        initializer.addAdditionalResources(root);
        return new MockOperationContext(root, false, operationAddress, false);
    }

    private class MockOperationContext extends AbstractOperationTestCase.MockOperationContext {
        private boolean reloadRequired;
        private boolean rollback;
        private OperationStepHandler nextStep;

        protected MockOperationContext(final Resource root, final boolean booting, final PathAddress operationAddress, final boolean rollback) {
            super(root, booting, operationAddress);
            this.rollback = rollback;
            when(this.registration.getCapabilities()).thenReturn(Collections.singleton(ProfileResourceDefinition.PROFILE_CAPABILITY));
        }

        public void completeStep(ResultHandler resultHandler) {
            if (nextStep != null) {
                try {
                    OperationStepHandler step = nextStep;
                    nextStep = null;
                    step.execute(this, null);
                } catch (OperationFailedException e) {
                    throw new OperationFailedRuntimeException(e);
                }
            } else if (rollback) {
                resultHandler.handleResult(ResultAction.ROLLBACK, this, null);
            }
        }

        public void reloadRequired() {
            reloadRequired = true;
        }

        public boolean isReloadRequired() {
            return reloadRequired;
        }

        public void revertReloadRequired() {
            reloadRequired = false;
        }

        public void addStep(OperationStepHandler step, OperationContext.Stage stage) throws IllegalArgumentException {
            if (step instanceof DomainModelIncludesValidator) {
                nextStep = step;
            }
        }

        public void addStep(ModelNode operation, OperationStepHandler step, OperationContext.Stage stage) throws IllegalArgumentException {
            if (operation.get(OP).asString().equals("verify-running-server")) {
                return;
            }
            super.addStep(operation, step, stage);
        }

        @Override
        public boolean isBooting() {
            return false;
        }

        public Resource removeResource(PathAddress address) throws UnsupportedOperationException {
            PathElement element = operationAddress.getLastElement();
            PathAddress parentAddress = operationAddress.size() > 1 ? operationAddress.subAddress(0, operationAddress.size() - 1) : PathAddress.EMPTY_ADDRESS;
            Resource parent = root.navigate(parentAddress);
            return parent.removeChild(element);
        }

        public void executeNextStep() throws OperationFailedException {
            nextStep.execute(this, new ModelNode());
        }

        @Override
        public ServiceRegistry getServiceRegistry(boolean modify) throws UnsupportedOperationException {
            return null;
        }
    }

    private static class OperationFailedRuntimeException extends RuntimeException {
        public OperationFailedRuntimeException(OperationFailedException e) {
            super(e.getMessage());

        }
    }

    /**
     * Allows tests to add more resources to the model
     */
    private interface RootResourceInitializer {
        void addAdditionalResources(Resource root);
    }
}
