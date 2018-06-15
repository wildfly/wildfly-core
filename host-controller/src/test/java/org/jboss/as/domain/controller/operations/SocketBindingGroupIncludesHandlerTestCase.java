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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.RuntimeCapability;
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
public class SocketBindingGroupIncludesHandlerTestCase extends AbstractOperationTestCase {

    @Test
    public void testGoodSocketBindingGroupIncludesAdd() throws Exception {
        PathAddress addr = getSocketBindingGroupAddress("test");
        ModelNode op = Util.createAddOperation(addr);
        op.get(DEFAULT_INTERFACE).set("public");
        op.get(INCLUDES).add("binding-one").add("binding-two");
        MockOperationContext operationContext = getOperationContext(addr);
        SocketBindingGroupAddHandler.INSTANCE.execute(operationContext, op);
        operationContext.executeNextStep();
    }


//    // WFCORE-833 replaced by DomainSocketBindingGroupTestCase.testBadSocketBindingGroupIncludesAdd()
//    @Test(expected=OperationFailedException.class)
//    public void testBadSocketBindingGroupIncludesAdd() throws Exception {
//        PathAddress addr = getSocketBindingGroupAddress("test");
//        ModelNode op = Util.createAddOperation(addr);
//        op.get(DEFAULT_INTERFACE).set("public");
//        op.get(INCLUDES).add("binding-one").add("NOT_THERE");
//        MockOperationContext operationContext = getOperationContext(addr);
//        SocketBindingGroupAddHandler.INSTANCE.execute(operationContext, op);
//        operationContext.executeNextStep();
//    }

    @Test
    public void testGoodSocketBindingGroupIncludesWrite() throws Exception {
        PathAddress addr = getSocketBindingGroupAddress("binding-one");
        ModelNode list = new ModelNode().add("binding-two");
        ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
        MockOperationContext operationContext = getOperationContext(addr);
        SocketBindingGroupResourceDefinition.createIncludesValidationHandler().execute(operationContext, op);
        operationContext.executeNextStep();
    }

//    // WFCORE-833 replaced by DomainSocketBindingGroupTestCase.testBadSocketBindingGroupIncludesWrite()
//    @Test(expected=OperationFailedException.class)
//    public void testBadSocketBindingGroupIncludesWrite() throws Exception {
//        PathAddress addr = getSocketBindingGroupAddress("binding-one");
//        ModelNode list = new ModelNode().add("bad-SocketBindingGroup");
//        ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
//        MockOperationContext operationContext = getOperationContext(addr);
//        SocketBindingGroupResourceDefinition.createRestartRequiredHandler().execute(operationContext, op);
//        operationContext.executeNextStep();
//    }

    @Test(expected=OperationFailedException.class)
    public void testCyclicSocketBindingGroupIncludesWrite() throws Exception {
        PathAddress addr = getSocketBindingGroupAddress("binding-three");
        ModelNode list = new ModelNode().add("binding-four");
        ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
        MockOperationContext operationContext = getOperationContextWithIncludes(addr);
        SocketBindingGroupResourceDefinition.createIncludesValidationHandler().execute(operationContext, op);
        operationContext.executeNextStep();
    }

    @Test
    public void testGoodSocketBindingGroupIncludesRemove() throws Exception {
        PathAddress addr = getSocketBindingGroupAddress("binding-four");
        ModelNode op = Util.createRemoveOperation(addr);
        MockOperationContext operationContext = getOperationContextWithIncludes(addr);
        ModelOnlyRemoveStepHandler.INSTANCE.execute(operationContext, op);
        // WFCORE-833 no next validation step any more
        //operationContext.executeNextStep();
    }

//    // WFCORE-833 replaced by DomainSocketBindingGroupTestCase.testBadSocketBindingGroupIncludesRemove()
//    @Test(expected=OperationFailedException.class)
//    public void testBadSocketBindingGroupIncludesRemove() throws Exception {
//        PathAddress addr = getSocketBindingGroupAddress("binding-three");
//        ModelNode op = Util.createRemoveOperation(addr);
//        MockOperationContext operationContext = getOperationContextWithIncludes(addr);
//        ModelOnlyRemoveStepHandler.INSTANCE.execute(operationContext, op);
//        operationContext.executeNextStep();
//    }

    @Test
    public void testIncludesWithNoOverriddenSubsystems() throws Exception {
        //Here we test changing the includes attribute value
        //Testing what happens when adding subsystems at runtime becomes a bit too hard to mock up
        //so we test that in ServerManagementTestCase
        PathAddress addr = getSocketBindingGroupAddress("binding-four");
        ModelNode list = new ModelNode().add("binding-three");
        ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
        MockOperationContext operationContext = getOperationContextForSocketBindingIncludes(addr, new RootResourceInitializer() {
            @Override
            public void addAdditionalResources(Resource root) {
                Resource subsystemA = Resource.Factory.create();
                root.getChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-three"))
                        .registerChild(PathElement.pathElement(SUBSYSTEM, "a"), subsystemA);

                Resource subsystemB = Resource.Factory.create();
                Resource SocketBindingGroup4 = root.getChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-four"));
                SocketBindingGroup4.registerChild(PathElement.pathElement(SUBSYSTEM, "b"), subsystemB);
            }
        });
        SocketBindingGroupResourceDefinition.createIncludesValidationHandler().execute(operationContext, op);
        operationContext.executeNextStep();
    }

    @Test
    public void testIncludesWithOverriddenSocketBindings() throws Exception {
        try {
            //Here we test changing the includes attribute value
            //Testing what happens when adding subsystems at runtime becomes a bit too hard to mock up
            //so we test that in ServerManagementTestCase
            PathAddress addr = getSocketBindingGroupAddress("binding-four");
            ModelNode list = new ModelNode().add("binding-three");
            ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
            MockOperationContext operationContext = getOperationContextForSocketBindingIncludes(addr, new RootResourceInitializer() {
                @Override
                public void addAdditionalResources(Resource root) {
                    Resource subsystemA = Resource.Factory.create();
                    root.getChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-three"))
                            .registerChild(PathElement.pathElement(SOCKET_BINDING, "a"), subsystemA);

                    Resource subsystemB = Resource.Factory.create();
                    Resource SocketBindingGroup4 = root.getChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-four"));
                    SocketBindingGroup4.registerChild(PathElement.pathElement(SOCKET_BINDING, "a"), subsystemB);
                }
            });
            SocketBindingGroupResourceDefinition.createIncludesValidationHandler().execute(operationContext, op);
            operationContext.executeNextStep();
            Assert.fail("Expected error");
        } catch (OperationFailedException expected) {
            Assert.assertTrue(expected.getMessage().contains("166"));
            Assert.assertTrue(expected.getMessage().contains("'binding-four'"));
            Assert.assertTrue(expected.getMessage().contains("'binding-three'"));
            Assert.assertTrue(expected.getMessage().contains("'a'"));
        }
    }


    @Test
    public void testGroupWithBindingsIncludesSameBindings() throws Exception {
        try {
            //Here we test changing the includes attribute value
            //Testing what happens when adding subsystems at runtime becomes a bit too hard to mock up
            //so we test that in ServerManagementTestCase
            PathAddress addr = getSocketBindingGroupAddress("binding-five");
            ModelNode list = new ModelNode().add("binding-three").add("binding-four");
            ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
            MockOperationContext operationContext = getOperationContextForSocketBindingIncludes(addr, new RootResourceInitializer() {
                @Override
                public void addAdditionalResources(Resource root) {
                    Resource bindingA = Resource.Factory.create();
                    root.getChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-three"))
                            .registerChild(PathElement.pathElement(SOCKET_BINDING, "a"), bindingA);

                    Resource bindingB = Resource.Factory.create();
                    Resource group4 = root.getChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-four"));
                    group4.registerChild(PathElement.pathElement(SOCKET_BINDING, "a"), bindingB);

                    Resource bindingC = Resource.Factory.create();
                    Resource group5 = root.getChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-five"));
                    group5.registerChild(PathElement.pathElement(SOCKET_BINDING, "x"), bindingC);
                }
            });
            ProfileResourceDefinition.createIncludesValidationHandler().execute(operationContext, op);
            operationContext.executeNextStep();
            Assert.fail("Expected error");
        } catch (OperationFailedException expected) {
            Assert.assertTrue(expected.getMessage().contains("168"));
            Assert.assertTrue(expected.getMessage().contains("'binding-five'"));
            Assert.assertTrue(expected.getMessage().contains("'binding-four'"));
            Assert.assertTrue(expected.getMessage().contains("'binding-three'"));
            Assert.assertTrue(expected.getMessage().contains("'a'"));
        }
    }

    @Test
    public void testEmptyGroupIncludesSameBindings() throws Exception {
        try {
            //Here we test changing the includes attribute value
            //Testing what happens when adding subsystems at runtime becomes a bit too hard to mock up
            //so we test that in ServerManagementTestCase
            PathAddress addr = getSocketBindingGroupAddress("binding-five");
            ModelNode list = new ModelNode().add("binding-three").add("binding-four");
            ModelNode op = Util.getWriteAttributeOperation(addr, INCLUDES, list);
            MockOperationContext operationContext = getOperationContextForSocketBindingIncludes(addr, new RootResourceInitializer() {
                @Override
                public void addAdditionalResources(Resource root) {
                    Resource bindingA = Resource.Factory.create();
                    root.getChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-three"))
                            .registerChild(PathElement.pathElement(SOCKET_BINDING, "a"), bindingA);

                    Resource bindingB = Resource.Factory.create();
                    Resource group4 = root.getChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-four"));
                    group4.registerChild(PathElement.pathElement(SOCKET_BINDING, "a"), bindingB);

                    //binding-five is empty
                }
            });
            ProfileResourceDefinition.createIncludesValidationHandler().execute(operationContext, op);
            operationContext.executeNextStep();
            Assert.fail("Expected error");
        } catch (OperationFailedException expected) {
            Assert.assertTrue(expected.getMessage().contains("168"));
            Assert.assertTrue(expected.getMessage().contains("'binding-five'"));
            Assert.assertTrue(expected.getMessage().contains("'binding-four'"));
            Assert.assertTrue(expected.getMessage().contains("'binding-three'"));
            Assert.assertTrue(expected.getMessage().contains("'a'"));
        }
    }


    private PathAddress getSocketBindingGroupAddress(String SocketBindingGroupName) {
        return PathAddress.pathAddress(SOCKET_BINDING_GROUP, SocketBindingGroupName);
    }

    MockOperationContext getOperationContext(final PathAddress operationAddress) {
        final Resource root = createRootResource();
        return new MockOperationContext(root, false, operationAddress, false);
    }


    MockOperationContext getOperationContextWithIncludes(final PathAddress operationAddress) {
        final Resource root = createRootResource();
        Resource socketBindingGroupThree = Resource.Factory.create();
        root.registerChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-three"), socketBindingGroupThree);

        Resource socketBindingGroupFour = Resource.Factory.create();
        socketBindingGroupFour.getModel().get(INCLUDES).add("binding-three");
        root.registerChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-four"), socketBindingGroupFour);

        return new MockOperationContext(root, false, operationAddress, false);

    }

    MockOperationContext getOperationContextForSocketBindingIncludes(final PathAddress operationAddress, RootResourceInitializer initializer) {
        final Resource root = createRootResource();
        Resource socketBindingGroupThree = Resource.Factory.create();
        root.registerChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-three"), socketBindingGroupThree);

        Resource socketBindingGroupFour = Resource.Factory.create();
        root.registerChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-four"), socketBindingGroupFour);

        Resource socketBindingGroupFive = Resource.Factory.create();
        root.registerChild(PathElement.pathElement(SOCKET_BINDING_GROUP, "binding-five"), socketBindingGroupFive);

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
            Set<RuntimeCapability> capabilities = new HashSet<>();
            capabilities.add(SocketBindingGroupResourceDefinition.SOCKET_BINDING_GROUP_CAPABILITY);
            capabilities.add(ProfileResourceDefinition.PROFILE_CAPABILITY);
            when(this.registration.getCapabilities()).thenReturn(capabilities);
        }

        public void completeStep(ResultHandler resultHandler) {
            if (nextStep != null) {
                stepCompleted();
            } else if (rollback) {
                resultHandler.handleResult(ResultAction.ROLLBACK, this, null);
            }
        }

        public void stepCompleted() {
            if (nextStep != null) {
                try {
                    OperationStepHandler step = nextStep;
                    nextStep = null;
                    step.execute(this, null);
                } catch (OperationFailedException e) {
                    throw new OperationFailedRuntimeException(e);
                }
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

        public void addStep(OperationStepHandler step, Stage stage) throws IllegalArgumentException {
            if (step instanceof DomainModelIncludesValidator) {
                nextStep = step;
            }
        }

        public void addStep(ModelNode operation, OperationStepHandler step, Stage stage) throws IllegalArgumentException {
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
