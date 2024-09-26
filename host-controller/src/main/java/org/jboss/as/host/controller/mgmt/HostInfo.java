/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLONE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STABILITY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORED_RESOURCES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORED_RESOURCE_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORE_UNUSED_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MICRO_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAMES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRODUCT_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRODUCT_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELEASE_CODENAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELEASE_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WILDCARD;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jboss.as.controller.Feature;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.domain.controller.operations.ReadMasterDomainModelUtil;
import org.jboss.as.host.controller.IgnoredNonAffectedServerGroupsUtil;
import org.jboss.as.host.controller.IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo;
import org.jboss.as.host.controller.RemoteDomainConnectionService;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.version.Stability;
import org.jboss.as.version.ProductConfig;
import org.jboss.as.version.Version;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;


/**
 * Registration information provided by a slave Host Controller.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class HostInfo implements Transformers.ResourceIgnoredTransformationRegistry, Transformers.OperationExcludedTransformationRegistry, Feature {

    /**
     * Create the metadata which gets send to the DC when registering.
     *
     *
     * @param hostInfo the local host info
     * @param productConfig the product config
     * @param ignoredResourceRegistry registry of ignored resources
     * @return the host info
     */
    public static ModelNode createLocalHostHostInfo(final LocalHostControllerInfo hostInfo, final ProductConfig productConfig,
                                             final IgnoredDomainResourceRegistry ignoredResourceRegistry, final Resource hostModelResource) {
        final ModelNode info = new ModelNode();
        info.get(NAME).set(hostInfo.getLocalHostName());
        info.get(RELEASE_VERSION).set(Version.AS_VERSION);
        info.get(RELEASE_CODENAME).set(Version.AS_RELEASE_CODENAME);
        info.get(MANAGEMENT_MAJOR_VERSION).set(Version.MANAGEMENT_MAJOR_VERSION);
        info.get(MANAGEMENT_MINOR_VERSION).set(Version.MANAGEMENT_MINOR_VERSION);
        info.get(MANAGEMENT_MICRO_VERSION).set(Version.MANAGEMENT_MICRO_VERSION);
        info.get(STABILITY).set(hostInfo.getStability().name());
        final String productName = productConfig.getProductName();
        final String productVersion = productConfig.getProductVersion();
        if(productName != null) {
            info.get(PRODUCT_NAME).set(productName);
        }
        if(productVersion != null) {
            info.get(PRODUCT_VERSION).set(productVersion);
        }
        ModelNode ignoredModel = ignoredResourceRegistry.getIgnoredResourcesAsModel();
        if (ignoredModel.hasDefined(IGNORED_RESOURCE_TYPE)) {
            info.get(IGNORED_RESOURCES).set(ignoredModel.require(IGNORED_RESOURCE_TYPE));
        }
        boolean ignoreUnaffectedServerGroups = hostInfo.isRemoteDomainControllerIgnoreUnaffectedConfiguration();
        IgnoredNonAffectedServerGroupsUtil.addCurrentServerGroupsToHostInfoModel(ignoreUnaffectedServerGroups, hostModelResource, info);
        return info;
    }

    public static HostInfo fromModelNode(final ModelNode hostInfo) {
        return fromModelNode(hostInfo, null);
    }

    public static HostInfo fromModelNode(final ModelNode hostInfo, DomainHostExcludeRegistry hostIgnoreRegistry) {
        return new HostInfo(hostInfo, hostIgnoreRegistry);
    }

    private final String hostName;
    private final String releaseVersion;
    private final String releaseCodeName;
    private final int managementMajorVersion;
    private final int managementMinorVersion;
    private final int managementMicroVersion;
    private final String productName;
    private final String productVersion;
    private final Long remoteConnectionId;
    private final Transformers.ResourceIgnoredTransformationRegistry ignoredResources;
    private final boolean ignoreUnaffectedConfig;
    private final Set<ServerConfigInfo> serverConfigInfos;
    private final Set<String> domainIgnoredExtensions;
    private final boolean hostDeclaredIgnoreUnaffected;
    private final Stability stability;
    // GuardedBy this
    private ReadMasterDomainModelUtil.RequiredConfigurationHolder requiredConfigurationHolder;

    private HostInfo(final ModelNode hostInfo, DomainHostExcludeRegistry hostIgnoreRegistry) {
        hostName = hostInfo.require(NAME).asString();
        releaseVersion = hostInfo.require(RELEASE_VERSION).asString();
        releaseCodeName = hostInfo.require(RELEASE_CODENAME).asString();
        managementMajorVersion = hostInfo.require(MANAGEMENT_MAJOR_VERSION).asInt();
        managementMinorVersion = hostInfo.require(MANAGEMENT_MINOR_VERSION).asInt();
        managementMicroVersion = hostInfo.hasDefined(MANAGEMENT_MICRO_VERSION) ? hostInfo.require(MANAGEMENT_MICRO_VERSION).asInt() : 0;
        productName = hostInfo.hasDefined(PRODUCT_NAME) ? hostInfo.require(PRODUCT_NAME).asString() : null;
        productVersion = hostInfo.hasDefined(PRODUCT_VERSION) ? hostInfo.require(PRODUCT_VERSION).asString() : null;
        remoteConnectionId = hostInfo.hasDefined(RemoteDomainConnectionService.DOMAIN_CONNECTION_ID)
                ? hostInfo.get(RemoteDomainConnectionService.DOMAIN_CONNECTION_ID).asLong() : null;
        // Legacy hosts may return null - if so, assume default stability per our ProductConfig
        this.stability = Optional.ofNullable(hostInfo.get(ModelDescriptionConstants.STABILITY).asStringOrNull()).map(Stability::valueOf).orElse(Stability.DEFAULT);

        Set<String> domainIgnoredExtensions = null;
        Set<String> domainActiveServerGroups = null;
        Set<String> domainActiveSocketBindingGroups = null;
        if (hostIgnoreRegistry != null) {
            DomainHostExcludeRegistry.VersionExcludeData domainHostIgnoreData = hostIgnoreRegistry.getVersionIgnoreData(managementMajorVersion, managementMinorVersion, managementMicroVersion);
            if (domainHostIgnoreData != null) {
                domainIgnoredExtensions = domainHostIgnoreData.getExcludedExtensions();
                domainActiveServerGroups = domainHostIgnoreData.getActiveServerGroups();
                domainActiveSocketBindingGroups = domainHostIgnoreData.getActiveSocketBindingGroups();
            } else {
                DomainControllerLogger.ROOT_LOGGER.tracef("No VersionExcludeData for %d.%d.%d", managementMajorVersion, managementMinorVersion, managementMicroVersion);
            }
        } else {
            DomainControllerLogger.ROOT_LOGGER.trace("DomainHostExcludeRegistry is null");
        }
        this.domainIgnoredExtensions = domainIgnoredExtensions;

        ignoredResources = createIgnoredRegistry(hostInfo, domainIgnoredExtensions);

        hostDeclaredIgnoreUnaffected = hostInfo.hasDefined(IGNORE_UNUSED_CONFIG) && hostInfo.get(IGNORE_UNUSED_CONFIG).asBoolean();
        ignoreUnaffectedConfig = hostDeclaredIgnoreUnaffected || (domainActiveServerGroups != null && !domainActiveServerGroups.isEmpty());

        final Set<ServerConfigInfo> serverConfigInfos;
        if (ignoreUnaffectedConfig) {
            if (hostDeclaredIgnoreUnaffected) {
                // Slave provided data takes precedence
                serverConfigInfos = IgnoredNonAffectedServerGroupsUtil.createConfigsFromModel(hostInfo);
            } else {
                serverConfigInfos = IgnoredNonAffectedServerGroupsUtil.createConfigsFromDomainWideData(domainActiveServerGroups, domainActiveSocketBindingGroups);
            }
        } else {
            serverConfigInfos = Collections.emptySet();
        }
        this.serverConfigInfos = serverConfigInfos;
    }

    @Override
    public Stability getStability() {
        return this.stability;
    }

    public String getHostName() {
        return hostName;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public String getReleaseCodeName() {
        return releaseCodeName;
    }

    public int getManagementMajorVersion() {
        return managementMajorVersion;
    }

    public int getManagementMinorVersion() {
        return managementMinorVersion;
    }

    public int getManagementMicroVersion() {
        return managementMicroVersion;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductVersion() {
        return productVersion;
    }

    public Long getRemoteConnectionId() {
        return remoteConnectionId;
    }

    public boolean isResourceTransformationIgnored(final PathAddress address) {
        // This resource transformation is only used when registering the host
        // Future operations will send an updated list of ignored-resources
        return ignoredResources.isResourceTransformationIgnored(address);
    }

    public boolean isIgnoreUnaffectedConfig() {
        return ignoreUnaffectedConfig;
    }

    public Set<IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo> getServerConfigInfos() {
        return serverConfigInfos;
    }

    public String getPrettyProductName() {

        final String result;
        if(productName != null) {
            result = ProductConfig.getPrettyVersionString(productName, productVersion, releaseVersion);
        } else {
            result = ProductConfig.getPrettyVersionString(null, releaseVersion, releaseCodeName);
        }
        return result;
    }

    @Override
    public boolean isOperationExcluded(PathAddress address, String operationName) {

        if (address.size() > 0) {
            final PathElement element = address.getElement(0);
            final String type = element.getKey();
            final String name = element.getValue();
            final boolean domainExcluding = ignoreUnaffectedConfig && !hostDeclaredIgnoreUnaffected && requiredConfigurationHolder != null;
            switch (type) {
                case ModelDescriptionConstants.EXTENSION:
                    return domainIgnoredExtensions != null && domainIgnoredExtensions.contains(element.getValue());
                case PROFILE:
                    return domainExcluding
                            && ((CLONE.equals(operationName) && address.size() == 1)
                                   || !requiredConfigurationHolder.getProfiles().contains(name));
                case SERVER_GROUP:
                    return domainExcluding && !requiredConfigurationHolder.getServerGroups().contains(name);
                case SOCKET_BINDING_GROUP:
                    return domainExcluding
                            && ((CLONE.equals(operationName) && address.size() == 1)
                            || !requiredConfigurationHolder.getSocketBindings().contains(name));
            }
        }
        return false;
    }

    public synchronized ReadMasterDomainModelUtil.RequiredConfigurationHolder
            populateRequiredConfigurationHolder(Resource resource, ExtensionRegistry extensionRegistry) {
        if (requiredConfigurationHolder != null) {
            throw new IllegalStateException();
        }
        requiredConfigurationHolder = ReadMasterDomainModelUtil.populateHostResolutionContext(this, resource, extensionRegistry);
        // Keep a ref to this data for future use. Only relevant if we're ignoring stuff based
        // on a domain-wide host-exclude.active-server-groups setting not overridden by
        // ignore-unused-configuration sent from the slave.
        return requiredConfigurationHolder;
    }

    public Set<String> getDomainIgnoredExtensions() {
        return domainIgnoredExtensions;
    }

    private static class IgnoredType {
        private final boolean wildcard;
        private final Set<String> names;

        private IgnoredType() {
            wildcard = true;
            names = null;
        }

        private IgnoredType(ModelNode names) {
            wildcard = false;
            if (names.isDefined()) {
                this.names = new HashSet<String>();
                for (ModelNode name : names.asList()) {
                    this.names.add(name.asString());
                }
            } else {
                this.names = null;
            }
        }

        private IgnoredType(IgnoredType slaveIgnoredNames, Set<String> domainIgnoredNames) {
            wildcard = false;
            names = new HashSet<>();
            if (slaveIgnoredNames != null && slaveIgnoredNames.names != null) {
                names.addAll(slaveIgnoredNames.names);
            }
            if (domainIgnoredNames != null) {
                names.addAll(domainIgnoredNames);
            }
        }

        private boolean hasName(String name) {
            return wildcard || (names != null && names.contains(name));
        }
    }

    public static Transformers.ResourceIgnoredTransformationRegistry createIgnoredRegistry(final ModelNode modelNode) {
        return createIgnoredRegistry(modelNode, null);
    }

    public static Transformers.ResourceIgnoredTransformationRegistry createIgnoredRegistry(final ModelNode modelNode,
                                                                                            Set<String> domainIgnoredExtensions) {
        final Map<String, IgnoredType> ignoredResources = processIgnoredResource(modelNode, domainIgnoredExtensions);
        return new Transformers.ResourceIgnoredTransformationRegistry() {
            @Override
            public boolean isResourceTransformationIgnored(PathAddress address) {
                if (ignoredResources != null && address.size() > 0) {
                    PathElement firstElement = address.getElement(0);
                    IgnoredType ignoredType = ignoredResources.get(firstElement.getKey());
                    if (ignoredType != null) {
                        if (ignoredType.hasName(firstElement.getValue())) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }

    private static Map<String, IgnoredType> processIgnoredResource(final ModelNode model, Set<String> domainIgnoredExtensions) {
        Map<String, IgnoredType> ignoredResources = null;
        if (model.hasDefined(IGNORED_RESOURCES)) {
            ignoredResources = new HashMap<>();
            for (Property prop : model.require(IGNORED_RESOURCES).asPropertyList()) {
                String type = prop.getName();
                ModelNode ignoredModel = prop.getValue();
                IgnoredType ignoredType = ignoredModel.get(WILDCARD).asBoolean(false) ? new IgnoredType() : new IgnoredType(ignoredModel.get(NAMES));
                ignoredResources.put(type, ignoredType);
            }
        }
        if (domainIgnoredExtensions != null && !domainIgnoredExtensions.isEmpty()) {
            IgnoredType slaveIgnoredExtensions = null;
            if (ignoredResources == null) {
                ignoredResources = new HashMap<>();
            } else {
                slaveIgnoredExtensions = ignoredResources.get(EXTENSION);
            }
            IgnoredType ignoredExtensions = new IgnoredType(slaveIgnoredExtensions, domainIgnoredExtensions);
            ignoredResources.put(EXTENSION, ignoredExtensions);
            DomainControllerLogger.ROOT_LOGGER.tracef("Ignoring extensions %s", ignoredExtensions.names);
        } else {
            DomainControllerLogger.ROOT_LOGGER.tracef("No domain ignored extensions: %s", domainIgnoredExtensions);
        }
        return ignoredResources;
    }

}
