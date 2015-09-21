/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.host.controller.ignored;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLONE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TO_PROFILE;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.dmr.ModelNode;

/**
 * Registry for excluded domain-level resources. To be used by slave Host Controllers to ignore requests
 * for particular resources that the host cannot understand. This is a mechanism to allow hosts running earlier
 * AS releases to function as slaves in domains whose master is in a later release.
 *
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class IgnoredDomainResourceRegistry {

    private final LocalHostControllerInfo localHostControllerInfo;
    private volatile IgnoredDomainResourceRoot rootResource;
    private IgnoredClonedProfileRegistry ignoredClonedProfileRegistry = new IgnoredClonedProfileRegistry();

    public IgnoredDomainResourceRegistry(LocalHostControllerInfo localHostControllerInfo) {
        this.localHostControllerInfo = localHostControllerInfo;
    }

    /**
     * Returns whether this host should ignore operations from the master domain controller that target
     * the given address.
     *
     * @param address the resource address. Cannot be {@code null}
     *
     * @return {@code true} if the operation should be ignored; {@code false} otherwise
     */
    public boolean isResourceExcluded(final PathAddress address) {
        if (!localHostControllerInfo.isMasterDomainController() && address.size() > 0) {
            IgnoredDomainResourceRoot root = this.rootResource;
            PathElement firstElement = address.getElement(0);
            IgnoreDomainResourceTypeResource typeResource = root == null ? null : root.getChildInternal(firstElement.getKey());
            if (typeResource != null) {
                if (typeResource.hasName(firstElement.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void registerResources(final ManagementResourceRegistration parentRegistration) {
        parentRegistration.registerSubModel(new ResourceDefinition());
    }

    public Resource.ResourceEntry getRootResource() {
        IgnoredDomainResourceRoot root = new IgnoredDomainResourceRoot(this);
        this.rootResource = root;
        return root;
    }

    public ModelNode getIgnoredResourcesAsModel() {
        IgnoredDomainResourceRoot root = this.rootResource;
        ModelNode model =  (root == null ? new ModelNode() : Resource.Tools.readModel(root));
        return model;
    }

    void publish(IgnoredDomainResourceRoot root) {
        this.rootResource = root;
    }

    boolean isMaster() {
        return localHostControllerInfo.isMasterDomainController();
    }

    public IgnoredClonedProfileRegistry getIgnoredClonedProfileRegistry() {
        return ignoredClonedProfileRegistry;
    }

    private class ResourceDefinition extends SimpleResourceDefinition {

        public ResourceDefinition() {
            super(IgnoredDomainResourceRoot.PATH_ELEMENT, HostResolver.getResolver(ModelDescriptionConstants.IGNORED_RESOURCES));
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerSubModel(new IgnoredDomainTypeResourceDefinition());
        }
    }

    /**
     * <p>This class is for internal use only.</p>
     *
     * <p>The main purpose of this registry is to deal with the situation where a profile is explicitly ignored on the slave,
     * and that profile is cloned. So say that the {@code ignored} profile is ignored on the slave, and a call comes to
     * {@code /profile=ignore:clone(to-profile=new)}. Since the {@code ignored} profile is ignored on the slave, the
     * {@code clone} operation never gets called, so no {@code new} profile gets created on the slave. Hence, we need
     * to ignore all subsequent operation invocations on the slave for the {@code new} profile. This class maintains the
     * runtime registry of profiles resulting from clone operations on profiles that were ignored on this slave.</p>
     *
     * <p>The situation above where the {@code new} profile does not get created on the slave due to the {@ignored} profile
     * being explicitly ignored on the slave, will result in the server being put into the {@code reload-required} state
     * since according to the explicit ignores it should really be part of the slave's domain model, and a reload of the
     * slave will download that.
     *
     * <p>If the {@new} profile was also explicitly ignored, we do not add it to the runtime registry and do not put the
     * server into the {@code reload-required} state. The settings in the slave model deal with the ignores for us in
     * that case.</p>
     *
     * <p>Finally in the example above, a call to {@code /profile=new:remove} will remove the {@code new} profile from the
     * runtime registry of profiles resulting from clone operations on profiles that were ignored on this slave.</p>
     *
     * <p>The registry is transactional, using a transaction local copy of the changes made, and should be published/rolled back
     * on transaction completion.</p>
     */
    public class IgnoredClonedProfileRegistry {
        private volatile Set<String> ignoredClonedProfiles = Collections.synchronizedSet(new HashSet<>());
        private volatile Set<String> currentTxIgnoredClonedProfiles;
        private volatile boolean reloadRequired;

        private IgnoredClonedProfileRegistry() {
        }

        /**
         * Checks if an operation should be ignored, and updates the runtime registry and host state as required. Use
         * {@link #isReloadRequired()} to check if the host state should be set to {@code reload-required}.
         *
         * @param operation the operation to check
         * @return whether the operation should be ignored
         */
        public boolean checkIgnoredProfileClone(ModelNode operation) {
            if (!localHostControllerInfo.isMasterDomainController()) {
                PathAddress addr = PathAddress.pathAddress(operation.get(OP_ADDR));
                if (addr.size() > 0) {
                    PathElement first = addr.getElement(0);
                    if (first.getKey().equals(PROFILE)) {
                        String name = operation.get(OP).asString();
                        if (name.equals(CLONE) && isResourceExcluded(addr)) {
                            //We are cloning an ignored profile
                            final String profileName = operation.get(TO_PROFILE).asString();

                            if (!isResourceExcluded(PathAddress.pathAddress(PROFILE, profileName))) {
                                //The new profile is not explicitly ignored, so  add the new profile to the runtime registry
                                //and indicate that the host should be put into the reload-required state.
                                getIgnoredClonedProfiles(true).add(profileName);
                                reloadRequired = true;
                                return true;
                            }
                        } else if (name.equals(REMOVE) && addr.size() == 1) {
                            //We are removing a profile, remove it from the runtime registry if it is there
                            return getIgnoredClonedProfiles(true).remove(first.getValue());
                        } else {
                            //Ignore depending on if it is in the runtime registry
                            return getIgnoredClonedProfiles(false).contains(first.getValue());
                        }
                    }
                }
            }
            return false;
        }

        /**
         * Callback for starting applying a fresh domain model from the DC. This will clear the runtime registry
         */
        public void initializeModelSync() {
            if (!localHostControllerInfo.isMasterDomainController()) {
                //We are resyncing the model, so clear the runtime registry
                getIgnoredClonedProfiles(true).clear();
            }
        }

        /**
         * Callback for when the controller transaction completes. This will publish the changes to the runtime registry
         * if the transaction was committed, and roll them back if it was rolled back.
         *
         * @param rollback {@code true} if the changes should be rolled back, {@code false} if they should be committed.
         */
        public void complete(boolean rollback) {
            if (!localHostControllerInfo.isMasterDomainController()) {
                if (!rollback) {
                    if (currentTxIgnoredClonedProfiles != null) {
                        ignoredClonedProfiles = currentTxIgnoredClonedProfiles;
                    }
                }
                currentTxIgnoredClonedProfiles = null;
                reloadRequired = false;
            }
        }

        /**
         * Check if the changes to the registry should cause the slave to be put into the {@code reload-required} state.
         *
         * @return {@code true} if the host should be put into the {@code reload-required} state, {@code false} otherwise.
         */
        public boolean isReloadRequired() {
            return reloadRequired;
        }

        private Set<String> getIgnoredClonedProfiles(boolean write) {
            if (currentTxIgnoredClonedProfiles != null) {
                return currentTxIgnoredClonedProfiles;
            }
            if (write) {
                currentTxIgnoredClonedProfiles = Collections.synchronizedSet(new HashSet<>(ignoredClonedProfiles));
                return currentTxIgnoredClonedProfiles;
            }
            return ignoredClonedProfiles;
        }

    }
}
