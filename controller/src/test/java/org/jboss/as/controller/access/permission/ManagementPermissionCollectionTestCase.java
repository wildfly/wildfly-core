/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.permission;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.Permission;
import java.util.Enumeration;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.security.ControllerPermission;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
public class ManagementPermissionCollectionTestCase {
    private ManagementPermissionCollection permissionCollection;

    @Before
    public void setUp() {
        permissionCollection = new ManagementPermissionCollection(getClass().getSimpleName(), TestManagementPermission.class);
    }

    @Test
    public void testAdd() {
        try {
            permissionCollection.add(new TestManagementPermission(Action.ActionEffect.ADDRESS));
        } catch (Exception e) {
            fail();
        }

        try {
            permissionCollection.add(new AnotherTestManagementPermission(Action.ActionEffect.ADDRESS));
            fail();
        } catch (IllegalArgumentException ignored) { /* expected */ }

        try {
            permissionCollection.add(new ControllerPermission("bad"));
            fail();
        } catch (IllegalArgumentException ignored) { /* expected */ }
    }

    @Test
    public void testElements() {
        permissionCollection.add(new TestManagementPermission(Action.ActionEffect.ADDRESS));
        permissionCollection.add(new TestManagementPermission(Action.ActionEffect.READ_CONFIG));
        permissionCollection.add(new TestManagementPermission(Action.ActionEffect.READ_RUNTIME));

        Enumeration<Permission> elements = permissionCollection.elements();
        while (elements.hasMoreElements()) {
            ManagementPermission permission = (ManagementPermission) elements.nextElement();
            Action.ActionEffect actionEffect = permission.getActionEffect();
            assertTrue(actionEffect == Action.ActionEffect.ADDRESS
                    || actionEffect == Action.ActionEffect.READ_CONFIG
                    || actionEffect == Action.ActionEffect.READ_RUNTIME);
        }
    }

    @Test
    public void testImplies() {
        permissionCollection.add(new TestManagementPermission(Action.ActionEffect.ADDRESS));
        assertTrue(permissionCollection.implies(new TestManagementPermission(Action.ActionEffect.ADDRESS)));
        assertFalse(permissionCollection.implies(new TestManagementPermission(Action.ActionEffect.READ_CONFIG)));

        permissionCollection.add(new TestManagementPermission(Action.ActionEffect.READ_CONFIG));
        assertTrue(permissionCollection.implies(new TestManagementPermission(Action.ActionEffect.ADDRESS)));
        assertTrue(permissionCollection.implies(new TestManagementPermission(Action.ActionEffect.READ_CONFIG)));
        assertFalse(permissionCollection.implies(new TestManagementPermission(Action.ActionEffect.READ_RUNTIME)));
    }

    @Test
    public void testReadOnly() {
        assertFalse(permissionCollection.isReadOnly());

        try {
            permissionCollection.add(new TestManagementPermission(Action.ActionEffect.ADDRESS));
        } catch (Exception e) {
            fail();
        }

        permissionCollection.setReadOnly();
        assertTrue(permissionCollection.isReadOnly());

        try {
            permissionCollection.add(new TestManagementPermission(Action.ActionEffect.READ_CONFIG));
            fail();
        } catch (SecurityException ignored) { /* expected */ }
    }

    // ---

    private static final class TestManagementPermission extends ManagementPermission {
        private TestManagementPermission(Action.ActionEffect actionEffect) {
            super("test", actionEffect);
        }

        @Override
        public boolean implies(Permission permission) {
            return equals(permission);
        }
    }

    private static final class AnotherTestManagementPermission extends ManagementPermission {
        private AnotherTestManagementPermission(Action.ActionEffect actionEffect) {
            super("test2", actionEffect);
        }

        @Override
        public boolean implies(Permission permission) {
            return equals(permission);
        }
    }
}
