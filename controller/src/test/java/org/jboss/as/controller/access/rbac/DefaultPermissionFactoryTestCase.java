/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.rbac;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizerConfiguration;
import org.jboss.as.controller.access.CombinationPolicy;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.access.constraint.Constraint;
import org.jboss.as.controller.access.constraint.ConstraintFactory;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
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
public class DefaultPermissionFactoryTestCase {

    private static final ManagementResourceRegistration ROOT_RR = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(new SimpleResourceDefinition(null, NonResolvingResourceDescriptionResolver.INSTANCE) {
        @Override
        public List<AccessConstraintDefinition> getAccessConstraints() {
            return Collections.emptyList();
        }
    });

    private static final AuthorizerConfiguration PERMISSIVE;
    private static final AuthorizerConfiguration REJECTING;

    static {
        PERMISSIVE = new WritableAuthorizerConfiguration(StandardRBACAuthorizer.AUTHORIZER_DESCRIPTION);
        WritableAuthorizerConfiguration rejecting = new WritableAuthorizerConfiguration(StandardRBACAuthorizer.AUTHORIZER_DESCRIPTION);
        rejecting.setPermissionCombinationPolicy(CombinationPolicy.REJECTING);
        REJECTING = rejecting;
    }

    private SecurityIdentity identity;
    private Environment environment;

    @Before
    public void setUp() {
        SecurityDomain testDomain = SecurityDomain.builder()
            .build();
        identity = testDomain.getAnonymousSecurityIdentity();

        ControlledProcessState processState = new ControlledProcessState(false);
        processState.setRunning();
        environment = new Environment(processState, ProcessType.EMBEDDED_SERVER);
    }

    @Test
    public void testSingleRoleRejectingCombinationPolicy() {
        testResourceSingleRole(REJECTING, StandardRole.MONITOR, StandardRole.MONITOR, true);
        testResourceSingleRole(REJECTING, StandardRole.MONITOR, StandardRole.OPERATOR, false);

        testAttributeSingleRole(REJECTING, StandardRole.MONITOR, StandardRole.MONITOR, true);
        testAttributeSingleRole(REJECTING, StandardRole.MONITOR, StandardRole.OPERATOR, false);
    }

    @Test
    public void testSingleRolePermissiveCombinationPolicy() {
        testResourceSingleRole(PERMISSIVE, StandardRole.MONITOR, StandardRole.MONITOR, true);
        testResourceSingleRole(PERMISSIVE, StandardRole.MONITOR, StandardRole.OPERATOR, false);

        testAttributeSingleRole(PERMISSIVE, StandardRole.MONITOR, StandardRole.MONITOR, true);
        testAttributeSingleRole(PERMISSIVE, StandardRole.MONITOR, StandardRole.OPERATOR, false);
    }

    @Test
    public void testSingleUserRoleMoreAllowedRoles() {
        testResource(PERMISSIVE, new StandardRole[] {StandardRole.MONITOR},
                new StandardRole[] {StandardRole.MONITOR, StandardRole.ADMINISTRATOR}, true);
        testResource(PERMISSIVE, new StandardRole[] {StandardRole.MONITOR},
                new StandardRole[] {StandardRole.OPERATOR, StandardRole.ADMINISTRATOR}, false);

        testAttribute(PERMISSIVE, new StandardRole[]{StandardRole.MONITOR},
                new StandardRole[]{StandardRole.MONITOR, StandardRole.ADMINISTRATOR}, true);
        testAttribute(PERMISSIVE, new StandardRole[]{StandardRole.MONITOR},
                new StandardRole[]{StandardRole.OPERATOR, StandardRole.ADMINISTRATOR}, false);
    }

    @Test
    public void testMoreUserRolesSingleAllowedRole() {
        testResource(PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.MONITOR}, true);
        testResource(PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.OPERATOR}, true);
        testResource(PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.ADMINISTRATOR}, false);

        testAttribute(PERMISSIVE, new StandardRole[]{StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[]{StandardRole.MONITOR}, true);
        testAttribute(PERMISSIVE, new StandardRole[]{StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[]{StandardRole.OPERATOR}, true);
        testAttribute(PERMISSIVE, new StandardRole[]{StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[]{StandardRole.ADMINISTRATOR}, false);
    }

    @Test
    public void testMoreUserRolesMoreAllowedRoles() {
        testResource(PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR}, true);
        testResource(PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.OPERATOR, StandardRole.ADMINISTRATOR}, true);
        testResource(PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.ADMINISTRATOR, StandardRole.AUDITOR}, false);

        testAttribute(PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR}, true);
        testAttribute(PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.OPERATOR, StandardRole.ADMINISTRATOR}, true);
        testAttribute(PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.ADMINISTRATOR, StandardRole.AUDITOR}, false);
    }

    private void testResourceSingleRole(AuthorizerConfiguration authorizerConfiguration, StandardRole userRole, StandardRole allowedRole,
                                        boolean accessExpectation) {
        testResource(authorizerConfiguration, new StandardRole[] {userRole}, new StandardRole[] {allowedRole}, accessExpectation);
    }

    private void testAttributeSingleRole(AuthorizerConfiguration authorizerConfiguration, StandardRole userRole, StandardRole allowedRole,
                                         boolean accessExpectation) {
        testAttribute(authorizerConfiguration, new StandardRole[] {userRole}, new StandardRole[] {allowedRole}, accessExpectation);
    }

    private void testResource(AuthorizerConfiguration authorizerConfiguration,
                              StandardRole[] userRoles,
                              StandardRole[] allowedRoles,
                              boolean accessExpectation) {

        ConstraintFactory constraintFactory = new TestConstraintFactory(allowedRoles);
        TestRoleMapper roleMapper = new TestRoleMapper(userRoles);
        DefaultPermissionFactory permissionFactory = new DefaultPermissionFactory(roleMapper,
                Collections.singleton(constraintFactory), authorizerConfiguration);

        Action action = new Action(null, null, EnumSet.of(Action.ActionEffect.ADDRESS));
        TargetResource targetResource = TargetResource.forStandalone(PathAddress.EMPTY_ADDRESS, ROOT_RR, null);

        PermissionCollection userPermissions = permissionFactory.getUserPermissions(identity, environment, action, targetResource);
        PermissionCollection requiredPermissions = permissionFactory.getRequiredPermissions(action, targetResource);

        for (Permission requiredPermission : toSet(requiredPermissions)) {
            assertEquals(accessExpectation, userPermissions.implies(requiredPermission));
        }
    }

    private void testAttribute(AuthorizerConfiguration authorizerConfiguration,
                               StandardRole[] userRoles,
                               StandardRole[] allowedRoles,
                               boolean accessExpectation) {

        ConstraintFactory constraintFactory = new TestConstraintFactory(allowedRoles);
        TestRoleMapper roleMapper = new TestRoleMapper(userRoles);
        DefaultPermissionFactory permissionFactory = new DefaultPermissionFactory(roleMapper,
                Collections.singleton(constraintFactory), authorizerConfiguration);

        Action action = new Action(null, null, EnumSet.of(Action.ActionEffect.ADDRESS));
        TargetResource targetResource = TargetResource.forStandalone(PathAddress.EMPTY_ADDRESS, ROOT_RR, null);
        TargetAttribute targetAttribute = new TargetAttribute("test", null, new ModelNode(), targetResource);

        PermissionCollection userPermissions = permissionFactory.getUserPermissions(identity, environment, action, targetAttribute);
        PermissionCollection requiredPermissions = permissionFactory.getRequiredPermissions(action, targetAttribute);

        for (Permission requiredPermission : toSet(requiredPermissions)) {
            assertEquals(accessExpectation, userPermissions.implies(requiredPermission));
        }
    }

    @Test
    public void testRoleCombinationRejecting() {
        Action action = new Action(null, null, EnumSet.of(Action.ActionEffect.ADDRESS,
                Action.ActionEffect.READ_CONFIG));
        TargetResource targetResource = TargetResource.forStandalone(PathAddress.EMPTY_ADDRESS, ROOT_RR, null);

        DefaultPermissionFactory permissionFactory = null;
        try {
            permissionFactory = new DefaultPermissionFactory(new TestRoleMapper(),
                    Collections.<ConstraintFactory>emptySet(), REJECTING);
            permissionFactory.getUserPermissions(identity, environment, action, targetResource);
        } catch (Exception e) {
            fail();
        }

        try {
            permissionFactory = new DefaultPermissionFactory(
                    new TestRoleMapper(StandardRole.MONITOR), Collections.<ConstraintFactory>emptySet(), REJECTING);
            permissionFactory.getUserPermissions(identity, environment, action, targetResource);
        } catch (Exception e) {
            fail();
        }

        permissionFactory = new DefaultPermissionFactory(
                new TestRoleMapper(StandardRole.MONITOR, StandardRole.DEPLOYER), REJECTING);
        try {
            permissionFactory.getUserPermissions(identity, environment, action, targetResource);
            fail();
        } catch (Exception e) { /* expected */ }

        permissionFactory = new DefaultPermissionFactory(
                new TestRoleMapper(StandardRole.MONITOR, StandardRole.DEPLOYER, StandardRole.AUDITOR),
                Collections.<ConstraintFactory>emptySet(), REJECTING);
        try {
            permissionFactory.getUserPermissions(identity, environment, action, targetResource);
            fail();
        } catch (Exception e) { /* expected */ }
    }

    // ---

    private static Set<Permission> toSet(PermissionCollection permissionCollection) {
        Set<Permission> result = new HashSet<>();
        Enumeration<Permission> elements = permissionCollection.elements();
        while (elements.hasMoreElements()) {
            result.add(elements.nextElement());
        }
        return Collections.unmodifiableSet(result);
    }

    private static final class TestRoleMapper implements RoleMapper {
        private final Set<String> roles;

        private TestRoleMapper(StandardRole... roles) {
            Set<String> stringRoles = new HashSet<>();
            for (StandardRole role : roles) {
                stringRoles.add(role.name());
            }
            this.roles = Collections.unmodifiableSet(stringRoles);
        }

        @Override
        public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Action action, TargetAttribute attribute) {
            return roles;
        }

        @Override
        public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Action action, TargetResource resource) {
            return roles;
        }

        @Override
        public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, JmxAction action, JmxTarget target) {
            return roles;
        }

        @Override
        public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Set<String> operationHeaderRoles) {
            return roles;
        }

        @Override
        public boolean canRunAs(Set<String> mappedRoles, String runAsRole) {
            return runAsRole != null && roles.contains(runAsRole) && mappedRoles.contains(StandardRole.SUPERUSER.toString());
        }
    }

    private static final class TestConstraintFactory implements ConstraintFactory {
        private final Set<StandardRole> allowedRoles;

        private TestConstraintFactory(StandardRole... allowedRoles) {
            Set<StandardRole> roles = new HashSet<>();
            for (StandardRole allowedRole : allowedRoles) {
                roles.add(allowedRole);
            }
            this.allowedRoles = Collections.unmodifiableSet(roles);
        }

        @Override
        public Constraint getStandardUserConstraint(StandardRole role, Action.ActionEffect actionEffect) {
            boolean allowed = allowedRoles.contains(role);
            return new TestConstraint(allowed);
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetAttribute target) {
            return new TestConstraint(true);
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetResource target) {
            return new TestConstraint(true);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TestConstraintFactory;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public int compareTo(ConstraintFactory o) {
            return this.equals(o) ? 0 : -1;
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, JmxAction action, JmxTarget target) {
            return new TestConstraint(true);
        }
    }

    private static final class TestConstraint implements Constraint {
        private final boolean allowed;

        private TestConstraint(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public boolean violates(Constraint other, Action.ActionEffect actionEffect) {
            if (other instanceof TestConstraint) {
                return this.allowed != ((TestConstraint) other).allowed;
            }
            return false;
        }

        @Override
        public boolean replaces(Constraint other) {
            return false;
        }
    }
}
