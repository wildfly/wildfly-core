/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition.Parameters;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.ApplicationTypeAccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.AliasEntry.AliasContext;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Before;
import org.junit.Test;

/**
 * TODO class javadoc.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class CoreManagementResourceRegistrationUnitTestCase {

    private ManagementResourceRegistration rootRegistration;
    private final PathElement childElement = PathElement.pathElement("child");
    private final PathElement fullChildElement = PathElement.pathElement("child", "a");
    private final PathAddress childAddress = PathAddress.pathAddress(childElement);
    private final PathAddress fullChildAddress = PathAddress.pathAddress(fullChildElement);
    private final PathElement grandchildElement = PathElement.pathElement("grandchild");
    private final PathElement fullGrandchildElement = PathElement.pathElement("grandchild", "b");
    private final PathAddress grandchildAddress = childAddress.append(grandchildElement);
    private final PathAddress fullGrandchildAddress = childAddress.append(fullGrandchildElement);

    @Before
    public void setup() {
        rootRegistration = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(new SimpleResourceDefinition(null, NonResolvingResourceDescriptionResolver.INSTANCE));
    }

    @Test
    public void simpleAlias() {
        PathElement parentPath = PathElement.pathElement("parent");
        ManagementResourceRegistration parent = this.rootRegistration.registerSubModel(new SimpleResourceDefinition(parentPath, NonResolvingResourceDescriptionResolver.INSTANCE));

        PathElement path = PathElement.pathElement("foo", "bar");
        PathElement alias = PathElement.pathElement("foo-alias", "bar-alias");
        parent.registerSubModel(new SimpleResourceDefinition(path, NonResolvingResourceDescriptionResolver.INSTANCE)).registerAlias(alias);
        PathElement wildcardPath = PathElement.pathElement("baz");
        PathElement wildcardAlias = PathElement.pathElement("baz-alias");
        parent.registerSubModel(new SimpleResourceDefinition(wildcardPath, NonResolvingResourceDescriptionResolver.INSTANCE)).registerAlias(wildcardAlias);

        AliasContext context = mock(AliasContext.class);

        PathAddress parentAddress = PathAddress.pathAddress(parentPath.getKey(), "test");
        PathAddress address = parent.getSubModel(PathAddress.pathAddress(alias)).getAliasEntry().convertToTargetAddress(parentAddress.append(alias), mock(AliasContext.class));
        PathAddress wildcardAddress = parent.getSubModel(PathAddress.pathAddress(wildcardAlias)).getAliasEntry().convertToTargetAddress(parentAddress.append("baz-alias", "qux"), mock(AliasContext.class));

        verifyNoInteractions(context);

        assertEquals(parentAddress.append(path), address);
        assertEquals(parentAddress.append("baz", "qux"), wildcardAddress);
    }

    @Test
    public void testHandlersOnRootResource() throws Exception {

        rootRegistration.registerOperationHandler(getOpDef("one"), TestHandler.ONE);
        rootRegistration.registerOperationHandler(getOpDef("two", OperationEntry.Flag.READ_ONLY), TestHandler.TWO);

        OperationStepHandler oneHandler = rootRegistration.getOperationHandler(PathAddress.EMPTY_ADDRESS, "one");
        assertSame(TestHandler.ONE, oneHandler);

        OperationStepHandler twoHandler = rootRegistration.getOperationHandler(PathAddress.EMPTY_ADDRESS, "two");
        assertSame(TestHandler.TWO, twoHandler);
    }

    @Test
    public void testHandlersOnChildResource() throws Exception {

        ManagementResourceRegistration child = rootRegistration.registerSubModel(new SimpleResourceDefinition(childElement, NonResolvingResourceDescriptionResolver.INSTANCE));
        child.registerOperationHandler(getOpDef("one"), TestHandler.ONE);
        child.registerOperationHandler(getOpDef("two", OperationEntry.Flag.READ_ONLY), TestHandler.TWO);

        OperationStepHandler oneHandler = child.getOperationHandler(PathAddress.EMPTY_ADDRESS, "one");
        assertSame(TestHandler.ONE, oneHandler);

        OperationStepHandler twoHandler = child.getOperationHandler(PathAddress.EMPTY_ADDRESS, "two");
        assertSame(TestHandler.TWO, twoHandler);

        oneHandler = rootRegistration.getOperationHandler(childAddress, "one");
        assertSame(TestHandler.ONE, oneHandler);

        twoHandler = rootRegistration.getOperationHandler(childAddress, "two");
        assertSame(TestHandler.TWO, twoHandler);

        oneHandler = rootRegistration.getOperationHandler(fullChildAddress, "one");
        assertSame(TestHandler.ONE, oneHandler);

        twoHandler = rootRegistration.getOperationHandler(fullChildAddress, "two");
        assertSame(TestHandler.TWO, twoHandler);

        oneHandler = rootRegistration.getOperationHandler(PathAddress.EMPTY_ADDRESS, "one");
        assertNull(oneHandler);

        twoHandler = rootRegistration.getOperationHandler(PathAddress.EMPTY_ADDRESS, "two");
        assertNull(twoHandler);
    }

    @Test
    public void testHandlerInheritance() throws Exception {

        rootRegistration.registerOperationHandler(getOpDef("one", OperationEntry.Flag.READ_ONLY), TestHandler.PARENT, true);
        rootRegistration.registerOperationHandler(getOpDef("two", OperationEntry.Flag.READ_ONLY), TestHandler.PARENT, true);
        rootRegistration.registerOperationHandler(getOpDef("three", OperationEntry.Flag.READ_ONLY), TestHandler.PARENT, true);
        rootRegistration.registerOperationHandler(getOpDef("four", OperationEntry.Flag.READ_ONLY), TestHandler.PARENT, false);

        ManagementResourceRegistration child = rootRegistration.registerSubModel(new SimpleResourceDefinition(childElement, NonResolvingResourceDescriptionResolver.INSTANCE));
        child.registerOperationHandler(getOpDef("one"), TestHandler.CHILD, true);
        child.registerOperationHandler(getOpDef("two", OperationEntry.Flag.MASTER_HOST_CONTROLLER_ONLY), TestHandler.CHILD, true);

        ManagementResourceRegistration grandchild = child.registerSubModel(new SimpleResourceDefinition(grandchildElement, NonResolvingResourceDescriptionResolver.INSTANCE));

        OperationStepHandler oneHandler = child.getOperationHandler(PathAddress.EMPTY_ADDRESS, "one");
        assertSame(TestHandler.CHILD, oneHandler);

        OperationStepHandler twoHandler = child.getOperationHandler(PathAddress.EMPTY_ADDRESS, "two");
        assertSame(TestHandler.CHILD, twoHandler);

        OperationStepHandler threeHandler = child.getOperationHandler(PathAddress.EMPTY_ADDRESS, "three");
        assertSame(TestHandler.PARENT, threeHandler);

        oneHandler = rootRegistration.getOperationHandler(childAddress, "one");
        assertSame(TestHandler.CHILD, oneHandler);

        twoHandler = rootRegistration.getOperationHandler(childAddress, "two");
        assertSame(TestHandler.CHILD, twoHandler);

        threeHandler = child.getOperationHandler(PathAddress.EMPTY_ADDRESS, "three");
        assertSame(TestHandler.PARENT, threeHandler);

        OperationStepHandler fourHandler = child.getOperationHandler(PathAddress.EMPTY_ADDRESS, "four");
        assertNull(fourHandler);

        fourHandler = rootRegistration.getOperationHandler(childAddress, "four");
        assertNull(fourHandler);

        // Sanity check
        fourHandler = rootRegistration.getOperationHandler(PathAddress.EMPTY_ADDRESS, "four");
        assertSame(TestHandler.PARENT, fourHandler);

        oneHandler = rootRegistration.getOperationHandler(grandchildAddress, "one");
        assertSame(TestHandler.CHILD, oneHandler);

        oneHandler = rootRegistration.getOperationHandler(fullGrandchildAddress, "one");
        assertSame(TestHandler.CHILD, oneHandler);

        oneHandler = grandchild.getOperationHandler(PathAddress.EMPTY_ADDRESS, "one");
        assertSame(TestHandler.CHILD, oneHandler);

        twoHandler = rootRegistration.getOperationHandler(grandchildAddress, "two");
        assertSame(TestHandler.CHILD, twoHandler);

        twoHandler = rootRegistration.getOperationHandler(fullGrandchildAddress, "two");
        assertSame(TestHandler.CHILD, twoHandler);

        twoHandler = grandchild.getOperationHandler(PathAddress.EMPTY_ADDRESS, "two");
        assertSame(TestHandler.CHILD, twoHandler);

        threeHandler = rootRegistration.getOperationHandler(grandchildAddress, "three");
        assertSame(TestHandler.PARENT, threeHandler);

        threeHandler = rootRegistration.getOperationHandler(fullGrandchildAddress, "three");
        assertSame(TestHandler.PARENT, threeHandler);

        threeHandler = grandchild.getOperationHandler(PathAddress.EMPTY_ADDRESS, "three");
        assertSame(TestHandler.PARENT, threeHandler);
    }

    @Test
    public void testFlagsOnRootResource() throws Exception {

        rootRegistration.registerOperationHandler(getOpDef("one"), TestHandler.INSTANCE);
        rootRegistration.registerOperationHandler(getOpDef("two", OperationEntry.Flag.READ_ONLY), TestHandler.INSTANCE, false);

        Set<OperationEntry.Flag> oneFlags = rootRegistration.getOperationFlags(PathAddress.EMPTY_ADDRESS, "one");
        assertNotNull(oneFlags);
        assertEquals(0, oneFlags.size());

        Set<OperationEntry.Flag> twoFlags = rootRegistration.getOperationFlags(PathAddress.EMPTY_ADDRESS, "two");
        assertNotNull(twoFlags);
        assertEquals(1, twoFlags.size());
    }

    @Test
    public void testFlagsOnChildResource() throws Exception {

        ManagementResourceRegistration child = rootRegistration.registerSubModel(new SimpleResourceDefinition(childElement, NonResolvingResourceDescriptionResolver.INSTANCE));
        child.registerOperationHandler(getOpDef("one"), TestHandler.INSTANCE);
        child.registerOperationHandler(getOpDef("two", OperationEntry.Flag.READ_ONLY), TestHandler.INSTANCE, false);

        Set<OperationEntry.Flag> oneFlags = child.getOperationFlags(PathAddress.EMPTY_ADDRESS, "one");
        assertNotNull(oneFlags);
        assertEquals(0, oneFlags.size());

        Set<OperationEntry.Flag> twoFlags = child.getOperationFlags(PathAddress.EMPTY_ADDRESS, "two");
        assertNotNull(twoFlags);
        assertEquals(1, twoFlags.size());

        oneFlags = rootRegistration.getOperationFlags(childAddress, "one");
        assertNotNull(oneFlags);
        assertEquals(0, oneFlags.size());

        twoFlags = rootRegistration.getOperationFlags(childAddress, "two");
        assertNotNull(twoFlags);
        assertEquals(1, twoFlags.size());

        oneFlags = rootRegistration.getOperationFlags(fullChildAddress, "one");
        assertNotNull(oneFlags);
        assertEquals(0, oneFlags.size());

        twoFlags = rootRegistration.getOperationFlags(fullChildAddress, "two");
        assertNotNull(twoFlags);
        assertEquals(1, twoFlags.size());
    }

    @Test
    public void testFlagsInheritance() throws Exception {

        rootRegistration.registerOperationHandler(getOpDef("one", OperationEntry.Flag.READ_ONLY), TestHandler.INSTANCE, true);
        rootRegistration.registerOperationHandler(getOpDef("two", OperationEntry.Flag.READ_ONLY), TestHandler.INSTANCE, true);
        rootRegistration.registerOperationHandler(getOpDef("three", OperationEntry.Flag.READ_ONLY), TestHandler.INSTANCE, true);
        rootRegistration.registerOperationHandler(getOpDef("four", OperationEntry.Flag.READ_ONLY), TestHandler.INSTANCE, false);

        ManagementResourceRegistration child = rootRegistration.registerSubModel(new SimpleResourceDefinition(childElement, NonResolvingResourceDescriptionResolver.INSTANCE));
        child.registerOperationHandler(getOpDef("one"), TestHandler.INSTANCE, true);
        child.registerOperationHandler(getOpDef("two", OperationEntry.Flag.MASTER_HOST_CONTROLLER_ONLY), TestHandler.INSTANCE, true);

        ManagementResourceRegistration grandchild = child.registerSubModel(new SimpleResourceDefinition(grandchildElement, NonResolvingResourceDescriptionResolver.INSTANCE));

        Set<OperationEntry.Flag> oneFlags = child.getOperationFlags(PathAddress.EMPTY_ADDRESS, "one");
        assertNotNull(oneFlags);
        assertEquals(0, oneFlags.size());

        Set<OperationEntry.Flag> twoFlags = child.getOperationFlags(PathAddress.EMPTY_ADDRESS, "two");
        assertNotNull(twoFlags);
        assertEquals(1, twoFlags.size());
        assertTrue(twoFlags.contains(OperationEntry.Flag.MASTER_HOST_CONTROLLER_ONLY));

        Set<OperationEntry.Flag> threeFlags = child.getOperationFlags(PathAddress.EMPTY_ADDRESS, "three");
        assertNotNull(threeFlags);
        assertEquals(1, threeFlags.size());
        assertTrue(threeFlags.contains(OperationEntry.Flag.READ_ONLY));

        oneFlags = rootRegistration.getOperationFlags(childAddress, "one");
        assertNotNull(oneFlags);
        assertEquals(0, oneFlags.size());

        twoFlags = rootRegistration.getOperationFlags(childAddress, "two");
        assertNotNull(twoFlags);
        assertEquals(1, twoFlags.size());
        assertTrue(twoFlags.contains(OperationEntry.Flag.MASTER_HOST_CONTROLLER_ONLY));

        threeFlags = child.getOperationFlags(PathAddress.EMPTY_ADDRESS, "three");
        assertNotNull(threeFlags);
        assertEquals(1, threeFlags.size());
        assertTrue(threeFlags.contains(OperationEntry.Flag.READ_ONLY));

        Set<OperationEntry.Flag> fourFlags = child.getOperationFlags(PathAddress.EMPTY_ADDRESS, "four");
        assertNull(fourFlags);

        fourFlags = rootRegistration.getOperationFlags(childAddress, "four");
        assertNull(fourFlags);

        // Sanity check
        fourFlags = rootRegistration.getOperationFlags(PathAddress.EMPTY_ADDRESS, "four");
        assertNotNull(fourFlags);
        assertEquals(1, fourFlags.size());
        assertTrue(fourFlags.contains(OperationEntry.Flag.READ_ONLY));

        oneFlags = rootRegistration.getOperationFlags(grandchildAddress, "one");
        assertNotNull(oneFlags);
        assertEquals(0, oneFlags.size());

        oneFlags = rootRegistration.getOperationFlags(fullGrandchildAddress, "one");
        assertNotNull(oneFlags);
        assertEquals(0, oneFlags.size());

        oneFlags = grandchild.getOperationFlags(PathAddress.EMPTY_ADDRESS, "one");
        assertNotNull(oneFlags);
        assertEquals(0, oneFlags.size());

        twoFlags = rootRegistration.getOperationFlags(grandchildAddress, "two");
        assertNotNull(twoFlags);
        assertEquals(1, twoFlags.size());
        assertTrue(twoFlags.contains(OperationEntry.Flag.MASTER_HOST_CONTROLLER_ONLY));

        twoFlags = rootRegistration.getOperationFlags(fullGrandchildAddress, "two");
        assertNotNull(twoFlags);
        assertEquals(1, twoFlags.size());
        assertTrue(twoFlags.contains(OperationEntry.Flag.MASTER_HOST_CONTROLLER_ONLY));

        twoFlags = grandchild.getOperationFlags(PathAddress.EMPTY_ADDRESS, "two");
        assertNotNull(twoFlags);
        assertEquals(1, twoFlags.size());
        assertTrue(twoFlags.contains(OperationEntry.Flag.MASTER_HOST_CONTROLLER_ONLY));

        threeFlags = rootRegistration.getOperationFlags(grandchildAddress, "three");
        assertNotNull(threeFlags);
        assertEquals(1, threeFlags.size());
        assertTrue(threeFlags.contains(OperationEntry.Flag.READ_ONLY));

        threeFlags = rootRegistration.getOperationFlags(fullGrandchildAddress, "three");
        assertNotNull(threeFlags);
        assertEquals(1, threeFlags.size());
        assertTrue(threeFlags.contains(OperationEntry.Flag.READ_ONLY));

        threeFlags = grandchild.getOperationFlags(PathAddress.EMPTY_ADDRESS, "three");
        assertNotNull(threeFlags);
        assertEquals(1, threeFlags.size());
        assertTrue(threeFlags.contains(OperationEntry.Flag.READ_ONLY));
    }

    @Test
    public void testInheritedAccessConstraints() {

        ResourceDefinition rootRd = new SimpleResourceDefinition(new Parameters(ResourceRegistration.root(), NonResolvingResourceDescriptionResolver.INSTANCE)
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.EXTENSIONS, ApplicationTypeAccessConstraintDefinition.DEPLOYMENT));
        ManagementResourceRegistration root = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(rootRd);

        List<AccessConstraintDefinition> acds = root.getAccessConstraints();
        assertEquals(2, acds.size());
        assertTrue(acds.contains(SensitiveTargetAccessConstraintDefinition.EXTENSIONS));
        assertTrue(acds.contains(ApplicationTypeAccessConstraintDefinition.DEPLOYMENT));

        ResourceDefinition childRd = new SimpleResourceDefinition(
                new Parameters(PathElement.pathElement("child"), NonResolvingResourceDescriptionResolver.INSTANCE)
                    .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SECURITY_DOMAIN, ApplicationTypeAccessConstraintDefinition.DEPLOYMENT));
        ManagementResourceRegistration child = root.registerSubModel(childRd);
        acds = child.getAccessConstraints();
        assertEquals(4, acds.size());
        assertTrue(acds.contains(SensitiveTargetAccessConstraintDefinition.EXTENSIONS));
        assertTrue(acds.contains(SensitiveTargetAccessConstraintDefinition.SECURITY_DOMAIN));
        assertTrue(acds.contains(ApplicationTypeAccessConstraintDefinition.DEPLOYMENT));
    }

    @Test
    public void testFilteredOperationParameters() {
        OperationDefinition unstableOperation = SimpleOperationDefinitionBuilder.of("unstable-operation", NonResolvingResourceDescriptionResolver.INSTANCE).setStability(Stability.PREVIEW).build();
        OperationStepHandler handler = (context, model) -> {};
        this.rootRegistration.registerOperationHandler(unstableOperation, handler);
        assertNull(this.rootRegistration.getOperationEntry(PathAddress.EMPTY_ADDRESS, "unstable-operation"));

        AttributeDefinition stableParameter = new SimpleAttributeDefinitionBuilder("stable", ModelType.STRING).build();
        AttributeDefinition unstableParameter = new SimpleAttributeDefinitionBuilder("unstable", ModelType.STRING).setStability(Stability.EXPERIMENTAL).build();
        AttributeDefinition stableReplyParameter = new SimpleAttributeDefinitionBuilder("stable-reply", ModelType.STRING).build();
        AttributeDefinition unstableReplyParameter = new SimpleAttributeDefinitionBuilder("unstable-reply", ModelType.STRING).setStability(Stability.EXPERIMENTAL).build();
        OperationDefinition stableOperation = SimpleOperationDefinitionBuilder.of("stable-operation", NonResolvingResourceDescriptionResolver.INSTANCE)
                .setParameters(stableParameter, unstableParameter)
                .setReplyParameters(stableReplyParameter, unstableReplyParameter)
                .build();
        this.rootRegistration.registerOperationHandler(stableOperation, handler);

        OperationEntry entry = this.rootRegistration.getOperationEntry(PathAddress.EMPTY_ADDRESS, "stable-operation");
        assertNotNull(entry);
        assertSame(handler, entry.getOperationHandler());

        OperationDefinition registeredOperation = entry.getOperationDefinition();
        assertNotSame(stableOperation, registeredOperation);
        assertEquals(1, registeredOperation.getParameters().length);
        assertSame(stableParameter, registeredOperation.getParameters()[0]);
        assertEquals(1, registeredOperation.getReplyParameters().length);
        assertSame(stableReplyParameter, registeredOperation.getReplyParameters()[0]);
    }

    private static class TestHandler implements OperationStepHandler {

        private static final TestHandler INSTANCE = new TestHandler();

        private static final TestHandler ONE = new TestHandler();
        private static final TestHandler TWO = new TestHandler();

        private static final TestHandler PARENT = new TestHandler();
        private static final TestHandler CHILD = new TestHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    static OperationDefinition getOpDef(String name, OperationEntry.Flag... flags) {
        return new SimpleOperationDefinitionBuilder(name, NonResolvingResourceDescriptionResolver.INSTANCE)
                .withFlags(flags)
                .build();
    }


}
