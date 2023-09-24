/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;

/**
 * Scope in which a {@link org.jboss.as.controller.capability.Capability capability} is available.
 * <p>
 * The {@link #GLOBAL} scope can be used for most cases. A Host Controller will use a different implementation
 * of this interface for capabilities that are limited to some subset of the domain-wide model, e.g. a single
 * profile.
 * </p>
 * <p>
 * Implementations of this interface should override {@link #equals(Object)} and {@link #hashCode()} such that
 * logically equivalent but non-identical instances can function as keys in a hash map.
 * </p>
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public interface CapabilityScope {

    /**
     * Gets whether a given capability associated with this scope can satisfy the given requirement.
     *
     * @param requiredName the name of the required capability
     * @param dependentScope the scope of the dependent capability
     * @param context resolution context in use for this resolution run
     *
     * @return {@code true} if the requirement can be satisfied from this scope; {@code false} otherwise
     */
    boolean canSatisfyRequirement(String requiredName, CapabilityScope dependentScope,
                                  CapabilityResolutionContext context);

    /**
     * Gets whether a consistency check must be performed when other capabilities depend on capabilities
     * in this scope. A consistency check is necessary if different capabilities in the dependent scope
     * can potentially require capabilities in different other scopes, but all such capabilities must be
     * available in at least one scope.
     * @return {@code true} if a consistency check is required
     */
    boolean requiresConsistencyCheck();

    /**
     * Gets a descriptive name of the scope
     * @return the name. Will not return {@code null}
     */
    String getName();

    /**
     * Gets any scope that logically include this one, i.e. where this scope can satisfy
     * requirements as if it were the including scope.
     *
     * @param context resolution context in use for this resolution run
     * @return the including scopes. Will not be {@code null} but may be empty.
     */
    default Set<CapabilityScope> getIncludingScopes(CapabilityResolutionContext context) {
        return Collections.emptySet();
    }

    /**
     * A {@code CapabilityScope} that can satisfy any dependent scope. Meant for capabilities that are present
     * regardless of any scope, or for convenience use in cases where there is only one scope.
     */
    CapabilityScope GLOBAL = new CapabilityScope() {

        /**
         * Always returns {@code true}
         * @return {@code true}, always
         */
        @Override
        public boolean canSatisfyRequirement(String requiredName, CapabilityScope dependentScope, CapabilityResolutionContext context) {
            return true;
        }

        @Override
        public boolean requiresConsistencyCheck() {
            return false;
        }

        @Override
        public String getName() {
            return "global";
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{global}";
        }
    };

    /** Factory for creating a {@code CapabilityScope} */
    class Factory {
        /**
         * Create a {@code CapabilityScope} appropriate for the given process type and address
         *
         * @param processType the type of process in which the {@code CapabilityScope} exists
         * @param address the address with which the {@code CapabilityScope} is associated
         */
        public static CapabilityScope create(ProcessType processType, PathAddress address) {
            CapabilityScope context = CapabilityScope.GLOBAL;
            PathElement pe = processType.isServer() || address.size() == 0 ? null : address.getElement(0);
            if (pe != null) {
                String type = pe.getKey();
                switch (type) {
                    case PROFILE: {
                        context = address.size() == 1 ? ProfilesCapabilityScope.INSTANCE : new ProfileChildCapabilityScope(pe.getValue());
                        break;
                    }
                    case SOCKET_BINDING_GROUP: {
                        context = address.size() == 1 ? SocketBindingGroupsCapabilityScope.INSTANCE : new SocketBindingGroupChildScope(pe.getValue());
                        break;
                    }
                    case HOST: {
                        if (address.size() >= 2) {
                            PathElement hostElement = address.getElement(1);
                            final String hostType = hostElement.getKey();
                            switch (hostType) {
                                case SUBSYSTEM:
                                case SOCKET_BINDING_GROUP:
                                    context = HostCapabilityScope.INSTANCE;
                                    break;
                                case SERVER_CONFIG:
                                    context = ServerConfigCapabilityScope.INSTANCE;
                            }
                        }
                        break;
                    }
                    case SERVER_GROUP :  {
                        context = ServerGroupsCapabilityScope.INSTANCE;
                        break;
                    }
                }
            }
            return context;

        }

        /**
         * Get capability scope for its name
         * @param name of the scope
         * @return capability scope for given name
         */
        public static CapabilityScope forName(String name){
            final boolean dynamic = name.contains("="); //for example profile=default
            final String key = dynamic ? name.substring(0, name.indexOf("=")) : name;
            final String value = dynamic ? name.substring(name.indexOf("=") + 1) : null;

            CapabilityScope context = GLOBAL;
            switch (key) {
                case PROFILE: {
                    context = dynamic ? new ProfileChildCapabilityScope(value) : ProfilesCapabilityScope.INSTANCE;
                    break;
                }
                case SOCKET_BINDING_GROUP: {
                    context = dynamic ? new SocketBindingGroupChildScope(value) : SocketBindingGroupsCapabilityScope.INSTANCE;
                    break;
                }
                /*case HOST: { //this is fishy part!
                    if (address.size() >= 2) {
                        PathElement hostElement = address.getElement(1);
                        final String hostType = hostElement.getKey();
                        switch (hostType) {
                            case SUBSYSTEM:
                            case SOCKET_BINDING_GROUP:
                                context = HostCapabilityScope.INSTANCE;
                                break;
                            case SERVER_CONFIG:
                                context = ServerConfigCapabilityScope.INSTANCE;
                        }
                    }
                    break;
                }*/
                case SERVER_GROUP: {
                    context = ServerGroupsCapabilityScope.INSTANCE;
                    break;
                }
                case SERVER_CONFIG: {
                    context = ServerConfigCapabilityScope.INSTANCE;
                    break;
                }
            }

            return context;
        }
    }
}
