/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.permission;

import static org.junit.Assert.assertEquals;

import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
public class ManagementPermissionAuthorizerTestCase {

    private static final ManagementResourceRegistration ROOT_RR = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(new SimpleResourceDefinition(null, NonResolvingResourceDescriptionResolver.INSTANCE) {
        @Override
        public List<AccessConstraintDefinition> getAccessConstraints() {
            return Collections.emptyList();
        }
    });

    private SecurityIdentity identity;
    private Environment environment;
    private ManagementPermissionAuthorizer authorizer;

    @Before
    public void setUp() {
        identity = SecurityDomain.builder().build().getAnonymousSecurityIdentity();
        ControlledProcessState processState = new ControlledProcessState(false);
        processState.setRunning();
        environment = new Environment(processState, ProcessType.EMBEDDED_SERVER);
        TestPermissionFactory testPermissionFactory = new TestPermissionFactory();
        authorizer = new ManagementPermissionAuthorizer(testPermissionFactory);
    }

    @Test
    public void testAuthorizerResourcePermit() {
        Action action = new Action(null, null, EnumSet.of(Action.ActionEffect.ADDRESS,
                Action.ActionEffect.READ_CONFIG));
        TargetResource targetResource = TargetResource.forStandalone(PathAddress.EMPTY_ADDRESS, ROOT_RR, null);
        AuthorizationResult result = authorizer.authorize(identity, environment, action, targetResource);

        assertEquals(AuthorizationResult.Decision.PERMIT, result.getDecision());
    }

    @Test
    public void testAuthorizerResourceDeny() {
        Action action = new Action(null, null, EnumSet.of(Action.ActionEffect.ADDRESS,
                Action.ActionEffect.READ_CONFIG, Action.ActionEffect.WRITE_CONFIG));
        TargetResource targetResource = TargetResource.forStandalone(PathAddress.EMPTY_ADDRESS, ROOT_RR, null);
        AuthorizationResult result = authorizer.authorize(identity, environment, action, targetResource);

        assertEquals(AuthorizationResult.Decision.DENY, result.getDecision());
    }

    @Test
    public void testAuthorizerAttributePermit() {
        Action action = new Action(null, null, EnumSet.of(Action.ActionEffect.ADDRESS,
                Action.ActionEffect.READ_CONFIG));
        TargetResource targetResource = TargetResource.forStandalone(PathAddress.EMPTY_ADDRESS, ROOT_RR, null);
        TargetAttribute targetAttribute = new TargetAttribute("test", null, new ModelNode(), targetResource);
        AuthorizationResult result = authorizer.authorize(identity, environment, action, targetAttribute);

        assertEquals(AuthorizationResult.Decision.PERMIT, result.getDecision());
    }

    @Test
    public void testAuthorizerAttributeDeny() {
        Action action = new Action(null, null, EnumSet.of(Action.ActionEffect.ADDRESS,
                Action.ActionEffect.READ_CONFIG, Action.ActionEffect.WRITE_CONFIG));
        TargetResource targetResource = TargetResource.forStandalone(PathAddress.EMPTY_ADDRESS, ROOT_RR, null);
        TargetAttribute targetAttribute = new TargetAttribute("test", null, new ModelNode(), targetResource);
        AuthorizationResult result = authorizer.authorize(identity, environment, action, targetAttribute);

        assertEquals(AuthorizationResult.Decision.DENY, result.getDecision());
    }

    // ---

    private static final class TestPermissionFactory implements PermissionFactory {
        private PermissionCollection getUserPermissions() {
            ManagementPermissionCollection mpc = new ManagementPermissionCollection("test", TestManagementPermission.class);
            mpc.add(new TestManagementPermission(Action.ActionEffect.ADDRESS));
            mpc.add(new TestManagementPermission(Action.ActionEffect.READ_CONFIG));
            mpc.add(new TestManagementPermission(Action.ActionEffect.READ_RUNTIME));
            return mpc;
        }

        private PermissionCollection getRequiredPermissions(Action action) {
            ManagementPermissionCollection mpc = new ManagementPermissionCollection(TestManagementPermission.class);
            for (Action.ActionEffect actionEffect : action.getActionEffects()) {
                mpc.add(new TestManagementPermission(actionEffect));
            }
            return mpc;
        }

        @Override
        public PermissionCollection getUserPermissions(SecurityIdentity identity, Environment callEnvironment, Action action, TargetAttribute target) {
            return getUserPermissions();
        }

        @Override
        public PermissionCollection getUserPermissions(SecurityIdentity identity, Environment callEnvironment, Action action, TargetResource target) {
            return getUserPermissions();
        }

        @Override
        public PermissionCollection getRequiredPermissions(Action action, TargetAttribute target) {
            return getRequiredPermissions(action);
        }

        @Override
        public PermissionCollection getRequiredPermissions(Action action, TargetResource target) {
            return getRequiredPermissions(action);
        }

        @Override
        public PermissionCollection getUserPermissions(SecurityIdentity identity, Environment callEnvironment, JmxAction action, JmxTarget target) {
            return null;
        }

        @Override
        public PermissionCollection getRequiredPermissions(JmxAction action, JmxTarget target) {
            return null;
        }
    }

    private static final class TestManagementPermission extends ManagementPermission {
        private TestManagementPermission(Action.ActionEffect actionEffect) {
            super("test", actionEffect);
        }

        @Override
        public boolean implies(Permission permission) {
            return equals(permission);
        }
    }
}
