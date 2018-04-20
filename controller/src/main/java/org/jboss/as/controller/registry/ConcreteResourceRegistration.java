/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.registry;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.AccessConstraintUtilizationRegistry;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.AttributeAccess.AccessType;
import org.jboss.as.controller.registry.AttributeAccess.Storage;
import org.wildfly.common.Assert;

final class ConcreteResourceRegistration extends AbstractResourceRegistration {

    private Map<String, NodeSubregistry> children;

    private Map<String, OperationEntry> operations;

    private Map<String, NotificationEntry> notifications;

    private final ResourceDefinition resourceDefinition;
    private final List<AccessConstraintDefinition> accessConstraintDefinitions;

    // We assume at least 2 attrs, so just instantiate a hash map
    private final Map<String, AttributeAccess> attributes = new HashMap<>();

    private Set <String> orderedChildTypes;

    private boolean runtimeOnly;
    private final boolean ordered;
    private final AccessConstraintUtilizationRegistry constraintUtilizationRegistry;
    private final CapabilityRegistry capabilityRegistry;

    private Set<RuntimeCapability> capabilities;

    private Set<RuntimeCapability> incorporatingCapabilities;

    private Set<CapabilityReferenceRecorder> requirements;

    private final Lock readLock;
    private final Lock writeLock;

    /** Constructor for a root MRR */
    ConcreteResourceRegistration(final ResourceDefinition definition,
                                 final AccessConstraintUtilizationRegistry constraintUtilizationRegistry,
                                 final CapabilityRegistry capabilityRegistry,
                                 final ProcessType processType) {
        super(processType);
        this.constraintUtilizationRegistry = constraintUtilizationRegistry;
        this.capabilityRegistry = capabilityRegistry;
        this.resourceDefinition = definition;
        this.runtimeOnly = definition.isRuntime();
        this.accessConstraintDefinitions = buildAccessConstraints();
        this.ordered = false;
        // For a root MRR we expect concurrent reads in critical performance code, i.e. boot
        // So we use a read-write lock
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();
    }

    /** Constructor for a non-root MRR */
    ConcreteResourceRegistration(final String valueString, final NodeSubregistry parent, final ResourceDefinition definition,
                                 final AccessConstraintUtilizationRegistry constraintUtilizationRegistry,
                                 final boolean ordered, CapabilityRegistry capabilityRegistry) {
        super(valueString, parent);
        this.constraintUtilizationRegistry = constraintUtilizationRegistry;
        this.capabilityRegistry = capabilityRegistry;
        this.resourceDefinition = definition;
        this.runtimeOnly = definition.isRuntime();
        this.accessConstraintDefinitions = buildAccessConstraints();
        this.ordered = ordered;
        // For non-root MRRs we don't expect much in the way of concurrent reads in performance
        // critical situations, so we want lock/unlock to be as simple and fast as possible
        // So we just use a single non-r/w lock for both reads and writes
        this.readLock = this.writeLock = new ReentrantLock();
    }

    void beginInitialization() {
        writeLock.lock();
    }

    void initialized() {
        writeLock.unlock();
    }

    @Override
    public int getMaxOccurs() {
        return resourceDefinition.getMaxOccurs();
    }

    @Override
    public int getMinOccurs() {
        return resourceDefinition.getMinOccurs();
    }

    @Override
    public boolean isFeature() {
        return resourceDefinition.isFeature();
    }

    @Override
    public boolean isRuntimeOnly() {
        checkPermission();
        readLock.lock();
        try {
            return runtimeOnly;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void setRuntimeOnly(final boolean runtimeOnly) {
        checkPermission();
        writeLock.lock();
        try {
            this.runtimeOnly = runtimeOnly;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean isRemote() {
        checkPermission();
        return false;
    }

    @Override
    public boolean isOrderedChildResource() {
        return ordered;
    }

    @Override
    Set<String> getOrderedChildTypes(ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = getSubregistry(next.getKey());
            if (subregistry == null) {
                return Collections.emptySet();
            }
            return subregistry.getOrderedChildTypes(iterator, next.getValue());
        } else {
            checkPermission();
            readLock.lock();
            try {
                return orderedChildTypes == null ? Collections.emptySet() : new HashSet<>(orderedChildTypes);
            } finally {
                readLock.unlock();
            }
        }
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        checkPermission();
        return accessConstraintDefinitions;
    }

    private List<AccessConstraintDefinition> buildAccessConstraints() {
        AbstractResourceRegistration reg = this;
        List<AccessConstraintDefinition> list = new ArrayList<AccessConstraintDefinition>();
        while (reg != null) {
            reg.addAccessConstraints(list);
            NodeSubregistry parent = reg.getParentSubRegistry();
            reg = parent == null ? null : parent.getParent();
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    void addAccessConstraints(List<AccessConstraintDefinition> list) {
        list.addAll(resourceDefinition.getAccessConstraints());
    }

    @Override
    public ManagementResourceRegistration registerSubModel(final ResourceDefinition resourceDefinition) {
        Assert.checkNotNullParam("resourceDefinition", resourceDefinition);
        final PathElement address = resourceDefinition.getPathElement();
        if (address == null) {
            throw ControllerLogger.ROOT_LOGGER.cannotRegisterSubmodelWithNullPath();
        }
        if (isRuntimeOnly() && !resourceDefinition.isRuntime()) {
            throw ControllerLogger.ROOT_LOGGER.cannotRegisterSubmodel();
        }
        final ManagementResourceRegistration existing = getSubRegistration(PathAddress.pathAddress(address));
        if (existing != null && existing.getPathAddress().getLastElement().getValue().equals(address.getValue())) {
            throw ControllerLogger.ROOT_LOGGER.nodeAlreadyRegistered(existing.getPathAddress().toCLIStyleString());
        }
        final NodeSubregistry child = getOrCreateSubregistry(address.getKey());
        return child.registerChild(address.getValue(), resourceDefinition);
    }

    @Override
    public void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler, boolean inherited) {
        checkPermission();
        String opName = definition.getName();
        OperationEntry entry = new OperationEntry(definition, handler, inherited);
        writeLock.lock();
        try {
            if (operations == null) {
                operations = new HashMap<>();
            } else if (operations.containsKey(opName)) {
                throw alreadyRegistered("operation handler", opName);
            }
            operations.put(opName, entry);
            if (constraintUtilizationRegistry != null) {
                for (AccessConstraintDefinition acd : definition.getAccessConstraints()) {
                    constraintUtilizationRegistry.registerAccessConstraintOperationUtilization(acd.getKey(), getPathAddress(), opName);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void unregisterSubModel(final PathElement address) throws IllegalArgumentException {
        writeLock.lock();
        try {
            final NodeSubregistry subregistry = getSubregistry(address.getKey());

            if (subregistry != null) {
                //we remove also children, effectively doing recursive delete
                // WFCORE-3410 -- do not call the getChildAddresses(PathAddress) variant
                // that results in querying (and thus read locking) nodes above
                // this one as that can lead to deadlocks.
                // Reading from the root would allow the call to find addresses
                // associated with a wildcard registration for which this MRR
                // is an override (if it is such an MRR.) But, we only end up
                // reading our own subregistry to find the MRR
                // to invoke the recursive delete on anyway, and an override MRR
                // trying to somehow remove children from the related wildcard MRR
                // would be wrong, so there's no point reading from the root to
                // find those kinds of addresses.
                Set<PathElement> childAddresses = getChildAddresses(PathAddress.pathAddress(address).iterator());
                if (childAddresses != null) {
                    ManagementResourceRegistration registration = subregistry.getResourceRegistration(PathAddress.EMPTY_ADDRESS.iterator(), address.getValue());
                    if(!registration.isAlias()) {
                        for (PathElement a : childAddresses) {
                            registration.unregisterSubModel(a);
                        }
                    }
                }
                subregistry.unregisterSubModel(address.getValue());
            }
            if (constraintUtilizationRegistry != null) {
                constraintUtilizationRegistry.unregisterAccessConstraintUtilizations(getPathAddress().append(address));
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    OperationEntry getOperationEntry(final ListIterator<PathElement> iterator, final String operationName, OperationEntry inherited) {
        if (iterator.hasNext()) {
            final NodeSubregistry subregistry;
            final OperationEntry inheritance;
            final PathElement next = iterator.next();
            readLock.lock();
            try {
                subregistry = children == null ? null : children.get(next.getKey());
                if (subregistry == null) {
                    return null;
                }
                OperationEntry ourInherited = getInheritableOperationEntryLocked(operationName);
                inheritance = ourInherited == null ? inherited : ourInherited;
            } finally {
                readLock.unlock();
            }
            return subregistry.getOperationEntry(iterator, next.getValue(), operationName, inheritance);
        } else {
            checkPermission();
            final OperationEntry entry;
            readLock.lock();
            try {
                entry = operations == null ? null : operations.get(operationName);
            } finally {
                readLock.unlock();
            }
            return entry == null ? inherited : entry;
        }
    }

    @Override
    OperationEntry getInheritableOperationEntry(final String operationName) {
        checkPermission();
        readLock.lock();
        try {
            return getInheritableOperationEntryLocked(operationName);
        } finally {
            readLock.unlock();
        }
    }

    // Only call with the read lock held
    private OperationEntry getInheritableOperationEntryLocked(final String operationName) {
        final OperationEntry entry = operations == null ? null : operations.get(operationName);
        if (entry != null && entry.isInherited()) {
            return entry;
        }
        return null;
    }

    @Override
    void getOperationDescriptions(final ListIterator<PathElement> iterator, final Map<String, OperationEntry> providers, final boolean inherited) {

        if (!iterator.hasNext() ) {
            checkPermission();
            readLock.lock();
            try {
                if (operations != null) {
                    providers.putAll(operations);
                }
            } finally {
                readLock.unlock();
            }
            if (inherited) {
                getInheritedOperations(providers, true);
            }
            return;
        }
        final PathElement next = iterator.next();
        try {
            final String key = next.getKey();
            final NodeSubregistry subregistry = getSubregistry(key);
            if (subregistry != null) {
                subregistry.getHandlers(iterator, next.getValue(), providers, inherited);
            }
        } finally {
            iterator.previous();
        }
    }

    @Override
    void getInheritedOperationEntries(final Map<String, OperationEntry> providers) {
        checkPermission();
        readLock.lock();
        try {
            if (operations != null) {
                for (final Map.Entry<String, OperationEntry> entry : operations.entrySet()) {
                    if (entry.getValue().isInherited() && !providers.containsKey(entry.getKey())) {
                        providers.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void unregisterOperationHandler(final String operationName) {
        checkPermission();
        writeLock.lock();
        try {
            if (operations == null || operations.remove(operationName) == null) {
                throw operationNotRegisteredException(operationName, resourceDefinition.getPathElement());
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void registerReadWriteAttribute(final AttributeDefinition definition, final OperationStepHandler readHandler, final OperationStepHandler writeHandler) {
        assert definition.getUndefinedMetricValue() == null : "Attributes cannot have undefined metric value set";
        checkPermission();
        if (!isAttributeRegistrationAllowed(definition)) {
            return;
        }
        AttributeAccess.Storage storage = definition.getImmutableFlags().contains(AttributeAccess.Flag.STORAGE_RUNTIME) ? Storage.RUNTIME : Storage.CONFIGURATION;
        AttributeAccess aa = new AttributeAccess(AccessType.READ_WRITE, storage, readHandler, writeHandler, definition);
        storeAttribute(definition, aa);
    }

    @Override
    public void registerReadOnlyAttribute(final AttributeDefinition definition, final OperationStepHandler readHandler) {
        assert definition.getUndefinedMetricValue() == null : "Attributes cannot have undefined metric value set";
        checkPermission();
        if (!isAttributeRegistrationAllowed(definition)) {
            return;
        }
        AttributeAccess.Storage storage = definition.getImmutableFlags().contains(AttributeAccess.Flag.STORAGE_RUNTIME) ? Storage.RUNTIME : Storage.CONFIGURATION;
        AttributeAccess aa = new AttributeAccess(AccessType.READ_ONLY, storage, readHandler, null, definition);
        storeAttribute(definition, aa);
    }

    @Override
    public void unregisterAttribute(String attributeName) {
        checkPermission();
        writeLock.lock();
        try {
            attributes.remove(attributeName);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void registerNotification(NotificationDefinition notification, boolean inherited) {
        checkPermission();
        String type = notification.getType();
        NotificationEntry entry = new NotificationEntry(notification.getDescriptionProvider(), inherited);
        writeLock.lock();
        try {
            if (notifications == null) {
                notifications = Collections.singletonMap(type, entry);
            } else {
                if (notifications.containsKey(type)) {
                    throw alreadyRegistered(NOTIFICATION, type);
                }
                if (notifications.size() == 1) {
                    notifications = new HashMap<>(notifications);
                }
                notifications.put(type, entry);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void registerNotification(NotificationDefinition notification) {
        registerNotification(notification, false);
    }

    @Override
    public void unregisterNotification(String notificationType) {
        checkPermission();
        writeLock.lock();
        try {
            if (notifications != null) {
                notifications.remove(notificationType);
            }
        } finally {
            writeLock.unlock();
        }
    }


    @Override
    public void registerMetric(AttributeDefinition definition, OperationStepHandler metricHandler) {
        assert assertMetricValues(definition); //The real message will be in an assertion thrown by assertMetricValues
        checkPermission();
        if (isAttributeRegistrationAllowed(definition) && !isProfileResource()) {
            AttributeAccess aa = new AttributeAccess(AccessType.METRIC, AttributeAccess.Storage.RUNTIME, metricHandler, null, definition);
            storeAttribute(definition, aa);
        }
    }

    /**
     * Metrics and runtime attributes are always registered in the MMR only for normal server.
     * If they are flagged with {@link AttributeAccess.Flag#STORAGE_RUNTIME} and
     * {@link AttributeAccess.Flag#RUNTIME_SERVICE_NOT_REQUIRED}, they are registered regardless of the process type.
     */
    private boolean isAttributeRegistrationAllowed(AttributeDefinition definition) {
        boolean runtime = definition.getImmutableFlags().contains(AttributeAccess.Flag.STORAGE_RUNTIME);
        if (!runtime) {
            return true;
        }
        boolean runtimeServiceNotRequired = definition.getImmutableFlags().contains(AttributeAccess.Flag.RUNTIME_SERVICE_NOT_REQUIRED);
        if (runtimeServiceNotRequired) {
            return true;
        }
        return getProcessType().isServer();
    }

    private boolean isProfileResource() {
        return !getProcessType().isServer() && getPathAddress().size() > 1 && PROFILE.equals(getPathAddress().getElement(0).getKey());
    }

    private void storeAttribute(AttributeDefinition definition, AttributeAccess aa) {
        String attributeName = definition.getName();
        writeLock.lock();
        try {
            if (attributes.containsKey(attributeName)) {
                throw alreadyRegistered("attribute", attributeName);
            }
            attributes.put(attributeName, aa);
            registerAttributeAccessConstraints(definition);
        } finally {
            writeLock.unlock();
        }
    }

    private boolean assertMetricValues(AttributeDefinition definition) {
        if (!definition.isRequired() && definition.getUndefinedMetricValue() != null) {
            assert false : "Nillable metric has an undefined metric value for '" + definition.getName() + "'";
        }
        // BES 2015/08/28 The WFCORE-831 spec does not require this assertion. The requirement is that read-attribute
        // not return undefined, but AttributeDefinition.getUndefinedMetricValue() is not the only way to achieve this.
        // The read-attribute handler can simply always work.
//        if (!definition.isAllowNull() && definition.getUndefinedMetricValue() == null) {
//            assert false : "Non-nillable metric does not have an undefined metric value for '" + definition.getName() + "'";
//        }
        if (definition.getDefaultValue() != null) {
            assert false : "Metrics cannot have a default value for '" + definition.getName() + "'";
        }
        return true;
    }

    private void registerAttributeAccessConstraints(AttributeDefinition ad) {
        if (constraintUtilizationRegistry != null) {
            for (AccessConstraintDefinition acd : ad.getAccessConstraints()) {
                constraintUtilizationRegistry.registerAccessConstraintAttributeUtilization(acd.getKey(), getPathAddress(), ad.getName());
            }
        }
    }

    @Override
    void getNotificationDescriptions(final ListIterator<PathElement> iterator, final Map<String, NotificationEntry> providers, final boolean inherited) {

        if (!iterator.hasNext() ) {
            checkPermission();
            readLock.lock();
            try {
                if (notifications != null) {
                    providers.putAll(notifications);
                }
            } finally {
                readLock.unlock();
            }
            if (inherited) {
                getInheritedNotifications(providers, true);
            }
            return;
        }
        final PathElement next = iterator.next();
        try {
            final String key = next.getKey();
            final NodeSubregistry subregistry = getSubregistry(key);
            if (subregistry != null) {
                subregistry.getNotificationDescriptions(iterator, next.getValue(), providers, inherited);
            }
        } finally {
            iterator.previous();
        }
    }

    private NodeSubregistry getSubregistry(String key) {
        readLock.lock();
        try {
            return children == null ? null : children.get(key);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    void getInheritedNotificationEntries(final Map<String, NotificationEntry> providers) {
        checkPermission();
        readLock.lock();
        try {
            if (notifications != null) {
                for (final Map.Entry<String, NotificationEntry> entry : notifications.entrySet()) {
                    if (entry.getValue().isInherited() && !providers.containsKey(entry.getKey())) {
                        providers.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    @Override
    Set<RuntimeCapability> getCapabilities(ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = getSubregistry(next.getKey());
            if (subregistry == null) {
                return Collections.emptySet();
            }
            return subregistry.getCapabilities(iterator, next.getValue());
        } else {
            checkPermission();
            readLock.lock();
            try {
                return capabilities == null ? Collections.emptySet() : Collections.unmodifiableSet(capabilities);
            } finally {
                readLock.unlock();
            }
        }
    }

    @Override
    Set<RuntimeCapability> getIncorporatingCapabilities(ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = getSubregistry(next.getKey());
            if (subregistry == null) {
                return Collections.emptySet();
            }
            return subregistry.getIncorporatingCapabilities(iterator, next.getValue());
        } else {
            checkPermission();
            readLock.lock();
            try {
                Set<RuntimeCapability> result;
                if (incorporatingCapabilities != null) {
                    result = incorporatingCapabilities;
                } else if (capabilities != null && !capabilities.isEmpty()) {
                    result = Collections.emptySet();
                } else {
                    result = null;
                }
                return result;
            } finally {
                readLock.unlock();
            }
        }
    }


    @Override
    public void registerProxyController(final PathElement address, final ProxyController controller) throws IllegalArgumentException {
        final ManagementResourceRegistration existing = getSubRegistration(PathAddress.pathAddress(address));
        if (existing != null && existing.getPathAddress().getLastElement().getValue().equals(address.getValue())) {
            throw ControllerLogger.ROOT_LOGGER.nodeAlreadyRegistered(existing.getPathAddress().toCLIStyleString());
        }
        getOrCreateSubregistry(address.getKey()).registerProxyController(address.getValue(), controller);
    }

    @Override
    public void unregisterProxyController(final PathElement address) throws IllegalArgumentException {
        final NodeSubregistry subregistry = getSubregistry(address.getKey());
        if (subregistry != null) {
            subregistry.unregisterProxyController(address.getValue());
        }
    }

    @Override
    public void registerAlias(PathElement address, AliasEntry alias, AbstractResourceRegistration target) {
        getOrCreateSubregistry(address.getKey()).registerAlias(address.getValue(), alias, target);
    }

    @Override
    public void unregisterAlias(PathElement address) {
        final NodeSubregistry subregistry = getSubregistry(address.getKey());
        if (subregistry != null) {
            subregistry.unregisterAlias(address.getValue());
        }
    }

    @Override
    public void registerCapability(RuntimeCapability capability) {
        writeLock.lock();
        try {
            if (capabilities == null) {
                capabilities = new HashSet<>();
            }
            capabilities.add(capability);
            if (capabilityRegistry != null) {
                capabilityRegistry.registerPossibleCapability(capability, getPathAddress());
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void registerIncorporatingCapabilities(Set<RuntimeCapability> capabilities) {
        writeLock.lock();
        try {
            if (capabilities == null) {
                incorporatingCapabilities = null;
            } else if (capabilities.isEmpty()) {
                incorporatingCapabilities = Collections.emptySet();
            } else {
                incorporatingCapabilities = Collections.unmodifiableSet(new HashSet<>(capabilities));
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void registerRequirements(Set<CapabilityReferenceRecorder> requirements) {
        writeLock.lock();
        try {
            if (requirements == null || requirements.isEmpty()) {
                this.requirements = Collections.emptySet();
            } else {
                this.requirements = Collections.unmodifiableSet(new HashSet<>(requirements));
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    Set<CapabilityReferenceRecorder> getRequirements(ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = getSubregistry(next.getKey());
            if (subregistry == null) {
                return Collections.emptySet();
            }
            return subregistry.getRequirements(iterator, next.getValue());
        } else {
            checkPermission();
            readLock.lock();
            try {
                return requirements == null ? Collections.emptySet() : Collections.unmodifiableSet(requirements);
            } finally {
                readLock.unlock();
            }
        }
    }

    NodeSubregistry getOrCreateSubregistry(final String key) {

        writeLock.lock();
        try {
            final NodeSubregistry subregistry = children == null ? null : children.get(key);
            if (subregistry != null) {
                return subregistry;
            } else {
                checkPermission();
                final NodeSubregistry newRegistry = new NodeSubregistry(key, this, constraintUtilizationRegistry, capabilityRegistry);
                if (children == null) {
                    children = Collections.singletonMap(key, newRegistry);
                } else {
                    if (children.size() == 1) {
                        children = new HashMap<>(children);
                    }
                    children.put(key, newRegistry);
                }
                return newRegistry;
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    DescriptionProvider getModelDescription(final ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = getSubregistry(next.getKey());
            if (subregistry == null) {
                return null;
            }
            return subregistry.getModelDescription(iterator, next.getValue());
        } else {
            checkPermission();
            return resourceDefinition.getDescriptionProvider(this);
        }
    }

    @Override
    Set<String> getAttributeNames(final ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = getSubregistry(next.getKey());
            if (subregistry == null) {
                return Collections.emptySet();
            }
            return subregistry.getAttributeNames(iterator, next.getValue());
        } else {
            checkPermission();
            synchronized (this) {
                return new HashSet<>(attributes.keySet());
            }
        }
    }

    @Override
    AttributeAccess getAttributeAccess(final ListIterator<PathElement> iterator, final String attributeName) {

        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = getSubregistry(next.getKey());
            if (subregistry == null) {
                return null;
            }
            return subregistry.getAttributeAccess(iterator, next.getValue(), attributeName);
        } else {
            checkPermission();
            readLock.lock();
            try {
                return attributes.get(attributeName);
            } finally {
                readLock.unlock();
            }
        }
    }

    @Override
    Map<String, AttributeAccess> getAttributes(final ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = getSubregistry(next.getKey());
            if (subregistry == null) {
                return Collections.emptyMap();
            }
            return subregistry.getAttributes(iterator, next.getValue());
        } else {
            checkPermission();
            synchronized (this) {
                return new HashMap<>(attributes);
            }
        }
    }

    @Override
    Set<String> getChildNames(final ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = getSubregistry(next.getKey());
            if (subregistry == null) {
                return Collections.emptySet();
            }
            return subregistry.getChildNames(iterator, next.getValue());
        } else {
            checkPermission();
            readLock.lock();
            try {
                if (children != null) {
                    return Collections.unmodifiableSet(children.keySet());
                }
                return Collections.emptySet();
            } finally {
                readLock.unlock();
            }
        }
    }

    @Override
    Set<PathElement> getChildAddresses(final ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = getSubregistry(next.getKey());
            if (subregistry == null) {
                return Collections.emptySet();
            }
            return subregistry.getChildAddresses(iterator, next.getValue());
        } else {
            checkPermission();
            readLock.lock();
            try {
                if (children != null) {
                    final Set<PathElement> elements = new HashSet<PathElement>();
                    for (final Map.Entry<String, NodeSubregistry> entry : children.entrySet()) {
                        for (final String entryChild : entry.getValue().getChildNames()) {
                            elements.add(PathElement.pathElement(entry.getKey(), entryChild));
                        }
                    }
                    return elements;
                }
            } finally {
                readLock.unlock();
            }
            return Collections.emptySet();
        }
    }

    @Override
    ProxyController getProxyController(ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = getSubregistry(next.getKey());
            if (subregistry == null) {
                return null;
            }
            return subregistry.getProxyController(iterator, next.getValue());
        } else {
            return null;
        }
    }

    @Override
    void getProxyControllers(ListIterator<PathElement> iterator, Set<ProxyController> controllers) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = getSubregistry(next.getKey());
            if (subregistry == null) {
                return;
            }
            if (next.isWildcard()) {
                subregistry.getProxyControllers(iterator, null, controllers);
            } else if (next.isMultiTarget()) {
                for(final String value : next.getSegments()) {
                    subregistry.getProxyControllers(iterator, value, controllers);
                }
            } else {
                subregistry.getProxyControllers(iterator, next.getValue(), controllers);
            }
        } else {
            readLock.lock();
            try {
                if (children != null) {
                    for (NodeSubregistry subregistry : children.values()) {
                        subregistry.getProxyControllers(iterator, null, controllers);
                    }
                }
            } finally {
                readLock.unlock();
            }
        }
    }

    @Override
    ManagementResourceRegistration getResourceRegistration(ListIterator<PathElement> iterator) {
        if (! iterator.hasNext()) {
            checkPermission();
            return this;
        } else {
            final PathElement address = iterator.next();
            final NodeSubregistry subregistry = getSubregistry(address.getKey());
            if (subregistry != null) {
                return subregistry.getResourceRegistration(iterator, address.getValue());
            } else {
                return null;
            }
        }
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
    protected void setOrderedChild(String type) {
        writeLock.lock();
        try {
            if (orderedChildTypes == null) {
                orderedChildTypes = Collections.singleton(type);
            } else {
                if (orderedChildTypes.size() == 1) {
                    orderedChildTypes = new HashSet<>(orderedChildTypes);
                }
                orderedChildTypes.add(type);
            }
        } finally {
            writeLock.unlock();
        }
    }
}

