/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ProxyStepHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.OverrideDescriptionProvider;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ProxyControllerRegistration extends AbstractResourceRegistration implements DescriptionProvider {

    private static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("proxy-step",
            NonResolvingResourceDescriptionResolver.INSTANCE)
            .withFlags(OperationEntry.Flag.HIDDEN)
            .setRuntimeOnly()
            .build();

    @SuppressWarnings("unused")
    private volatile Map<String, OperationEntry> operations;

    @SuppressWarnings("unused")
    private volatile Map<String, AttributeAccess> attributes;

    private final ProxyController proxyController;
    private final OperationEntry operationEntry;

    private static final AtomicMapFieldUpdater<ProxyControllerRegistration, String, OperationEntry> operationsUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(ProxyControllerRegistration.class, Map.class, "operations"));
    private static final AtomicMapFieldUpdater<ProxyControllerRegistration, String, AttributeAccess> attributesUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(ProxyControllerRegistration.class, Map.class, "attributes"));

    ProxyControllerRegistration(final String valueString, final NodeSubregistry parent, final ProxyController proxyController) {
        super(valueString, parent);
        this.operationEntry = new OperationEntry(DEFINITION, new ProxyStepHandler(proxyController), false);
        this.proxyController = proxyController;
        operationsUpdater.clear(this);
        attributesUpdater.clear(this);
    }

    @Override
    OperationEntry getOperationEntry(final ListIterator<PathElement> iterator, final String operationName, OperationEntry inherited) {
        checkPermission();
        if (! iterator.hasNext()) {
            // Only in case there is an explicit handler...
            final OperationEntry entry = operationsUpdater.get(this, operationName);
            return entry == null ? operationEntry : entry;
        } else {
            return operationEntry;
        }
    }

    @Override
    OperationEntry getInheritableOperationEntry(String operationName) {
        checkPermission();
        return null;
    }

    @Override
    public int getMaxOccurs() {
        return 1;
    }

    @Override
    public int getMinOccurs() {
        return 1;
    }

    @Override
    public boolean isRuntimeOnly() {
        checkPermission();
        return true;
    }

    @Override
    public boolean isRemote() {
        checkPermission();
        return true;
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        checkPermission();
        return Collections.emptyList();
    }

    @Override
    public ManagementResourceRegistration registerSubModel(final ResourceDefinition resourceDefinition) {
        throw alreadyRegistered();
    }

    @Override
    public void registerCapability(RuntimeCapability capability) {
        throw alreadyRegistered();
    }

    @Override
    public void registerIncorporatingCapabilities(Set<RuntimeCapability> capabilities) {
        throw alreadyRegistered();
    }

    @Override
    public void registerRequirements(Set<? extends CapabilityReferenceRecorder> requirements) {
        throw alreadyRegistered();
    }

    @Override
    public void unregisterSubModel(final PathElement address) throws IllegalArgumentException {
        throw alreadyRegistered();
    }

    @Override
    public ManagementResourceRegistration registerOverrideModel(String name, OverrideDescriptionProvider descriptionProvider) {
        throw alreadyRegistered();
    }

    @Override
    public void unregisterOverrideModel(String name) {
        throw alreadyRegistered();
    }

    @Override
    public void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler, boolean inherited) {
        if (operationsUpdater.putIfAbsent(this, definition.getName(),
                new OperationEntry(definition, handler, inherited)) != null) {
            throw alreadyRegistered("operation handler", definition.getName());
        }
    }

    @Override
    public void unregisterOperationHandler(final String operationName) {
        if (operationsUpdater.remove(this, operationName) == null) {
            throw operationNotRegisteredException(operationName, proxyController.getProxyNodeAddress().getLastElement());
        }
    }

    @Override
    public void registerReadWriteAttribute(final AttributeDefinition definition, final OperationStepHandler readHandler, final OperationStepHandler writeHandler) {
        final String attributeName = definition.getName();
        AttributeAccess.Storage storage = definition.getFlags().contains(AttributeAccess.Flag.STORAGE_RUNTIME) ? AttributeAccess.Storage.RUNTIME : AttributeAccess.Storage.CONFIGURATION;
        AttributeAccess aa = new AttributeAccess(AttributeAccess.AccessType.READ_WRITE, storage, readHandler, writeHandler, definition);
        if (attributesUpdater.putIfAbsent(this, attributeName, aa) != null) {
            throw alreadyRegistered("attribute", attributeName);
        }
    }

    @Override
    public void registerReadOnlyAttribute(final AttributeDefinition definition, final OperationStepHandler readHandler) {
        final String attributeName = definition.getName();
        AttributeAccess.Storage storage = definition.getFlags().contains(AttributeAccess.Flag.STORAGE_RUNTIME) ? AttributeAccess.Storage.RUNTIME : AttributeAccess.Storage.CONFIGURATION;
        AttributeAccess aa = new AttributeAccess(AttributeAccess.AccessType.READ_ONLY, storage, readHandler, null, definition);
        if (attributesUpdater.putIfAbsent(this, attributeName, aa) != null) {
            throw alreadyRegistered("attribute", attributeName);
        }
    }

    @Override
    public void unregisterAttribute(String attributeName) {
        attributesUpdater.remove(this, attributeName);
    }

    @Override
    public void registerMetric(AttributeDefinition definition, OperationStepHandler metricHandler) {
        AttributeAccess aa = new AttributeAccess(AttributeAccess.AccessType.METRIC, AttributeAccess.Storage.RUNTIME, metricHandler, null, definition);
        if (attributesUpdater.putIfAbsent(this, definition.getName(), aa) != null) {
            throw alreadyRegistered("attribute", definition.getName());
        }
    }

    @Override
    public void registerProxyController(final PathElement address, final ProxyController proxyController) throws IllegalArgumentException {
        throw alreadyRegistered();
    }

    @Override
    public void unregisterProxyController(final PathElement address) throws IllegalArgumentException {
        throw alreadyRegistered();
    }

    @Override
    public void registerAlias(PathElement address, AliasEntry alias) {
        throw alreadyRegistered();
    }

    @Override
    public void unregisterAlias(PathElement address) {
        throw alreadyRegistered();
    }

    @Override
    public void registerNotification(NotificationDefinition notification, boolean inherited) {
        throw alreadyRegistered();
    }

    @Override
    public void registerNotification(NotificationDefinition notification) {
        throw alreadyRegistered();
    }

    @Override
    public void unregisterNotification(String notificationType) {
        throw alreadyRegistered();
    }

    @Override
    void getOperationDescriptions(final ListIterator<PathElement> iterator, final Map<String, OperationEntry> providers, final boolean inherited) {
        checkPermission();
    }


    @Override
    void getInheritedOperationEntries(final Map<String, OperationEntry> providers) {
        checkPermission();
    }

    @Override
    void getNotificationDescriptions(ListIterator<PathElement> iterator, Map<String, NotificationEntry> providers, boolean inherited) {
        checkPermission();
    }

    @Override
    void getInheritedNotificationEntries(Map<String, NotificationEntry> providers) {
        checkPermission();
    }

    @Override
    Set<RuntimeCapability> getCapabilities(ListIterator<PathElement> iterator) {
        return Collections.emptySet();
    }

    @Override
    Set<RuntimeCapability> getIncorporatingCapabilities(ListIterator<PathElement> iterator) {
        return Collections.emptySet();
    }

    @Override
    Set<CapabilityReferenceRecorder> getRequirements(ListIterator<PathElement> iterator) {
        return Collections.emptySet();
    }

    @Override
    DescriptionProvider getModelDescription(final ListIterator<PathElement> iterator) {
        checkPermission();
        return this;
    }

    @Override
    Set<String> getAttributeNames(final ListIterator<PathElement> iterator) {
        checkPermission();
        if (iterator.hasNext()) {
            return Collections.emptySet();
        } else {
            final Map<String, AttributeAccess> snapshot = attributesUpdater.get(this);
            return snapshot.keySet();
        }
    }

    @Override
    Map<String, AttributeAccess> getAttributes(final ListIterator<PathElement> iterator) {
        checkPermission();
        if (iterator.hasNext()) {
            return Collections.emptyMap();
        } else {
            return attributesUpdater.get(this);
        }
    }

    @Override
    Set<String> getChildNames(final ListIterator<PathElement> iterator) {
        checkPermission();
        return Collections.emptySet();
    }

    @Override
    Set<PathElement> getChildAddresses(final ListIterator<PathElement> iterator) {
        checkPermission();
        return Collections.emptySet();
    }

    @Override
    AttributeAccess getAttributeAccess(final ListIterator<PathElement> iterator, final String attributeName) {
        checkPermission();
        if (iterator.hasNext()) {
            return null;
        } else {
            final Map<String, AttributeAccess> snapshot = attributesUpdater.get(this);
            return snapshot.get(attributeName);
        }
    }

    @Override
    ProxyController getProxyController(ListIterator<PathElement> iterator) {
        checkPermission();
        return proxyController;
    }

    @Override
    void getProxyControllers(ListIterator<PathElement> iterator, Set<ProxyController> controllers) {
        checkPermission();
        controllers.add(proxyController);
    }

    @Override
    ManagementResourceRegistration getResourceRegistration(ListIterator<PathElement> iterator) {
        // BES 2011/06/14 I do not see why the IAE makes sense, so...
//        if (!iterator.hasNext()) {
//            return this;
//        }
//        throw new IllegalArgumentException("Can't get child registrations of a proxy");
        PathAddress childAddress = null;
        if (iterator.hasNext()) {
            childAddress = getPathAddress();
            while (iterator.hasNext()) {
                childAddress = childAddress.append(iterator.next());
            }
        }
        checkPermission();
        return childAddress == null ? this : new ChildRegistration(childAddress);
    }

    @Override
    public ModelNode getModelDescription(Locale locale) {
        checkPermission();
        //TODO
        return new ModelNode();
    }

    private IllegalArgumentException alreadyRegistered() {
        return ControllerLogger.ROOT_LOGGER.proxyHandlerAlreadyRegistered(getLocationString());
    }

    private IllegalArgumentException alreadyRegistered(final String type, final String name) {
        return ControllerLogger.ROOT_LOGGER.alreadyRegistered(type, name, getLocationString());
    }

    private IllegalArgumentException operationNotRegisteredException(String op, PathElement address) {
        return ControllerLogger.ROOT_LOGGER.operationNotRegisteredException(op, PathAddress.pathAddress(address));
    }

    @Override
    public AliasEntry getAliasEntry() {
        checkPermission();
        return null;
    }

    @Override
    public void setOrderedChild(String key) {
        throw alreadyRegistered();
    }

    @Override
    protected void registerAlias(PathElement address, AliasEntry alias, AbstractResourceRegistration target) {
        throw ControllerLogger.ROOT_LOGGER.proxyHandlerAlreadyRegistered(getLocationString());
    }

    @Override
    public boolean isOrderedChildResource() {
        checkPermission();
        return false;
    }

    @Override
    Set<String> getOrderedChildTypes(ListIterator<PathElement> iterator) {
        checkPermission();
        return Collections.emptySet();
    }

    @Override
    public boolean isFeature() {
        return false;
    }

    @Override
    public void registerAdditionalRuntimePackages(RuntimePackageDependency... pkgs) {
        throw alreadyRegistered();
    }

    @Override
    public Set<RuntimePackageDependency> getAdditionalRuntimePackages() {
        return Collections.emptySet();
    }

    /**
     * Registration meant to represent a child registration on the proxied process.
     * Differs from ProxyControllerRegistration in that it never provides locally
     * registered handlers or attributes. See WFCORE-229.
     */
    private class ChildRegistration extends DelegatingManagementResourceRegistration {
        private final PathAddress pathAddress;

        public ChildRegistration(PathAddress pathAddress) {
            super(ProxyControllerRegistration.this);
            this.pathAddress = pathAddress;
        }

        @Override
        public PathAddress getPathAddress() {
            return pathAddress;
        }

        @Override
        public OperationEntry getOperationEntry(PathAddress address, String operationName) {
            checkPermission();
            return ProxyControllerRegistration.this.operationEntry;
        }

        @Override
        public ManagementResourceRegistration getParent() {
            PathAddress parentAddress = ProxyControllerRegistration.this.getPathAddress();
            if (pathAddress.size() == parentAddress.size() + 1) {
                return ProxyControllerRegistration.this;
            } else {
                return new ChildRegistration(pathAddress.getParent());
            }
        }

        @Override
        public OperationStepHandler getOperationHandler(PathAddress address, String operationName) {
            return getOperationEntry(address, operationName).getOperationHandler();
        }

        @Override
        public DescriptionProvider getOperationDescription(PathAddress address, String operationName) {
            return getOperationEntry(address, operationName).getDescriptionProvider();
        }

        @Override
        public Set<OperationEntry.Flag> getOperationFlags(PathAddress address, String operationName) {
            return getOperationEntry(address, operationName).getFlags();
        }

        @Override
        public Set<String> getAttributeNames(PathAddress address) {
            checkPermission();
            return Collections.emptySet();
        }

        @Override
        public AttributeAccess getAttributeAccess(PathAddress address, String attributeName) {
            checkPermission();
            return null;
        }

        @Override
        public ManagementResourceRegistration getOverrideModel(String name) {
            checkPermission();
            return null;
        }

        @Override
        public ManagementResourceRegistration getSubModel(PathAddress address) {
            if (address.size() == 0) {
                return this;
            }
            return new ChildRegistration(pathAddress.append(address));
        }

        @Override
        public boolean isOrderedChildResource() {
            return false;
        }

        @Override
        public Set<String> getOrderedChildTypes() {
            return Collections.emptySet();
        }

        // For all other methods, ProxyControllerRegistration behavior is ok, so delegate
    }
}
