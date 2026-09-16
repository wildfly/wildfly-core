/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.global.QueryOperationHandler;
import org.jboss.as.controller.registry.OperationTransformerRegistry;
import org.jboss.as.controller.registry.OperationTransformerRegistry.PlaceholderResolver;
import org.jboss.as.version.Stability;


/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public class TransformationTargetImpl implements TransformationTarget {

    private final String hostName;
    private final ModelVersion version;
    private final TransformerRegistry transformerRegistry;
    private final Map<String, ModelVersion> subsystemVersions = Collections.synchronizedMap(new HashMap<String, ModelVersion>());
    private final OperationTransformerRegistry registry;
    private final TransformationTargetType type;
    private final PlaceholderResolver placeholderResolver;
    private final Transformers.OperationExcludedTransformationRegistry operationIgnoredRegistry;

    private TransformationTargetImpl(final String hostName, final TransformerRegistry transformerRegistry, final ModelVersion version,
                                     final Map<PathAddress, ModelVersion> subsystemVersions, final OperationTransformerRegistry transformers,
                                     final TransformationTargetType type,
                                     final Transformers.OperationExcludedTransformationRegistry operationIgnoredRegistry,
                                     final PlaceholderResolver placeholderResolver) {
        this.version = version;
        this.hostName = hostName;
        this.transformerRegistry = transformerRegistry;
        for (Map.Entry<PathAddress, ModelVersion> p : subsystemVersions.entrySet()) {
            final String name = p.getKey().getLastElement().getValue();
            this.subsystemVersions.put(name, p.getValue());
        }
        this.registry = transformers;
        this.type = type;
        this.placeholderResolver = placeholderResolver;
        this.operationIgnoredRegistry = operationIgnoredRegistry;
    }

    private TransformationTargetImpl(final TransformationTargetImpl target, final PlaceholderResolver placeholderResolver) {
        this.version = target.version;
        this.hostName = target.hostName;
        this.transformerRegistry = target.transformerRegistry;
        this.subsystemVersions.putAll(target.subsystemVersions);
        this.registry = target.registry;
        this.type = target.type;
        this.operationIgnoredRegistry = target.operationIgnoredRegistry;
        this.placeholderResolver = placeholderResolver;
    }

    @Deprecated(forRemoval = true, since = "27.0.0")
    public static TransformationTarget createLocal() {
        return createLocal(Stability.DEFAULT);
    }

    public static TransformationTarget createLocal(Stability stability) {
        TransformerRegistry registry = new TransformerRegistry(stability);
        OperationTransformerRegistry r2 = registry.resolveHost(ModelVersion.create(0), new HashMap<PathAddress, ModelVersion>());
        return new TransformationTargetImpl(null, registry, ModelVersion.create(0), new HashMap<PathAddress, ModelVersion>(),
                r2, TransformationTargetType.SERVER, Transformers.OperationExcludedTransformationRegistry.DEFAULT, null);

    }

    public static TransformationTargetImpl create(final String hostName, final TransformerRegistry transformerRegistry, final ModelVersion version,
                                                  final Map<PathAddress, ModelVersion> subsystems, final TransformationTargetType type) {
        return create(hostName, transformerRegistry, version, subsystems, type, Transformers.OperationExcludedTransformationRegistry.DEFAULT);
    }

    public static TransformationTargetImpl createForHost(final String hostName, final TransformerRegistry transformerRegistry, final ModelVersion version,
                                                         final Map<PathAddress, ModelVersion> subsystems,
                                                         final Transformers.OperationExcludedTransformationRegistry ignoredRegistry) {
        return create(hostName, transformerRegistry, version, subsystems, TransformationTargetType.HOST, ignoredRegistry);
    }

    private static TransformationTargetImpl create(final String hostName, final TransformerRegistry transformerRegistry, final ModelVersion version,
                                                   final Map<PathAddress, ModelVersion> subsystems,
                                                   final TransformationTargetType type,
                                                   final Transformers.OperationExcludedTransformationRegistry ignoredRegistry) {
        final OperationTransformerRegistry registry;
        switch (type) {
            case SERVER:
                registry = transformerRegistry.resolveServer(version, subsystems);
                break;
            default:
                registry = transformerRegistry.resolveHost(version, subsystems);
        }
        return new TransformationTargetImpl(hostName, transformerRegistry, version, subsystems, registry, type, ignoredRegistry, null);
    }

    TransformationTargetImpl copyWithplaceholderResolver(final PlaceholderResolver placeholderResolver) {
        return new TransformationTargetImpl(this, placeholderResolver);
    }

    @Override
    public ModelVersion getVersion() {
        return version;
    }

    @Override
    public ModelVersion getSubsystemVersion(String subsystemName) {
        return subsystemVersions.get(subsystemName);
    }

    @Override
    public ResourceTransformer resolveTransformer(ResourceTransformationContext context, final PathAddress address ) {
        if (ignoreResourceTransformation(context, address)) {
            return ResourceTransformer.DISCARD;
        }
        OperationTransformerRegistry.ResourceTransformerEntry entry = registry.resolveResourceTransformer(address, placeholderResolver);
        if(entry == null) {
            return ResourceTransformer.DEFAULT;
        }
        return entry.getTransformer();
    }

    @Override
    public TransformerEntry getTransformerEntry(TransformationContext context, final PathAddress address) {
        if (ignoreResourceTransformation((ResourceTransformationContext) context, address)) {
            return TransformerEntry.DISCARD;
        }
        return registry.getTransformerEntry(address, placeholderResolver);
    }

    @Override
    public List<PathAddressTransformer> getPathTransformation(final PathAddress address) {
        return registry.getPathTransformations(address, placeholderResolver);
    }

    @Override
    public OperationTransformer resolveTransformer(TransformationContext context, final PathAddress address, final String operationName) {
        if(address.size() == 0) {
            // TODO use registry registry to register this operations.
            if(ModelDescriptionConstants.COMPOSITE.equals(operationName)) {
                return new CompositeOperationTransformer();
            }
        }
        if (operationIgnoredRegistry.isOperationExcluded(address, operationName)) {
            ControllerLogger.MGMT_OP_LOGGER.tracef("Excluding operation %s to %s", operationName, address);
            return OperationTransformer.DISCARD;
        }
        if (version.getMajor() < 3 && ModelDescriptionConstants.QUERY.equals(operationName)) { // TODO use transformer inheritance and register this normally
            return QueryOperationHandler.TRANSFORMER;
        }
        final OperationTransformerRegistry.OperationTransformerEntry entry = registry.resolveOperationTransformer(address, operationName, placeholderResolver);
        return entry.getTransformer();
    }

    @Override
    public void addSubsystemVersion(String subsystemName, int majorVersion, int minorVersion) {
        addSubsystemVersion(subsystemName, ModelVersion.create(majorVersion, minorVersion));
    }

    @Override
    public void addSubsystemVersion(final String subsystemName, final ModelVersion version) {
        this.subsystemVersions.put(subsystemName, version);
        transformerRegistry.addSubsystem(registry, subsystemName, version);
    }

    @Override
    public TransformationTargetType getTargetType() {
        return type;
    }

    @Override
    public String getHostName() {
        return hostName;
    }

    @Override
    public boolean isIgnoredResourceListAvailableAtRegistration() {
        return version.getMajor() >= 1 && version.getMinor() >= 4;
    }

    @Override
    public boolean isIgnoreUnaffectedConfig() {
        return false;
    }

    private boolean ignoreResourceTransformation(ResourceTransformationContext context, PathAddress address) {
        if (context.isResourceTransformationIgnored(address)) {
            return true;
        }
        return false;
    }
}
