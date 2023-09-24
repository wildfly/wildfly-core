/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.rbac;

import static org.jboss.as.controller.logging.ControllerLogger.ACCESS_LOGGER;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.security.Permission;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.logging.ControllerLogger;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizerConfiguration;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;

/**
 * A {@link RoleMapper} that supports configuration from the WildFly management API.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class StandardRoleMapper implements RoleMapper {

    private static final String IN_VM_ROLE = StandardRole.SUPERUSER.getOfficialForm();
    private static final RunAsRolePermission RUN_AS_IN_VM_ROLE = new RunAsRolePermission(IN_VM_ROLE);
    private final AuthorizerConfiguration authorizerConfiguration;

    public StandardRoleMapper(final AuthorizerConfiguration authorizerConfiguration) {
        this.authorizerConfiguration = authorizerConfiguration;
    }

    @Override
    public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Action action, TargetAttribute attribute) {
        return mapRoles(identity);
    }

    @Override
    public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Action action, TargetResource resource) {
        return mapRoles(identity);
    }

    @Override
    public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, JmxAction action, JmxTarget target) {
        return mapRoles(identity);
    }

    @Override
    public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Set<String> operationHeaderRoles) {
        return mapRoles(identity);
    }

    @Override
    public boolean canRunAs(Set<String> mappedRoles, String runAsRole) {
        if (runAsRole == null) {
            return false;
        }

        boolean hasRole = authorizerConfiguration.hasRole(runAsRole);
        boolean isSuperUser = mappedRoles.contains(StandardRole.SUPERUSER.toString());

        /*
         * We only allow users to specify roles to run as if they are SuperUser, if the user is not SuperUser we need to be
         * careful to not provide a way for the user to test which roles actually exist.
         */

        if (isSuperUser && hasRole == false) {
            throw ControllerLogger.ROOT_LOGGER.unknownRole(runAsRole);
        }

        return hasRole && isSuperUser;
    }

    private Set<String> mapRoles(final SecurityIdentity identity) {
        checkNotNullParam("identity", identity);
        Set<String> mappedRoles = new HashSet<String>();

        boolean traceEnabled = ACCESS_LOGGER.isTraceEnabled();

        if (SecurityActions.isInVmCall()) {
            /*
             * If the IN-VM code does not have the required permission a SecurityException will be thrown.
             *
             * At the moment clients should not be making speculation requests so in a correctly configured installation this
             * check should pass with no error.
             */
            checkPermission(RUN_AS_IN_VM_ROLE);
            ACCESS_LOGGER.tracef("Assigning role '%s' for call (An IN-VM Call).", IN_VM_ROLE);

            mappedRoles.add(IN_VM_ROLE);
        } else {
            Map<String, AuthorizerConfiguration.RoleMapping> rolesToCheck;
            if (authorizerConfiguration.isMapUsingIdentityRoles()) {
                rolesToCheck = new HashMap<String, AuthorizerConfiguration.RoleMapping>(authorizerConfiguration.getRoleMappings());
                for (String r : identity.getRoles()) {
                    String roleName = r.toUpperCase(Locale.ENGLISH);
                    if (rolesToCheck.containsKey(roleName)) {
                        AuthorizerConfiguration.RoleMapping roleMapping = rolesToCheck.remove(roleName);
                        AuthorizerConfiguration.MappingPrincipal exclusion = roleMapping.isExcluded(identity);
                        if (exclusion == null) {
                            if (traceEnabled) {
                                ACCESS_LOGGER
                                        .tracef("User '%s' assigned role '%s' due to realm assignment and no exclusion in role mapping definition.",
                                                identity.getPrincipal().getName(), roleName);
                            }
                            mappedRoles.add(roleName);
                        } else {
                            if (traceEnabled) {
                                ACCESS_LOGGER
                                        .tracef("User '%s' NOT assigned role '%s' despite realm assignment due to exclusion match against %s.",
                                                identity.getPrincipal().getName(), roleName, exclusion);
                            }
                        }
                    } else {
                        if (traceEnabled) {
                            ACCESS_LOGGER
                                    .tracef("User '%s' assigned role '%s' due to realm assignment and no role mapping to check for exclusion.",
                                            identity.getPrincipal().getName(), roleName);
                        }
                        mappedRoles.add(roleName);
                    }
                }
            } else {
                // A clone is not needed here as the whole set of values is to be iterated with no need for removal.
                rolesToCheck = authorizerConfiguration.getRoleMappings();
            }

            for (AuthorizerConfiguration.RoleMapping current : rolesToCheck.values()) {
                boolean includeAll = current.includeAllAuthedUsers() && !identity.isAnonymous();
                AuthorizerConfiguration.MappingPrincipal inclusion = includeAll == false ? current.isIncluded(identity) : null;
                if (includeAll || inclusion != null) {
                    AuthorizerConfiguration.MappingPrincipal exclusion = current.isExcluded(identity);
                    if (exclusion == null) {
                        if (traceEnabled) {
                            if (includeAll) {
                                ACCESS_LOGGER.tracef("User '%s' assiged role '%s' due to include-all set on role.", identity.getPrincipal().getName(),
                                        current.getName());
                            } else {
                                ACCESS_LOGGER.tracef("User '%s' assiged role '%s' due to match on inclusion %s", identity.getPrincipal().getName(),
                                        current.getName(), inclusion);
                            }
                        }
                        mappedRoles.add(current.getName());
                    } else {
                        if (traceEnabled) {
                            ACCESS_LOGGER.tracef("User '%s' denied membership of role '%s' due to exclusion %s",
                                    identity.getPrincipal().getName(), current.getName(), exclusion);
                        }
                    }
                } else {
                    if (traceEnabled) {
                        ACCESS_LOGGER.tracef(
                                "User '%s' not assigned role '%s' as no match on the include definition of the role mapping.",
                                identity.getPrincipal().getName(), current.getName());
                    }
                }
            }
        }

        if (traceEnabled) {
            StringBuilder sb = new StringBuilder("User '").append(identity.getPrincipal().getName()).append("' Assigned Roles { ");
            for (String current : mappedRoles) {
                sb.append("'").append(current).append("' ");
            }
            sb.append("}");
            ACCESS_LOGGER.trace(sb.toString());
        }

        // TODO - We could consider something along the lines of a WeakHashMap to hold this result keyed on the Caller.
        // The contents of the Caller are not expected to change during a call and we could clear the cache on a config change.
        return Collections.unmodifiableSet(mappedRoles);
    }

    private static void checkPermission(final Permission permission) {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            securityManager.checkPermission(permission);
        }
    }

}
