/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations.coordination;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * TODO:  Rename
 *
 * @author John Bailey
 */
public class DomainServerUtils {

    public static Set<ServerIdentity> getAllRunningServers(ModelNode hostModel, final String localHostName, final Map<String, ProxyController> serverProxies) {
        return getServersForGroup(null, hostModel, localHostName, serverProxies);
    }

    public static Set<ServerIdentity> getServersForGroup(String groupName, ModelNode hostModel, final String localHostName, final Map<String, ProxyController> serverProxies) {
        Set<ServerIdentity> result;
        if (hostModel.hasDefined(SERVER_CONFIG)) {
            result = new HashSet<ServerIdentity>();
            for (Property prop : hostModel.get(SERVER_CONFIG).asPropertyList()) {
                String serverName = prop.getName();
                if (serverProxies.get(serverName) == null) {
                    continue;
                }
                ModelNode server = prop.getValue();
                String serverGroupName = server.require(GROUP).asString();
                if (groupName != null && !groupName.equals(serverGroupName)) {
                    continue;
                }
                ServerIdentity groupedServer = new ServerIdentity(localHostName, serverGroupName, serverName);
                result.add(groupedServer);
            }
        } else {
            result = Collections.emptySet();
        }
        return result;
    }

    public static Set<ServerIdentity> getServersForType(String type, String ref, ModelNode domainModel, ModelNode hostModel, final String localHostName, final Map<String, ProxyController> serverProxies) {
        Set<String> groups = getGroupsForType(type, ref, domainModel);
        Set<ServerIdentity> allServers = new HashSet<ServerIdentity>();
        for (String group : groups) {
            allServers.addAll(getServersForGroup(group, hostModel, localHostName, serverProxies));
        }
        return allServers;
    }

    public static Set<String> getGroupsForType(String type, String ref, ModelNode domainModel) {
        Set<String> groups;
        if (domainModel.hasDefined(SERVER_GROUP)) {
            groups = new HashSet<String>();
            for (Property prop : domainModel.get(SERVER_GROUP).asPropertyList()) {
                ModelNode serverGroup = prop.getValue();
                if (ref.equals(serverGroup.get(type).asString())) {
                    groups.add(prop.getName());
                }
            }
        } else {
            groups = Collections.emptySet();
        }
        return groups;
    }

    public static Set<String> getRelatedElements(String containerType, String parent,
                                                 String childType, String child, ModelNode domainModel) {
        Set<String> result = new HashSet<String>();
        result.add(parent);
        Set<String> checked = new HashSet<String>();
        checked.add(parent);

        // Ignore any peers the target element includes
        ModelNode targetContainer = domainModel.get(containerType, parent);
        if (targetContainer.hasDefined(INCLUDES)) {
            for (ModelNode include : targetContainer.get(INCLUDES).asList()) {
                checked.add(include.asString());
            }
        }

        ModelNode containerModel = domainModel.get(containerType);
        Set<String> allContainers = containerModel.keys();
        while (checked.size() < allContainers.size()) {
            for (String name : allContainers) {
                if (!checked.contains(name)) {
                    ModelNode container = containerModel.get(name);
                    if (!container.hasDefined(INCLUDES)) {
                        checked.add(name);
                    } else {
                        boolean allKnown = true;
                        for (ModelNode include : container.get(INCLUDES).asList()) {
                            String includeName = include.asString();
                            if (result.contains(includeName)) {
                                if (container.hasDefined(childType) && container.get(childType).hasDefined(child)) {
                                    //The child is overridden in the including container, so don't push the change to the included occurrance to the servers
                                } else {
                                    result.add(name);
                                }
                                break;
                            } else if (!checked.contains(includeName)) {
                                allKnown = false;
                                break;
                            }
                        }
                        if (allKnown) {
                            checked.add(name);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static Map<String, ProxyController> getServerProxies(String localHostName, Resource domainRootResource, ImmutableManagementResourceRegistration domainRootResourceRegistration) {
        final Set<String> serverNames = domainRootResource.getChild(PathElement.pathElement(HOST, localHostName)).getChildrenNames(SERVER_CONFIG);
        final Map<String, ProxyController> proxies = new HashMap<String, ProxyController>();
        for(String serverName : serverNames) {
            final PathAddress serverAddress = PathAddress.pathAddress(PathElement.pathElement(HOST, localHostName), PathElement.pathElement(SERVER, serverName));
            final ProxyController proxyController = domainRootResourceRegistration.getProxyController(serverAddress);
            if(proxyController != null) {
                proxies.put(serverName, proxyController);
            }
        }
        return proxies;
    }
}
