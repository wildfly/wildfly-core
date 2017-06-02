/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.controller.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALIAS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_ALIASES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODEL_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.test.TestUtils.createOperationDefinition;

import java.util.Set;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;

/**
 * Reproducer for WFLY-5290. The alias add has a type parameter which determines the address value under the 'main' key.
 * There can only be one entry for 'main'.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class AliasContextTestCase extends AbstractControllerTestBase {

    private static final String MAIN = "main";
    private static final String ALIASED = "aliased";

    @Test
    public void testReadResource() throws Exception {
        ModelNode result = executeForResult(getReadResourceOperation(PathAddress.EMPTY_ADDRESS, true, true));
        ModelNode expected = new ModelNode();
        expected.get(MAIN);
        expected.get(ALIASED);
        Assert.assertEquals(expected, result);

        ModelNode add = Util.createAddOperation(PathAddress.pathAddress(ALIASED, ALIAS));
        add.get(READ_WRITE.getName()).set("RW");
        add.get(TYPE.getName()).set("one");
        executeCheckNoFailure(add);

        result = executeForResult(getReadResourceOperation(PathAddress.EMPTY_ADDRESS, true, false));
        expected = new ModelNode();
        expected.get(MAIN).get("one").get("rw").set("RW");
        Assert.assertEquals(expected, result);

        result = executeForResult(getReadResourceOperation(PathAddress.pathAddress(MAIN, "one"), true, false));
        Assert.assertEquals(expected.get(MAIN, "one"), result);

        result = executeForResult(getReadResourceOperation(PathAddress.pathAddress(ALIASED, ALIAS), true, false));
        Assert.assertEquals(expected.get(MAIN, "one"), result);

        result = executeForResult(getReadResourceOperation(PathAddress.EMPTY_ADDRESS, true, true));
        expected.get(ALIASED, ALIAS).set(expected.get(MAIN, "one"));
        Assert.assertEquals(expected, result);

        result = executeForResult(getReadResourceOperation(PathAddress.pathAddress(MAIN, "one"), true, true));
        Assert.assertEquals(expected.get(MAIN, "one"), result);

        result = executeForResult(getReadResourceOperation(PathAddress.pathAddress(ALIASED, ALIAS), true, true));
        Assert.assertEquals(expected.get(MAIN, "one"), result);

        executeCheckNoFailure(Util.createRemoveOperation(PathAddress.pathAddress(ALIASED, ALIAS)));
        result = executeForResult(getReadResourceOperation(PathAddress.EMPTY_ADDRESS, true, true));
        expected = new ModelNode();
        expected.get(MAIN);
        expected.get(ALIASED);
        Assert.assertEquals(expected, result);
    }

    @Test
    public void testReadResourceDescription() throws Exception {
        ModelNode result = executeForResult(getReadResourceDescriptionOperation(PathAddress.EMPTY_ADDRESS, true, false));
        ModelNode children = result.get(CHILDREN);
        Assert.assertEquals(1, children.keys().size());
        Assert.assertTrue(children.hasDefined(MAIN, MODEL_DESCRIPTION, "*", ATTRIBUTES, "rw"));

        result = executeForResult(getReadResourceDescriptionOperation(PathAddress.pathAddress(MAIN, "one"), true, false));
        Assert.assertTrue(result.hasDefined(ATTRIBUTES, "rw"));

        result = executeForResult(getReadResourceDescriptionOperation(PathAddress.pathAddress(ALIASED, ALIAS), true, false));
        //This ends up being in an array since no resource, ends up being main=>*
        Assert.assertTrue(result.hasDefined(ATTRIBUTES, "rw"));

        result = executeForResult(getReadResourceDescriptionOperation(PathAddress.EMPTY_ADDRESS, true, true));
        children = result.get(CHILDREN);
        Assert.assertEquals(2, children.keys().size());
        Assert.assertTrue(children.hasDefined(MAIN, MODEL_DESCRIPTION, "*", ATTRIBUTES, "rw"));
        Assert.assertTrue(children.hasDefined(ALIASED, MODEL_DESCRIPTION, ALIAS, ATTRIBUTES, "rw"));

        result = executeForResult(getReadResourceDescriptionOperation(PathAddress.pathAddress(MAIN, "one"), true, true));
        Assert.assertTrue(result.hasDefined(ATTRIBUTES, "rw"));

        result = executeForResult(getReadResourceDescriptionOperation(PathAddress.pathAddress(ALIASED, ALIAS), true, true));
        //This ends up being in an array since no resource, ends up being main=>*
        Assert.assertTrue(result.hasDefined(ATTRIBUTES, "rw"));
    }

    private ModelNode getReadResourceOperation(PathAddress addr, boolean recursive, boolean aliases) {
        ModelNode rr = Util.createEmptyOperation(READ_RESOURCE_OPERATION, addr);
        rr.get(RECURSIVE).set(recursive);
        rr.get(INCLUDE_ALIASES).set(aliases);
        return rr;
    }

    private ModelNode getReadResourceDescriptionOperation(PathAddress addr, boolean recursive, boolean aliases) {
        ModelNode rr = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, addr);
        rr.get(RECURSIVE).set(recursive);
        rr.get(INCLUDE_ALIASES).set(aliases);
        return rr;
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(registration, processType);

        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        GlobalNotifications.registerGlobalNotifications(registration, processType);

        ManagementResourceRegistration mainReg = registration.registerSubModel(new MainResourceDefinition());
        registration.registerAlias(PathElement.pathElement(ALIASED, ALIAS), new TestAliasEntry(mainReg));
    }

    private static SimpleAttributeDefinition READ_WRITE = new SimpleAttributeDefinitionBuilder("rw", ModelType.STRING, true).build();
    private static SimpleAttributeDefinition TYPE = new SimpleAttributeDefinitionBuilder("type", ModelType.STRING, false).build();

    private class MainResourceDefinition extends SimpleResourceDefinition {

        public MainResourceDefinition() {
            super(PathElement.pathElement(MAIN), new NonResolvingResourceDescriptionResolver(), null, new ModelOnlyRemoveStepHandler());
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadWriteAttribute(READ_WRITE, null, new ModelOnlyWriteAttributeHandler(READ_WRITE));
        }


        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            super.registerOperations(resourceRegistration);
            resourceRegistration.registerOperationHandler(createOperationDefinition(ADD, READ_WRITE, TYPE), new ModelOnlyAddStepHandler(READ_WRITE));
        }
    }

    public static class TestAliasEntry extends AliasEntry {

        public TestAliasEntry(final ManagementResourceRegistration target) {
            super(target);
        }

        @Override
        public PathAddress convertToTargetAddress(PathAddress addr, AliasContext aliasContext) {
            final ModelNode op = aliasContext.getOperation();
            if (op.get(OP).asString().equals(ADD)) {
                return PathAddress.pathAddress(MAIN, op.get(TYPE.getName()).asString());
            }
            Resource root = aliasContext.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);
            Set<String> names = root.getChildrenNames(MAIN);
            if (names.size() > 1) {
                throw new AssertionError("There should be at most one child");
            } else if (names.size() == 0) {
                return PathAddress.pathAddress(MAIN, "*");
            }
            return PathAddress.pathAddress(MAIN, names.iterator().next());
        }
    }
}
