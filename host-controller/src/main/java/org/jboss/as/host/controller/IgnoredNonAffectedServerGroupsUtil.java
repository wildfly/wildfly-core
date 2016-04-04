/*
 *
 *  * JBoss, Home of Professional Open Source.
 *  * Copyright 2013, Red Hat, Inc., and individual contributors
 *  * as indicated by the @author tags. See the copyright.txt file in the
 *  * distribution for a full listing of individual contributors.
 *  *
 *  * This is free software; you can redistribute it and/or modify it
 *  * under the terms of the GNU Lesser General Public License as
 *  * published by the Free Software Foundation; either version 2.1 of
 *  * the License, or (at your option) any later version.
 *  *
 *  * This software is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  * Lesser General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU Lesser General Public
 *  * License along with this software; if not, write to the Free
 *  * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 */
package org.jboss.as.host.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORE_UNUSED_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INITIAL_SERVER_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.SubsystemInformation;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.Resource.ResourceEntry;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Utility to inspect what resources should be ignored on a slave according to its server-configs
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class IgnoredNonAffectedServerGroupsUtil {

    private final ExtensionRegistry extensionRegistry;

    private IgnoredNonAffectedServerGroupsUtil(final ExtensionRegistry extensionRegistry) {
        this.extensionRegistry = extensionRegistry;
    }

    /**
     * Static factory
     *
     * @param extensionRegistry the extension registry
     * @return the created instance
     */
    public static IgnoredNonAffectedServerGroupsUtil create(final ExtensionRegistry extensionRegistry) {
        return new IgnoredNonAffectedServerGroupsUtil(extensionRegistry);
    }

    /**
     * Used by the slave host when creating the host info dmr sent across to the DC during the registration process
     *
     * @param ignoreUnaffectedServerGroups whether the slave host is set up to ignore config for server groups it does not have servers for
     * @param hostModel the resource containing the host model
     * @param model the dmr sent across to theDC
     * @return the modified dmr
     */
    public static ModelNode addCurrentServerGroupsToHostInfoModel(boolean ignoreUnaffectedServerGroups, Resource hostModel, ModelNode model) {
        if (!ignoreUnaffectedServerGroups) {
            return model;
        }
        model.get(IGNORE_UNUSED_CONFIG).set(ignoreUnaffectedServerGroups);
        addServerGroupsToModel(hostModel, model);
        return model;
    }

    public static void addServerGroupsToModel(Resource hostModel, ModelNode model) {
        ModelNode initialServerGroups = new ModelNode();
        initialServerGroups.setEmptyObject();
        for (ResourceEntry entry : hostModel.getChildren(SERVER_CONFIG)) {
            ModelNode serverNode = new ModelNode();
            serverNode.get(GROUP).set(entry.getModel().get(GROUP));
            if (entry.getModel().hasDefined(SOCKET_BINDING_GROUP)) {
                serverNode.get(SOCKET_BINDING_GROUP).set(entry.getModel().get(SOCKET_BINDING_GROUP).asString());
            }
            initialServerGroups.get(entry.getName()).set(serverNode);
        }
        model.get(ModelDescriptionConstants.INITIAL_SERVER_GROUPS).set(initialServerGroups);
    }

    public static Set<ServerConfigInfo> createConfigsFromModel(final ModelNode model) {
        final Set<ServerConfigInfo> serverConfigs = new HashSet<>();
        ModelNode initialServerGroups = model.get(INITIAL_SERVER_GROUPS);
        for (Property prop : initialServerGroups.asPropertyList()) {
            final List<ModelNode> servers = prop.getValue().asList();
            for (ModelNode server : servers) {
                final String socketBindingGroupOverride = server.hasDefined(SOCKET_BINDING_GROUP) ? server.get(SOCKET_BINDING_GROUP).asString() : null;
                final ServerConfigInfo serverConfigInfo = IgnoredNonAffectedServerGroupsUtil.createServerConfigInfo(prop.getValue().get(GROUP).asString(), socketBindingGroupOverride);
                serverConfigs.add(serverConfigInfo);
            }
        }
        return serverConfigs;
    }

    /**
     * For the DC to check whether an operation should be ignored on the slave, if the slave is set up to ignore config not relevant to it
     *
     * @param domainResource the domain root resource
     * @param serverConfigs the server configs the slave is known to have
     * @param pathAddress the address of the operation to check if should be ignored or not
     */
    public boolean ignoreOperation(final Resource domainResource, final Collection<ServerConfigInfo> serverConfigs, final PathAddress pathAddress) {
        if (pathAddress.size() == 0) {
            return false;
        }
        boolean ignore = ignoreResourceInternal(domainResource, serverConfigs, pathAddress);
        return ignore;
    }

    private boolean ignoreResourceInternal(final Resource domainResource, final Collection<ServerConfigInfo> serverConfigs, final PathAddress pathAddress) {
        String type = pathAddress.getElement(0).getKey();
        switch (type) {
        case PROFILE:
            return ignoreProfile(domainResource, serverConfigs, pathAddress.getElement(0).getValue());
        case SERVER_GROUP:
            return ignoreServerGroup(domainResource, serverConfigs, pathAddress.getElement(0).getValue());
        // We don't automatically ignore extensions for now
//        case EXTENSION:
//            return ignoreExtension(domainResource, serverConfigs, pathAddress.getElement(0).getValue());
        case SOCKET_BINDING_GROUP:
            return ignoreSocketBindingGroups(domainResource, serverConfigs, pathAddress.getElement(0).getValue());
        default:
            return false;
        }
    }

    private boolean ignoreProfile(final Resource domainResource, final Collection<ServerConfigInfo> serverConfigs, final String name) {
        Set<String> seenGroups = new HashSet<>();
        Set<String> profiles = new HashSet<>();
        for (ServerConfigInfo serverConfig : serverConfigs) {
            if (seenGroups.contains(serverConfig.getServerGroup())) {
                continue;
            }
            seenGroups.add(serverConfig.getServerGroup());
            Resource serverGroupResource = domainResource.getChild(PathElement.pathElement(SERVER_GROUP, serverConfig.getServerGroup()));
            String profile = serverGroupResource.getModel().get(PROFILE).asString();
            if (profile.equals(name)) {
                return false;
            }
            processProfiles(domainResource, profile, profiles);
        }
        return !profiles.contains(name);
    }

    private void processProfiles(final Resource domain, final String profile, final Set<String> profiles) {
        if (!profiles.contains(profile)) {
            profiles.add(profile);
            final PathElement pathElement = PathElement.pathElement(PROFILE, profile);
            if (domain.hasChild(pathElement)) {
                final Resource resource = domain.getChild(pathElement);
                final ModelNode model = resource.getModel();
                if (model.hasDefined(INCLUDES)) {
                    for (final ModelNode include : model.get(INCLUDES).asList()) {
                        processProfiles(domain, include.asString(), profiles);
                    }
                }
            }
        }
    }

    private boolean ignoreServerGroup(final Resource domainResource, final Collection<ServerConfigInfo> serverConfigs, final String name) {
        for (ServerConfigInfo serverConfig : serverConfigs) {
            if (serverConfig.getServerGroup().equals(name)) {
                return false;
            }
        }
        return true;
    }

    private boolean ignoreExtension(final Resource domainResource, final Collection<ServerConfigInfo> serverConfigs, final String name) {
        //Should these be the subsystems on the master, as we have it at present, or the ones from the slave?
        Map<String, SubsystemInformation> subsystems = extensionRegistry.getAvailableSubsystems(name);
        for (String subsystem : subsystems.keySet()) {
            for (ResourceEntry profileEntry : domainResource.getChildren(PROFILE)) {
                if (profileEntry.hasChild(PathElement.pathElement(SUBSYSTEM, subsystem))) {
                    if (!ignoreProfile(domainResource, serverConfigs, profileEntry.getName())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean ignoreSocketBindingGroups(final Resource domainResource, final Collection<ServerConfigInfo> serverConfigs, final String name) {
        Set<String> seenGroups = new HashSet<>();
        Set<String> socketBindingGroups = new HashSet<>();
        for (ServerConfigInfo serverConfig : serverConfigs) {
            final String socketBindingGroup;
            if (serverConfig.getSocketBindingGroup() != null) {
                if (serverConfig.getSocketBindingGroup().equals(name)) {
                    return false;
                }
                socketBindingGroup = serverConfig.getSocketBindingGroup();
            } else {
                if (seenGroups.contains(serverConfig.getServerGroup())) {
                    continue;
                }
                seenGroups.add(serverConfig.getServerGroup());
                Resource serverGroupResource = domainResource.getChild(PathElement.pathElement(SERVER_GROUP, serverConfig.getServerGroup()));
                socketBindingGroup = serverGroupResource.getModel().get(SOCKET_BINDING_GROUP).asString();
                if (socketBindingGroup.equals(name)) {
                    return false;
                }
            }
            processSocketBindingGroups(domainResource, socketBindingGroup, socketBindingGroups);
        }
        return !socketBindingGroups.contains(name);
    }

    private void processSocketBindingGroups(final Resource domainResource, final String name, final Set<String> socketBindingGroups) {
        if (!socketBindingGroups.contains(name)) {
            socketBindingGroups.add(name);
            final PathElement pathElement = PathElement.pathElement(SOCKET_BINDING_GROUP, name);
            if (domainResource.hasChild(pathElement)) {
                final Resource resource = domainResource.getChild(pathElement);
                final ModelNode model = resource.getModel();
                if (model.hasDefined(INCLUDES)) {
                    for (final ModelNode include : model.get(INCLUDES).asList()) {
                        processSocketBindingGroups(domainResource, include.asString(), socketBindingGroups);
                    }
                }
            }
        }
    }



    /**
     * For use on a slave HC to get all the server groups used by the host
     *
     * @param hostResource the host resource
     * @return the server configs on this host
     */
    public Set<ServerConfigInfo> getServerConfigsOnSlave(Resource hostResource){
        Set<ServerConfigInfo> groups = new HashSet<>();
        for (ResourceEntry entry : hostResource.getChildren(SERVER_CONFIG)) {
            groups.add(new ServerConfigInfoImpl(entry.getModel()));
        }
        return groups;
    }

    /**
     * Creates a server config info from its name, its server group and its socket binding group
     *
     * @param serverGroup the name of the server group
     * @param socketBindingGroup the name of the socket binding override used by the server config. May be {@code null}
     * @return the server config info
     */
    public static ServerConfigInfo createServerConfigInfo(String serverGroup, String socketBindingGroup) {
        return new ServerConfigInfoImpl(serverGroup, socketBindingGroup);
    }

    public static Set<ServerConfigInfo> createConfigsFromDomainWideData(Set<String> activeServerGroups, Set<String> activeSocketBindingGroups) {
        final Set<ServerConfigInfo> serverConfigs = new HashSet<>();
        if (activeSocketBindingGroups == null || activeSocketBindingGroups.isEmpty()) {
            for (String serverGroup : activeServerGroups) {
                ServerConfigInfo sci = new ServerConfigInfoImpl(serverGroup, null);
                serverConfigs.add(sci);
                DomainControllerLogger.ROOT_LOGGER.tracef("Domain wide host-exclude needs a simple %s", sci);
            }
        } else {
            // Hosts of this API version use socket binding groups beyond the default ones
            // for the server groups. Generate a set of ServerConfigInfo objects such that
            // all provided server groups and socket binding groups are named in at least
            // one. It doesn't matter what combinations are used.
            String[] sgs = activeServerGroups.toArray(new String[activeServerGroups.size()]);
            String[] sbgs = activeSocketBindingGroups.toArray(new String[activeSocketBindingGroups.size()]);
            if (sgs.length >= sbgs.length) {
                for (int i = 0; i < sgs.length; i++) {
                    String sbg = i >= sbgs.length ? null : sbgs[i];
                    ServerConfigInfo sci = new ServerConfigInfoImpl(sgs[i], sbg);
                    serverConfigs.add(sci);
                    DomainControllerLogger.ROOT_LOGGER.tracef("Domain wide host-exclude needs a synthetic active combination of %s", sci);
                }
            } else {
                for (int i = 0, j = 0; j < sbgs.length; i++, j++) {
                    if (i == sgs.length) {
                        // Start over again with the server groups
                        i = 0;
                    }
                    ServerConfigInfo sci = new ServerConfigInfoImpl(sgs[i], sbgs[j]);
                    serverConfigs.add(sci);
                    DomainControllerLogger.ROOT_LOGGER.tracef("Domain wide host-exclude needs a synthetic active combination of %s", sci);
                }
            }
        }
        return serverConfigs;
    }

    /**
     * Contains info about a server config
     */
    public interface ServerConfigInfo {

        /**
         * Gets the server config's server group name
         *
         * @return the server group name
         */
        String getServerGroup();

        /**
         * Gets the server config's socket binding group override name
         *
         * @return the socket binding group name. May be {@code null}
         */
        String getSocketBindingGroup();
    }


    private static class ServerConfigInfoImpl implements ServerConfigInfo {

        private final String serverGroup;
        private final String socketBindingGroup;

        ServerConfigInfoImpl(ModelNode model) {
            this.serverGroup = model.get(GROUP).asString();
            this.socketBindingGroup = model.hasDefined(SOCKET_BINDING_GROUP) ? model.get(SOCKET_BINDING_GROUP).asString() : null;
        }

        ServerConfigInfoImpl(String serverGroup, String socketBindingGroup) {
            this.serverGroup = serverGroup;
            this.socketBindingGroup = socketBindingGroup;
        }

        @Override
        public String getServerGroup() {
            return serverGroup;
        }

        @Override
        public String getSocketBindingGroup() {
            return socketBindingGroup;
        }

        @Override
        public String toString() {
            return "ServerConfigInfoImpl{" +
                    "serverGroup='" + serverGroup + '\'' +
                    ", socketBindingGroup='" + socketBindingGroup + '\'' +
                    '}';
        }
    }
}
