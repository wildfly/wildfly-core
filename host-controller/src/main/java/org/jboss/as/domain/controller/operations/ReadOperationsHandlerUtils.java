/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;

import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.IgnoredNonAffectedServerGroupsUtil;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
class ReadOperationsHandlerUtils {


    static void processServerConfig(final OperationContext context, final Resource root, final ResolutionContext resolutionContext, final IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo serverConfig, final ExtensionRegistry extensionRegistry) {

        final Set<String> extensions = resolutionContext.extensions;
        final Set<String> profiles = resolutionContext.profiles;
        final Set<String> serverGroups = resolutionContext.serverGroups;
        final Set<String> socketBindings = resolutionContext.socketBindings;

        if (serverConfig.getSocketBindingGroup() != null && !socketBindings.contains(serverConfig.getSocketBindingGroup())) {
            socketBindings.add(serverConfig.getSocketBindingGroup());
        }

        final String groupName = serverConfig.getServerGroup();
        final PathElement groupElement = PathElement.pathElement(SERVER_GROUP, groupName);
        // Also check the root, since this also gets executed on the slave which may not have the server-group configured yet
        if (!serverGroups.contains(groupName) && root.hasChild(groupElement)) {

            final Resource serverGroup = context.readResourceFromRoot(PathAddress.pathAddress(groupElement), false);
            final ModelNode groupModel = serverGroup.getModel();
            serverGroups.add(groupName);

            // Include the socket binding groups
            if (groupModel.hasDefined(SOCKET_BINDING_GROUP)) {
                final String socketBinding = groupModel.get(SOCKET_BINDING_GROUP).asString();
                if (!socketBindings.contains(socketBinding)) {
                    socketBindings.add(socketBinding);
                }
            }

            final String profileName = groupModel.get(PROFILE).asString();
            final PathElement profileElement = PathElement.pathElement(PROFILE, profileName);
            if (!profiles.contains(profileName) && root.hasChild(profileElement)) {
                final Resource profile = context.readResourceFromRoot(PathAddress.pathAddress(profileElement), false);

                if (profile.getModel().hasDefined(INCLUDE)) {
                    // TODO include is currently disabled
                }

                profiles.add(profileName);
                final Set<String> subsystems = new HashSet<>();
                final Set<String> availableExtensions = extensionRegistry.getExtensionModuleNames();
                for (final Resource.ResourceEntry subsystem : profile.getChildren(PROFILE)) {
                    subsystems.add(subsystem.getName());
                }
                for (final String extension : availableExtensions) {
                    if (extensions.contains(extension)) {
                        // Skip already processed extensions
                        continue;
                    }
                    for (final String subsystem : extensionRegistry.getAvailableSubsystems(extension).keySet()) {
                        if (subsystems.contains(subsystem)) {
                            extensions.add(extension);
                        }
                    }
                }
            }
        }
    }

    static class ResolutionContext {

        private final Set<String> extensions = new HashSet<>();
        private final Set<String> profiles = new HashSet<>();
        private final Set<String> serverGroups = new HashSet<>();
        private final Set<String> socketBindings = new HashSet<>();

        public Set<String> getExtensions() {
            return extensions;
        }

        public Set<String> getProfiles() {
            return profiles;
        }

        public Set<String> getServerGroups() {
            return serverGroups;
        }

        public Set<String> getSocketBindings() {
            return socketBindings;
        }
    }

}
