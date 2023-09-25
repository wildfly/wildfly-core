/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.host.controller.IgnoredNonAffectedServerGroupsUtil;
import org.jboss.as.host.controller.mgmt.HostInfo;
import org.jboss.dmr.ModelNode;

/**
 * Utility for the DC operation handlers to describe the missing resources for the slave hosts which are
 * set up to ignore domain config which does not affect their servers
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Emanuel Muckenhuber
 */
public class ReadMasterDomainModelUtil {

    public static final String DOMAIN_RESOURCE_ADDRESS = "domain-resource-address";

    public static final String DOMAIN_RESOURCE_MODEL = "domain-resource-model";

    public static final String DOMAIN_RESOURCE_PROPERTIES = "domain-resource-properties";

    public static final String ORDERED_CHILD_TYPES_PROPERTY = "ordered-child-types";

    private final Set<PathElement> newRootResources = new HashSet<>();

    private volatile List<ModelNode> describedResources;

    private ReadMasterDomainModelUtil() {
    }

    /**
     * Used to read the domain model when a slave host connects to the DC
     *
     *  @param transformers the transformers for the host
     *  @param transformationInputs parameters for the transformation
     *  @param ignoredTransformationRegistry registry of resources ignored by the transformation target
     *  @param domainRoot the root resource for the domain resource tree
     * @return a read master domain model util instance
     */
    static ReadMasterDomainModelUtil readMasterDomainResourcesForInitialConnect(final Transformers transformers,
                                                                                final Transformers.TransformationInputs transformationInputs,
                                                                                final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry,
                                                                                final Resource domainRoot) throws OperationFailedException {

        Resource transformedResource = transformers.transformRootResource(transformationInputs, domainRoot, ignoredTransformationRegistry);
        ReadMasterDomainModelUtil util = new ReadMasterDomainModelUtil();
        util.describedResources = util.describeAsNodeList(PathAddress.EMPTY_ADDRESS, transformedResource, false);
        return util;
    }

    /**
     * Gets a list of the resources for the slave's ApplyXXXXHandlers. Although the format might appear
     * similar as the operations generated at boot-time this description is only useful
     * to create the resource tree and cannot be used to invoke any operation.
     *
     * @return the resources
     */
    public List<ModelNode> getDescribedResources(){
        return describedResources;
    }

    /**
     * Describe the model as a list of resources with their address and model, which
     * the HC can directly apply to create the model. Although the format might appear
     * similar as the operations generated at boot-time this description is only useful
     * to create the resource tree and cannot be used to invoke any operation.
     *
     * @param rootAddress the address of the root resource being described
     * @param resource the root resource
     * @return the list of resources
     */
    private List<ModelNode> describeAsNodeList(PathAddress rootAddress, final Resource resource, boolean isRuntimeChange) {
        final List<ModelNode> list = new ArrayList<ModelNode>();

        describe(rootAddress, resource, list, isRuntimeChange);
        return list;
    }

    private void describe(final PathAddress base, final Resource resource, List<ModelNode> nodes, boolean isRuntimeChange) {
        if (resource.isProxy() || resource.isRuntime()) {
            return; // ignore runtime and proxies
        } else if (base.size() >= 1 && base.getElement(0).getKey().equals(ModelDescriptionConstants.HOST)) {
            return; // ignore hosts
        }
        if (base.size() == 1) {
            newRootResources.add(base.getLastElement());
        }
        final ModelNode description = new ModelNode();
        description.get(DOMAIN_RESOURCE_ADDRESS).set(base.toModelNode());
        description.get(DOMAIN_RESOURCE_MODEL).set(resource.getModel());
        Set<String> orderedChildren = resource.getOrderedChildTypes();
        if (!orderedChildren.isEmpty()) {
            ModelNode orderedChildTypes = description.get(DOMAIN_RESOURCE_PROPERTIES, ORDERED_CHILD_TYPES_PROPERTY);
            for (String type : orderedChildren) {
                orderedChildTypes.add(type);
            }
        }
        nodes.add(description);
        for (final String childType : resource.getChildTypes()) {
            for (final Resource.ResourceEntry entry : resource.getChildren(childType)) {
                describe(base.append(entry.getPathElement()), entry, nodes, isRuntimeChange);
            }
        }
    }


    /**
     * Create a resource based on the result of the {@code ReadMasterDomainModelHandler}.
     *
     * @param result        the operation result
     * @param extensions    set to track extensions
     * @return the resource
     */
    static Resource createResourceFromDomainModelOp(final ModelNode result, final Set<String> extensions) {
        final Resource root = Resource.Factory.create();
        for (ModelNode model : result.asList()) {

            final PathAddress resourceAddress = PathAddress.pathAddress(model.require(DOMAIN_RESOURCE_ADDRESS));

            if (resourceAddress.size() == 1) {
                final PathElement element = resourceAddress.getElement(0);
                if (element.getKey().equals(EXTENSION)) {
                    if (!extensions.contains(element.getValue())) {
                        extensions.add(element.getValue());
                    }
                }
            }

            Resource resource = root;
            final Iterator<PathElement> i = resourceAddress.iterator();
            if (!i.hasNext()) { //Those are root attributes
                resource.getModel().set(model.require(DOMAIN_RESOURCE_MODEL));
            }
            while (i.hasNext()) {
                final PathElement e = i.next();

                if (resource.hasChild(e)) {
                    resource = resource.getChild(e);
                } else {
                    /*
                    {
                        "domain-resource-address" => [
                            ("profile" => "test"),
                            ("subsystem" => "test")
                        ],
                        "domain-resource-model" => {},
                        "domain-resource-properties" => {"ordered-child-types" => ["ordered-child"]}
                    }*/
                    final Resource nr;
                    if (model.hasDefined(DOMAIN_RESOURCE_PROPERTIES, ORDERED_CHILD_TYPES_PROPERTY)) {
                        List<ModelNode> list = model.get(DOMAIN_RESOURCE_PROPERTIES, ORDERED_CHILD_TYPES_PROPERTY).asList();
                        Set<String> orderedChildTypes = new HashSet<String>(list.size());
                        for (ModelNode type : list) {
                            orderedChildTypes.add(type.asString());
                        }
                        nr = Resource.Factory.create(false, orderedChildTypes);
                    } else {
                        nr = Resource.Factory.create();
                    }
                    resource.registerChild(e, nr);
                    resource = nr;
                }

                if (!i.hasNext()) {
                    resource.getModel().set(model.require(DOMAIN_RESOURCE_MODEL));
                }
            }
        }
        return root;
    }

    /**
     * Process the host info and determine which configuration elements are required on the slave host.
     *
     * @param hostInfo             the host info
     * @param root                 the model root
     * @param extensionRegistry    the extension registry
     * @return
     */
    public static RequiredConfigurationHolder populateHostResolutionContext(final HostInfo hostInfo, final Resource root, final ExtensionRegistry extensionRegistry) {
        final RequiredConfigurationHolder rc = new RequiredConfigurationHolder();
        for (IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo info : hostInfo.getServerConfigInfos()) {
            processServerConfig(root, rc, info, extensionRegistry);
        }
        return rc;
    }

    /**
     * Determine the relevant pieces of configuration which need to be included when processing the domain model.
     *
     * @param root                 the resource root
     * @param requiredConfigurationHolder    the resolution context
     * @param serverConfig         the server config
     * @param extensionRegistry    the extension registry
     */
    static void processServerConfig(final Resource root, final RequiredConfigurationHolder requiredConfigurationHolder, final IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo serverConfig, final ExtensionRegistry extensionRegistry) {

        final Set<String> serverGroups = requiredConfigurationHolder.serverGroups;
        final Set<String> socketBindings = requiredConfigurationHolder.socketBindings;

        String sbg = serverConfig.getSocketBindingGroup();
        if (sbg != null && !socketBindings.contains(sbg)) {
            processSocketBindingGroup(root, sbg, requiredConfigurationHolder);
        }

        final String groupName = serverConfig.getServerGroup();
        final PathElement groupElement = PathElement.pathElement(SERVER_GROUP, groupName);
        // Also check the root, since this also gets executed on the slave which may not have the server-group configured yet
        if (!serverGroups.contains(groupName) && root.hasChild(groupElement)) {

            final Resource serverGroup = root.getChild(groupElement);
            final ModelNode groupModel = serverGroup.getModel();
            serverGroups.add(groupName);

            // Include the socket binding groups
            if (groupModel.hasDefined(SOCKET_BINDING_GROUP)) {
                final String socketBindingGroup = groupModel.get(SOCKET_BINDING_GROUP).asString();
                processSocketBindingGroup(root, socketBindingGroup, requiredConfigurationHolder);
            }

            final String profileName = groupModel.get(PROFILE).asString();
            processProfile(root, profileName, requiredConfigurationHolder, extensionRegistry);
        }
    }

    static void processHostModel(final RequiredConfigurationHolder holder, final Resource domain, final Resource hostModel, ExtensionRegistry extensionRegistry) {

        final Set<String> serverGroups = holder.serverGroups;

        for (final Resource.ResourceEntry entry : hostModel.getChildren(SERVER_CONFIG)) {
            final ModelNode model = entry.getModel();
            final String serverGroup = model.get(GROUP).asString();

            if (!serverGroups.contains(serverGroup)) {
                serverGroups.add(serverGroup);
            }
            if (model.hasDefined(SOCKET_BINDING_GROUP)) {
                final String socketBindingGroup = model.get(SOCKET_BINDING_GROUP).asString();
                processSocketBindingGroup(domain, socketBindingGroup, holder);
            }
            // Always process the server group, since it may be different between the current vs. original model
            processServerGroup(holder, serverGroup, domain, extensionRegistry);
        }
    }

    private static void processServerGroup(final RequiredConfigurationHolder holder, final String group, final Resource domain, ExtensionRegistry extensionRegistry) {

        final PathElement groupElement = PathElement.pathElement(SERVER_GROUP, group);
        if (!domain.hasChild(groupElement)) {
            return;
        }
        final Resource serverGroup = domain.getChild(groupElement);
        final ModelNode model = serverGroup.getModel();

        if (model.hasDefined(SOCKET_BINDING_GROUP)) {
            final String socketBindingGroup = model.get(SOCKET_BINDING_GROUP).asString();
            processSocketBindingGroup(domain, socketBindingGroup, holder);
        }

        final String profile = model.get(PROFILE).asString();
        processProfile(domain, profile, holder, extensionRegistry);

    }

    private static void processProfile(final Resource domain, final String profile, final RequiredConfigurationHolder holder, final ExtensionRegistry extensionRegistry) {
        final Set<String> profiles = holder.profiles;
        final Set<String> extensions = holder.extensions;

        if (profiles.contains(profile)) {
            return;
        }
        profiles.add(profile);
        final PathElement profileElement = PathElement.pathElement(PROFILE, profile);
        if (domain.hasChild(profileElement)) {
            final Resource resource = domain.getChild(profileElement);

            final Set<String> subsystems = new HashSet<>();
            final Set<String> availableExtensions = extensionRegistry.getExtensionModuleNames();
            for (final Resource.ResourceEntry subsystem : resource.getChildren(SUBSYSTEM)) {
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
            if (resource.getModel().hasDefined(INCLUDES)) {
                for (final ModelNode include : resource.getModel().get(INCLUDES).asList()) {
                    processProfile(domain, include.asString(), holder, extensionRegistry);
                }
            }
        }
    }

    private static void processSocketBindingGroup(final Resource domain, final String socketBindingGroup, final RequiredConfigurationHolder holder) {
        final Set<String> socketBindingGroups = holder.socketBindings;

        if (socketBindingGroups.contains(socketBindingGroup)) {
            return;
        }
        socketBindingGroups.add(socketBindingGroup);
        final PathElement socketBindingGroupElement = PathElement.pathElement(SOCKET_BINDING_GROUP, socketBindingGroup);
        if (domain.hasChild(socketBindingGroupElement)) {
            final Resource resource = domain.getChild(socketBindingGroupElement);

            if (resource.getModel().hasDefined(INCLUDES)) {
                for (final ModelNode include : resource.getModel().get(INCLUDES).asList()) {
                    processSocketBindingGroup(domain, include.asString(), holder);
                }
            }
        }
        ControllerLogger.ROOT_LOGGER.tracef("Recorded need for socket-binding-group %s", socketBindingGroup);
    }

    /**
     * Create the ResourceIgnoredTransformationRegistry for connection/reconnection process.
     *
     * @param hostInfo the host info
     * @param rc       the resolution context
     * @return
     */
    public static Transformers.ResourceIgnoredTransformationRegistry createHostIgnoredRegistry(final HostInfo hostInfo, final RequiredConfigurationHolder rc) {
        return new Transformers.ResourceIgnoredTransformationRegistry() {
            @Override
            public boolean isResourceTransformationIgnored(PathAddress address) {
                if (hostInfo.isResourceTransformationIgnored(address)) {
                    return true;
                }
                if (address.size() == 1 && hostInfo.isIgnoreUnaffectedConfig()) {
                    final PathElement element = address.getElement(0);
                    final String type = element.getKey();
                    switch (type) {
                        case ModelDescriptionConstants.EXTENSION:
                            // Don't ignore extensions for now
                            return false;
//                            if (local) {
//                                return false; // Always include all local extensions
//                            } else if (!rc.getExtensions().contains(element.getValue())) {
//                                return true;
//                            }
//                            break;
                        case PROFILE:
                            if (!rc.getProfiles().contains(element.getValue())) {
                                return true;
                            }
                            break;
                        case SERVER_GROUP:
                            if (!rc.getServerGroups().contains(element.getValue())) {
                                return true;
                            }
                            break;
                        case SOCKET_BINDING_GROUP:
                            if (!rc.getSocketBindings().contains(element.getValue())) {
                                return true;
                            }
                            break;
                    }
                }
                return false;
            }
        };
    }

    /**
     * Create the ResourceIgnoredTransformationRegistry when fetching missing content, only including relevant pieces
     * to a server-config.
     *
     * @param rc       the resolution context
     * @param delegate the delegate ignored resource transformation registry for manually ignored resources
     * @return
     */
    public static Transformers.ResourceIgnoredTransformationRegistry createServerIgnoredRegistry(final RequiredConfigurationHolder rc, final Transformers.ResourceIgnoredTransformationRegistry delegate) {
        return new Transformers.ResourceIgnoredTransformationRegistry() {
            @Override
            public boolean isResourceTransformationIgnored(PathAddress address) {
                final int length = address.size();
                if (length == 0) {
                    return false;
                } else if (length >= 1) {
                    if (delegate.isResourceTransformationIgnored(address)) {
                        return true;
                    }

                    final PathElement element = address.getElement(0);
                    final String type = element.getKey();
                    switch (type) {
                        case ModelDescriptionConstants.EXTENSION:
                            // Don't ignore extensions for now
                            return false;
//                            if (local) {
//                                return false; // Always include all local extensions
//                            } else if (rc.getExtensions().contains(element.getValue())) {
//                                return false;
//                            }
//                            break;
                        case ModelDescriptionConstants.PROFILE:
                            if (rc.getProfiles().contains(element.getValue())) {
                                return false;
                            }
                            break;
                        case ModelDescriptionConstants.SERVER_GROUP:
                            if (rc.getServerGroups().contains(element.getValue())) {
                                return false;
                            }
                            break;
                        case ModelDescriptionConstants.SOCKET_BINDING_GROUP:
                            if (rc.getSocketBindings().contains(element.getValue())) {
                                return false;
                            }
                            break;
                    }
                }
                return true;
            }
        };
    }

    public static class RequiredConfigurationHolder {

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

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("RequiredConfigurationHolder{");
            builder.append("extensions=").append(extensions);
            builder.append("profiles=").append(profiles).append(", ");
            builder.append("server-groups=").append(serverGroups).append(", ");
            builder.append("socket-bindings=").append(socketBindings).append("}");
            return builder.toString();
        }
    }
}
