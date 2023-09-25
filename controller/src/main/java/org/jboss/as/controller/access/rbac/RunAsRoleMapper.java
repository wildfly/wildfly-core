/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.rbac;

import static org.jboss.as.controller.logging.ControllerLogger.ACCESS_LOGGER;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * A {@link RoleMapper} that allows clients to specify the roles they desire to run as. By default this {@link RoleMapper} Reads
 * the set of roles from a request headers in the operation, allowing the client to completely control the mapping. Roles are
 * stored as a ModelNode of type ModelType.LIST, elements of ModelType.STRING, under operation.get("operation-headers",
 * "roles"). If no such header is found, the user is SUPERUSER. IF the list is empty, the user has no permissions.
 *
 * This {@link RoleMapper} can be extended to allow the ability to run as different roles to be checked.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class RunAsRoleMapper implements RoleMapper {

    private final RoleMapper realRoleMapper;

    public RunAsRoleMapper(RoleMapper realRoleMapper) {
        this.realRoleMapper = realRoleMapper;
    }

    @Override
    public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Action action, TargetAttribute attribute) {
        Set<String> runAsRoles = getOperationHeaderRoles(action.getOperation());
        return mapRoles(identity, realRoleMapper.mapRoles(identity, callEnvironment, action, attribute), runAsRoles, true);
    }

    @Override
    public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Action action, TargetResource resource) {
        Set<String> runAsRoles = getOperationHeaderRoles(action.getOperation());
        return mapRoles(identity, realRoleMapper.mapRoles(identity, callEnvironment, action, resource), runAsRoles, true);
    }

    @Override
    public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, JmxAction action, JmxTarget target) {
        // There's no mechanism for setting run-as roles over JMX
        return realRoleMapper.mapRoles(identity, callEnvironment, action, target);
    }

    @Override
    public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Set<String> operationHeaderRoles) {
        return mapRoles(identity, realRoleMapper.mapRoles(identity, callEnvironment, null), operationHeaderRoles, false);
    }

    @Override
    public boolean canRunAs(Set<String> mappedRoles, String runAsRole) {
        // This method is for us to call, not for others :)
        return false;
    }

    public static Set<String> getOperationHeaderRoles(ModelNode operation) {
        Set<String> result = null;
        if (operation.hasDefined(ModelDescriptionConstants.OPERATION_HEADERS)) {
            ModelNode headers = operation.get(ModelDescriptionConstants.OPERATION_HEADERS);
            if (headers.hasDefined(ModelDescriptionConstants.ROLES)) {
                ModelNode rolesNode = headers.get(ModelDescriptionConstants.ROLES);
                if (rolesNode.getType() == ModelType.STRING) {
                    rolesNode = parseRolesString(rolesNode.asString());
                }
                if (rolesNode.getType() == ModelType.STRING) {
                    result = Collections.singleton(getRoleFromText(rolesNode.asString()));
                } else {
                    result = new HashSet<String>();

                    for (ModelNode role : rolesNode.asList()) {
                        result.add(getRoleFromText(role.asString()));
                    }
                }
            }
        }
        return result;
    }

    private static ModelNode parseRolesString(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                return ModelNode.fromString(trimmed);
            } catch (Exception ignored) {
                // fall through and try comma splitting
            }
            // Strip the []
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.contains(",")) {
            ModelNode result = new ModelNode().setEmptyList();
            String[] split = trimmed.split(",");
            for (String item : split) {
                result.add(item.trim());
            }
            return result;
        } else {
            return new ModelNode(trimmed);
        }
    }

    private Set<String> mapRoles(SecurityIdentity identity, Set<String> currentRoles, Set<String> runAsRoles, boolean sanitized) {
        Set<String> result = currentRoles;
        if (runAsRoles != null) {
            Set<String> roleSet = new HashSet<String>();
            for (String role : runAsRoles) {
                String requestedRole = sanitized ? role : getRoleFromText(role);
                if (realRoleMapper.canRunAs(currentRoles, requestedRole)) {
                    roleSet.add(requestedRole);
                }
            }
            if (roleSet.isEmpty() == false) {
                result = Collections.unmodifiableSet(roleSet);
                if (ACCESS_LOGGER.isTraceEnabled()) {
                    StringBuilder sb = new StringBuilder("User '").append(identity.getPrincipal().getName()).append(
                            "' Mapped to requested roles { ");
                    for (String current : result) {
                        sb.append("'").append(current).append("' ");
                    }
                    sb.append("}");
                    ACCESS_LOGGER.trace(sb.toString());
                }
            }
        }

        return result;
    }

    private static String getRoleFromText(String text) {
        try {
            StandardRole standardRole = StandardRole.valueOf(text.toUpperCase(Locale.ENGLISH));
            return standardRole.toString();
        } catch (Exception e) {
            return text;
        }
    }
}
