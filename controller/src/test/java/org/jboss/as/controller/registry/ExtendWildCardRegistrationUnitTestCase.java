/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.registry;

import static org.jboss.as.controller.registry.CoreManagementResourceRegistrationUnitTestCase.getOpDef;
import static org.junit.Assert.*;

import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test for AS7-2930 functionality.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ExtendWildCardRegistrationUnitTestCase {

    private static final PathElement parentWild = PathElement.pathElement("parent");
    private static final PathElement parentExt = PathElement.pathElement("parent", "ext");

    private static final PathElement childWild = PathElement.pathElement("child");
    private static final PathElement childExt = PathElement.pathElement("child", "ext");
    private static final PathElement childWildExt = PathElement.pathElement("child", "wild-ext");

    private static final OperationStepHandler parentWildAttr = new TestOSH();
    private static final OperationStepHandler parentExtAttr = new TestOSH();
    private static final OperationStepHandler childWildAttr = new TestOSH();
    private static final OperationStepHandler childExtAttr = new TestOSH();
    private static final OperationStepHandler childWildExtAttr = new TestOSH();
    private static final OperationStepHandler parentWildOverrideAttr = new TestOSH();
    private static final OperationStepHandler parentExtOverrideAttr = new TestOSH();
    private static final OperationStepHandler childWildOverrideAttr = new TestOSH();
    private static final OperationStepHandler childExtOverrideAttr = new TestOSH();

    private static final OperationStepHandler parentWildOp = new TestOSH();
    private static final OperationStepHandler parentExtOp = new TestOSH();
    private static final OperationStepHandler childWildOp = new TestOSH();
    private static final OperationStepHandler childExtOp = new TestOSH();
    private static final OperationStepHandler childWildExtOp = new TestOSH();
    private static final OperationStepHandler parentWildOverrideOp = new TestOSH();
    private static final OperationStepHandler parentExtOverrideOp = new TestOSH();
    private static final OperationStepHandler childWildOverrideOp = new TestOSH();
    private static final OperationStepHandler childExtOverrideOp = new TestOSH();

    private static ManagementResourceRegistration registration;
    private static ManagementResourceRegistration parentWildReg;
    private static ManagementResourceRegistration parentExtReg;
    private static ManagementResourceRegistration childWildReg;
    private static ManagementResourceRegistration childWildExtReg;
    private static ManagementResourceRegistration childExtReg;

    private static final SimpleAttributeDefinition wildAttr = new SimpleAttributeDefinitionBuilder("wildAttr", ModelType.STRING, true).build();
    private static final SimpleAttributeDefinition overrideAttr = new SimpleAttributeDefinitionBuilder("overrideAttr", ModelType.STRING, true).build();
    private static final SimpleAttributeDefinition extAttr = new SimpleAttributeDefinitionBuilder("extAttr", ModelType.STRING, true).build();
    private static final SimpleAttributeDefinition wildExtAttr = new SimpleAttributeDefinitionBuilder("wildExtAttr", ModelType.STRING, true).build();

    @BeforeClass
    public static void setup() {
        registration = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(new SimpleResourceDefinition(PathElement.pathElement("root","root"), NonResolvingResourceDescriptionResolver.INSTANCE));

        parentWildReg = registration.registerSubModel(new SimpleResourceDefinition(parentWild, NonResolvingResourceDescriptionResolver.INSTANCE));
        parentWildReg.registerReadOnlyAttribute(wildAttr, parentWildAttr);
        parentWildReg.registerOperationHandler(getOpDef("wildOp"), parentWildOp);
        parentWildReg.registerReadOnlyAttribute(overrideAttr, parentWildOverrideAttr);
        parentWildReg.registerOperationHandler(getOpDef("overrideOp"), parentWildOverrideOp);

        parentExtReg = registration.registerSubModel(new SimpleResourceDefinition(parentExt, NonResolvingResourceDescriptionResolver.INSTANCE));
        parentExtReg.registerReadOnlyAttribute(extAttr, parentExtAttr);
        parentExtReg.registerOperationHandler(getOpDef("extOp"), parentExtOp);
        parentExtReg.registerReadOnlyAttribute(overrideAttr, parentExtOverrideAttr);
        parentExtReg.registerOperationHandler(getOpDef("overrideOp"), parentExtOverrideOp);

        childWildReg = parentWildReg.registerSubModel(new SimpleResourceDefinition(childWild, NonResolvingResourceDescriptionResolver.INSTANCE));
        childWildReg.registerReadOnlyAttribute(wildAttr, childWildAttr);
        childWildReg.registerOperationHandler(getOpDef("wildOp"), childWildOp);
        childWildReg.registerReadOnlyAttribute(overrideAttr, childWildOverrideAttr);
        childWildReg.registerOperationHandler(getOpDef("overrideOp"), childWildOverrideOp);

        childWildExtReg = parentWildReg.registerSubModel(new SimpleResourceDefinition(childWildExt, NonResolvingResourceDescriptionResolver.INSTANCE));
        childWildExtReg.registerReadOnlyAttribute(wildExtAttr, childWildExtAttr);
        childWildExtReg.registerOperationHandler(getOpDef("wildExtOp"), childWildExtOp);

        childExtReg = parentExtReg.registerSubModel(new SimpleResourceDefinition(childExt, NonResolvingResourceDescriptionResolver.INSTANCE));
        childExtReg.registerReadOnlyAttribute(extAttr, childExtAttr);
        childExtReg.registerOperationHandler(getOpDef("extOp"), childExtOp);
        childExtReg.registerReadOnlyAttribute(overrideAttr, childExtOverrideAttr);
        childExtReg.registerOperationHandler(getOpDef("overrideOp"), childExtOverrideOp);
    }

    @AfterClass
    public static void tearDown() {
        registration = null;
    }

    @Test
    public void testParentWildcardAttribute() throws Exception {
        assertSame(parentWildAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentWild), "wildAttr").getReadHandler());
        assertSame(parentWildAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentExt), "wildAttr").getReadHandler());
        assertSame(parentWildAttr, parentWildReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "wildAttr").getReadHandler());
        assertSame(parentWildAttr, parentExtReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "wildAttr").getReadHandler());
    }

    @Test
    public void testParentExtensionAttribute() throws Exception {
        assertNull(registration.getAttributeAccess(PathAddress.pathAddress(parentWild), "extAttr"));
        assertSame(parentExtAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentExt), "extAttr").getReadHandler());
        assertNull(parentWildReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "extAttr"));
        assertSame(parentExtAttr, parentExtReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "extAttr").getReadHandler());
    }

    @Test
    public void testParentOverrideAttribute() throws Exception {
        assertSame(parentWildOverrideAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentWild), "overrideAttr").getReadHandler());
        assertSame(parentExtOverrideAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentExt), "overrideAttr").getReadHandler());
        assertSame(parentWildOverrideAttr, parentWildReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "overrideAttr").getReadHandler());
        assertSame(parentExtOverrideAttr, parentExtReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "overrideAttr").getReadHandler());
    }

    @Test
    public void testParentMissingAttribute() throws Exception {
        assertNull(registration.getAttributeAccess(PathAddress.pathAddress(parentWild), "na"));
        assertNull(registration.getAttributeAccess(PathAddress.pathAddress(parentExt), "na"));
        assertNull(parentWildReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "na"));
        assertNull(parentExtReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "na"));

    }

    @Test
    public void testParentWildcardOperation() throws Exception {
        assertSame(parentWildOp, registration.getOperationHandler(PathAddress.pathAddress(parentWild), "wildOp"));
        assertSame(parentWildOp, registration.getOperationHandler(PathAddress.pathAddress(parentExt), "wildOp"));
        assertSame(parentWildOp, parentWildReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "wildOp"));
        assertSame(parentWildOp, parentExtReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "wildOp"));
    }

    @Test
    public void testParentExtensionOperation() throws Exception {
        assertNull(registration.getOperationHandler(PathAddress.pathAddress(parentWild), "extOp"));
        assertSame(parentExtOp, registration.getOperationHandler(PathAddress.pathAddress(parentExt), "extOp"));
        assertNull(parentWildReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "extOp"));
        assertSame(parentExtOp, parentExtReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "extOp"));
    }

    @Test
    public void testParentOverrideOperation() throws Exception {
        assertSame(parentWildOverrideOp, registration.getOperationHandler(PathAddress.pathAddress(parentWild), "overrideOp"));
        assertSame(parentExtOverrideOp, registration.getOperationHandler(PathAddress.pathAddress(parentExt), "overrideOp"));
        assertSame(parentWildOverrideOp, parentWildReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "overrideOp"));
        assertSame(parentExtOverrideOp, parentExtReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "overrideOp"));
    }

    @Test
    public void testParentMissingOperation() throws Exception {
        assertNull(registration.getOperationHandler(PathAddress.pathAddress(parentWild), "na"));
        assertNull(registration.getOperationHandler(PathAddress.pathAddress(parentExt), "na"));
        assertNull(parentWildReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "na"));
        assertNull(parentExtReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "na"));
    }

    @Test
    public void testChildWildcardAttribute() throws Exception {
        assertSame(childWildAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentWild, childWild), "wildAttr").getReadHandler());
        assertSame(childWildAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentWild, childExt), "wildAttr").getReadHandler());
        assertSame(childWildAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentWild, childWildExt), "wildAttr").getReadHandler());
        assertSame(childWildAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentExt, childWild), "wildAttr").getReadHandler());
        assertSame(childWildAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentExt, childExt), "wildAttr").getReadHandler());
        assertSame(childWildAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentExt, childWildExt), "wildAttr").getReadHandler());

        assertSame(childWildAttr, parentWildReg.getAttributeAccess(PathAddress.pathAddress(childWild), "wildAttr").getReadHandler());
        assertSame(childWildAttr, parentExtReg.getAttributeAccess(PathAddress.pathAddress(childWild), "wildAttr").getReadHandler());
        assertSame(childWildAttr, parentWildReg.getAttributeAccess(PathAddress.pathAddress(childExt), "wildAttr").getReadHandler());
        assertSame(childWildAttr, parentExtReg.getAttributeAccess(PathAddress.pathAddress(childExt), "wildAttr").getReadHandler());
        assertSame(childWildAttr, parentWildReg.getAttributeAccess(PathAddress.pathAddress(childWildExt), "wildAttr").getReadHandler());
        assertSame(childWildAttr, parentExtReg.getAttributeAccess(PathAddress.pathAddress(childWildExt), "wildAttr").getReadHandler());

        assertSame(childWildAttr, childWildReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "wildAttr").getReadHandler());
        assertSame(childWildAttr, childExtReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "wildAttr").getReadHandler());
        assertSame(childWildAttr, childWildExtReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "wildAttr").getReadHandler());
    }

    @Test
    public void testChildExtensionAttribute() throws Exception {
        assertNull(registration.getAttributeAccess(PathAddress.pathAddress(parentWild, childWild), "extAttr"));
        assertNull(registration.getAttributeAccess(PathAddress.pathAddress(parentExt, childWild), "extAttr"));
        assertNull(registration.getAttributeAccess(PathAddress.pathAddress(parentWild, childExt), "extAttr"));
        assertNull(registration.getAttributeAccess(PathAddress.pathAddress(parentWild, childWildExt), "extAttr"));
        assertSame(childExtAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentExt, childExt), "extAttr").getReadHandler());

        assertNull(parentWildReg.getAttributeAccess(PathAddress.pathAddress(childWild), "extAttr"));
        assertNull(parentExtReg.getAttributeAccess(PathAddress.pathAddress(childWild), "extAttr"));
        assertNull(parentWildReg.getAttributeAccess(PathAddress.pathAddress(childExt), "extAttr"));
        assertNull(parentWildReg.getAttributeAccess(PathAddress.pathAddress(childWildExt), "extAttr"));
        assertSame(childExtAttr, parentExtReg.getAttributeAccess(PathAddress.pathAddress(childExt), "extAttr").getReadHandler());

        assertNull(childWildReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "extAttr"));
        assertNull(childWildExtReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "extAttr"));
        assertSame(childExtAttr, childExtReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "extAttr").getReadHandler());
    }

    @Test
    public void testChildOverrideAttribute() throws Exception {
        assertSame(childWildOverrideAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentWild, childWild), "overrideAttr").getReadHandler());
        assertSame(childWildOverrideAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentExt, childWild), "overrideAttr").getReadHandler());
        assertSame(childWildOverrideAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentWild, childWildExt), "overrideAttr").getReadHandler());
        assertSame(childWildOverrideAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentExt, childWildExt), "overrideAttr").getReadHandler());
        assertSame(childWildOverrideAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentWild, childExt), "overrideAttr").getReadHandler());
        assertSame(childExtOverrideAttr, registration.getAttributeAccess(PathAddress.pathAddress(parentExt, childExt), "overrideAttr").getReadHandler());

        assertSame(childWildOverrideAttr, parentWildReg.getAttributeAccess(PathAddress.pathAddress(childWild), "overrideAttr").getReadHandler());
        assertSame(childWildOverrideAttr, parentExtReg.getAttributeAccess(PathAddress.pathAddress(childWild), "overrideAttr").getReadHandler());
        assertSame(childWildOverrideAttr, parentWildReg.getAttributeAccess(PathAddress.pathAddress(childWildExt), "overrideAttr").getReadHandler());
        assertSame(childWildOverrideAttr, parentExtReg.getAttributeAccess(PathAddress.pathAddress(childWildExt), "overrideAttr").getReadHandler());
        assertSame(childWildOverrideAttr, parentWildReg.getAttributeAccess(PathAddress.pathAddress(childExt), "overrideAttr").getReadHandler());
        assertSame(childExtOverrideAttr, parentExtReg.getAttributeAccess(PathAddress.pathAddress(childExt), "overrideAttr").getReadHandler());

        assertSame(childWildOverrideAttr, childWildReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "overrideAttr").getReadHandler());
        assertSame(childWildOverrideAttr, childWildExtReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "overrideAttr").getReadHandler());
        assertSame(childExtOverrideAttr, childExtReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "overrideAttr").getReadHandler());
    }

    @Test
    public void testChildMissingAttribute() throws Exception {
        assertNull(registration.getAttributeAccess(PathAddress.pathAddress(parentWild, childWild), "na"));
        assertNull(registration.getAttributeAccess(PathAddress.pathAddress(parentExt, childWild), "na"));
        assertNull(registration.getAttributeAccess(PathAddress.pathAddress(parentWild, childExt), "na"));
        assertNull(registration.getAttributeAccess(PathAddress.pathAddress(parentExt, childExt), "na"));

        assertNull(parentWildReg.getAttributeAccess(PathAddress.pathAddress(childWild), "na"));
        assertNull(parentExtReg.getAttributeAccess(PathAddress.pathAddress(childWild), "na"));
        assertNull(parentWildReg.getAttributeAccess(PathAddress.pathAddress(childExt), "na"));
        assertNull(parentExtReg.getAttributeAccess(PathAddress.pathAddress(childExt), "na"));

        assertNull(childWildReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "na"));
        assertNull(childExtReg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, "na"));
    }

    @Test
    public void testChildWildcardOperation() throws Exception {
        assertSame(childWildOp, registration.getOperationHandler(PathAddress.pathAddress(parentWild, childWild), "wildOp"));
        assertSame(childWildOp, registration.getOperationHandler(PathAddress.pathAddress(parentExt, childWild), "wildOp"));
        assertSame(childWildOp, registration.getOperationHandler(PathAddress.pathAddress(parentWild, childExt), "wildOp"));
        assertSame(childWildOp, registration.getOperationHandler(PathAddress.pathAddress(parentExt, childExt), "wildOp"));

        assertSame(childWildOp, parentWildReg.getOperationHandler(PathAddress.pathAddress(childWild), "wildOp"));
        assertSame(childWildOp, parentExtReg.getOperationHandler(PathAddress.pathAddress(childWild), "wildOp"));
        assertSame(childWildOp, parentWildReg.getOperationHandler(PathAddress.pathAddress(childExt), "wildOp"));
        assertSame(childWildOp, parentExtReg.getOperationHandler(PathAddress.pathAddress(childExt), "wildOp"));

        assertSame(childWildOp, childWildReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "wildOp"));
        assertSame(childWildOp, childExtReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "wildOp"));
    }

    @Test
    public void testChildExtensionOperation() throws Exception {
        assertNull(registration.getOperationHandler(PathAddress.pathAddress(parentWild, childWild), "extOp"));
        assertNull(registration.getOperationHandler(PathAddress.pathAddress(parentExt, childWild), "extOp"));
        assertNull(registration.getOperationHandler(PathAddress.pathAddress(parentWild, childExt), "extOp"));
        assertSame(childExtOp, registration.getOperationHandler(PathAddress.pathAddress(parentExt, childExt), "extOp"));

        assertNull(parentWildReg.getOperationHandler(PathAddress.pathAddress(childWild), "extOp"));
        assertNull(parentExtReg.getOperationHandler(PathAddress.pathAddress(childWild), "extOp"));
        assertNull(parentWildReg.getOperationHandler(PathAddress.pathAddress(childExt), "extOp"));
        assertSame(childExtOp, parentExtReg.getOperationHandler(PathAddress.pathAddress(childExt), "extOp"));

        assertNull(childWildReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "extOp"));
        assertSame(childExtOp, childExtReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "extOp"));
    }

    @Test
    public void testChildOverrideOperation() throws Exception {
        assertSame(childWildOverrideOp, registration.getOperationHandler(PathAddress.pathAddress(parentWild, childWild), "overrideOp"));
        assertSame(childWildOverrideOp, registration.getOperationHandler(PathAddress.pathAddress(parentExt, childWild), "overrideOp"));
        assertSame(childWildOverrideOp, registration.getOperationHandler(PathAddress.pathAddress(parentWild, childExt), "overrideOp"));
        assertSame(childExtOverrideOp, registration.getOperationHandler(PathAddress.pathAddress(parentExt, childExt), "overrideOp"));

        assertSame(childWildOverrideOp, parentWildReg.getOperationHandler(PathAddress.pathAddress(childWild), "overrideOp"));
        assertSame(childWildOverrideOp, parentExtReg.getOperationHandler(PathAddress.pathAddress(childWild), "overrideOp"));
        assertSame(childWildOverrideOp, parentWildReg.getOperationHandler(PathAddress.pathAddress(childExt), "overrideOp"));
        assertSame(childExtOverrideOp, parentExtReg.getOperationHandler(PathAddress.pathAddress(childExt), "overrideOp"));

        assertSame(childWildOverrideOp, childWildReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "overrideOp"));
        assertSame(childExtOverrideOp, childExtReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "overrideOp"));

    }

    @Test
    public void testChildMissingOperation() throws Exception {
        assertNull(registration.getOperationHandler(PathAddress.pathAddress(parentWild, childWild), "na"));
        assertNull(registration.getOperationHandler(PathAddress.pathAddress(parentExt, childWild), "na"));
        assertNull(registration.getOperationHandler(PathAddress.pathAddress(parentWild, childExt), "na"));
        assertNull(registration.getOperationHandler(PathAddress.pathAddress(parentExt, childExt), "na"));

        assertNull(parentWildReg.getOperationHandler(PathAddress.pathAddress(childWild), "na"));
        assertNull(parentExtReg.getOperationHandler(PathAddress.pathAddress(childWild), "na"));
        assertNull(parentWildReg.getOperationHandler(PathAddress.pathAddress(childExt), "na"));
        assertNull(parentExtReg.getOperationHandler(PathAddress.pathAddress(childExt), "na"));

        assertNull(childWildReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "na"));
        assertNull(childExtReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, "na"));
    }

    @Test
    public void testDuplicateSubModel() {
        try {
            parentExtReg.registerSubModel(new SimpleResourceDefinition(childWildExt, NonResolvingResourceDescriptionResolver.INSTANCE));
            fail("Duplicate child not rejected");
        } catch (Exception good) {
            //
        }

        try {
            parentExtReg.registerSubModel(new SimpleResourceDefinition( childWild, NonResolvingResourceDescriptionResolver.INSTANCE));
            fail("Duplicate child not rejected");
        } catch (Exception good) {
            //
        }

        try {
            parentWildReg.registerSubModel(new SimpleResourceDefinition(childWild, NonResolvingResourceDescriptionResolver.INSTANCE));
            fail("Duplicate child not rejected");
        } catch (Exception good) {
            //
        }

    }

    @Test
    public void testSubmodelRemoval() {
        PathElement grandchildWild = PathElement.pathElement("grandchild");
        PathElement grandchildExt = PathElement.pathElement("grandchild", "ext");
        PathElement anotherGranchild = PathElement.pathElement("another", "grandchild");

        childWildReg.registerSubModel(new SimpleResourceDefinition(grandchildWild, NonResolvingResourceDescriptionResolver.INSTANCE));
        childWildExtReg.registerSubModel(new SimpleResourceDefinition(grandchildExt, NonResolvingResourceDescriptionResolver.INSTANCE));
        childWildExtReg.registerSubModel(new SimpleResourceDefinition(anotherGranchild, NonResolvingResourceDescriptionResolver.INSTANCE));

        // Confirm setup.

        // Definition of /parent=*/child=* only has 1 child type -- granchild=*. The fact that
        // a concrete resource /parent=a/child=wild-ext might allow another=grandchild and also
        // an override form grandchild=ext doesn't change the fact that /parent=*/child=* only has 1 child type.
        // Remember, when it comes to MRRs, '*' doesn't mean "match" everything; it identifies a specific MRR
        // and the children are what are associated with that specific MRR
        Set<PathElement> grandchildren = childWildReg.getChildAddresses(PathAddress.EMPTY_ADDRESS);
        assertEquals(grandchildren.toString(), 1, grandchildren.size());
        assertTrue(grandchildren.toString(), grandchildren.contains(grandchildWild));
        // However /parent=a/child=wild-ext does allow children another=grandchild,
        // grandchild=ext (whose API presumably includes overrides) and
        // grandchild=<* != ext> (whose API does not include overrides). The child=wild-ext
        // is an override of child=* and that means it supports child=*'s API, including granchild=*
        grandchildren = childWildExtReg.getChildAddresses(PathAddress.EMPTY_ADDRESS);
        assertEquals(grandchildren.toString(), 3, grandchildren.size());
        assertTrue(grandchildren.toString(), grandchildren.contains(grandchildWild));
        assertTrue(grandchildren.toString(), grandchildren.contains(grandchildExt));
        assertTrue(grandchildren.toString(), grandchildren.contains(anotherGranchild));

        // Now do some removals

        // 1) A removal via the override does not remove from the wildcard

        childWildExtReg.unregisterSubModel(grandchildWild);
        grandchildren = childWildExtReg.getChildAddresses(PathAddress.EMPTY_ADDRESS);
        assertEquals(grandchildren.toString(), 3, grandchildren.size());
        assertTrue(grandchildren.toString(), grandchildren.contains(grandchildWild));
        assertTrue(grandchildren.toString(), grandchildren.contains(grandchildWild));
        assertTrue(grandchildren.toString(), grandchildren.contains(anotherGranchild));
        grandchildren = childWildReg.getChildAddresses(PathAddress.EMPTY_ADDRESS);
        assertEquals(grandchildren.toString(), 1, grandchildren.size());
        assertTrue(grandchildren.toString(), grandchildren.contains(grandchildWild));

        // 2) Removing a wildcard child removes its overrides
        childWildReg.unregisterSubModel(grandchildWild);
        grandchildren = childWildReg.getChildAddresses(PathAddress.EMPTY_ADDRESS);
        assertTrue(grandchildren.toString(), grandchildren.isEmpty());
        grandchildren = childWildExtReg.getChildAddresses(PathAddress.EMPTY_ADDRESS);
        // Minor TODO: it's not gone. Minor problem, as in the real world removing the wildcard
        // is done in some large scale context like extension remove where everything
        // ends up being removed
        //assertEquals(grandchildren.toString(), 1, grandchildren.size());

        // 3) Confirm the remove did not remove unrelated registrations
        assertTrue(grandchildren.toString(), grandchildren.contains(anotherGranchild));

        // Restore state
        childWildReg.registerSubModel(new SimpleResourceDefinition(grandchildWild, NonResolvingResourceDescriptionResolver.INSTANCE));

        // 4) Removing a higher level override does not remove children of the related wildcard
        parentWildReg.unregisterSubModel(childWildExt);
        Set<PathElement> children = parentWildReg.getChildAddresses(PathAddress.EMPTY_ADDRESS);
        assertEquals(children.toString(), 1, children.size());
        assertTrue(children.toString(), children.contains(childWild));
        grandchildren = childWildReg.getChildAddresses(PathAddress.EMPTY_ADDRESS);
        assertEquals(grandchildren.toString(), 1, grandchildren.size());
        assertTrue(grandchildren.toString(), grandchildren.contains(grandchildWild));
    }

    private static class TestOSH implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

}
