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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.AccessConstraintUtilizationRegistry;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.AttributeAccess.AccessType;
import org.jboss.as.controller.registry.AttributeAccess.Storage;

@SuppressWarnings("deprecation")
final class ConcreteResourceRegistration extends AbstractResourceRegistration {

    @SuppressWarnings("unused")
    private volatile Map<String, NodeSubregistry> children;

    @SuppressWarnings("unused")
    private volatile Map<String, OperationEntry> operations;

    @SuppressWarnings("unused")
    private volatile Map<String, NotificationEntry> notifications;

    private final ResourceDefinition resourceDefinition;
    private final List<AccessConstraintDefinition> accessConstraintDefinitions;

    @SuppressWarnings("unused")
    private volatile Map<String, AttributeAccess> attributes;

    @SuppressWarnings("unused")
    private volatile Map<String, Empty> orderedChildTypes;

    private final AtomicBoolean runtimeOnly = new AtomicBoolean();
    private final boolean ordered;
    private final AccessConstraintUtilizationRegistry constraintUtilizationRegistry;
    private final CapabilityRegistry capabilityRegistry;

    private static final AtomicMapFieldUpdater<ConcreteResourceRegistration, String, NodeSubregistry> childrenUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(ConcreteResourceRegistration.class, Map.class, "children"));
    private static final AtomicMapFieldUpdater<ConcreteResourceRegistration, String, OperationEntry> operationsUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(ConcreteResourceRegistration.class, Map.class, "operations"));
    private static final AtomicMapFieldUpdater<ConcreteResourceRegistration, String, NotificationEntry> notificationsUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(ConcreteResourceRegistration.class, Map.class, "notifications"));
    private static final AtomicMapFieldUpdater<ConcreteResourceRegistration, String, AttributeAccess> attributesUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(ConcreteResourceRegistration.class, Map.class, "attributes"));
    private static final AtomicMapFieldUpdater<ConcreteResourceRegistration, String, Empty> orderedChildUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(ConcreteResourceRegistration.class, Map.class, "orderedChildTypes"));

    private final Set<RuntimeCapability>  capabilities = new CopyOnWriteArraySet<>();

    ConcreteResourceRegistration(final String valueString, final NodeSubregistry parent, final ResourceDefinition definition,
                                 final AccessConstraintUtilizationRegistry constraintUtilizationRegistry,
                                 final boolean ordered, CapabilityRegistry capabilityRegistry) {
        super(valueString, parent);
        this.constraintUtilizationRegistry = constraintUtilizationRegistry;
        this.capabilityRegistry = capabilityRegistry;
        childrenUpdater.clear(this);
        operationsUpdater.clear(this);
        attributesUpdater.clear(this);
        notificationsUpdater.clear(this);
        orderedChildUpdater.clear(this);
        this.resourceDefinition = definition;
        this.runtimeOnly.set(definition.isRuntime());
        this.accessConstraintDefinitions = buildAccessConstraints();
        this.ordered = ordered;
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
    public boolean isRuntimeOnly() {
        checkPermission();
        return runtimeOnly.get();
    }

    @Override
    public void setRuntimeOnly(final boolean runtimeOnly) {
        checkPermission();
        this.runtimeOnly.set(runtimeOnly);
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
            final NodeSubregistry subregistry = children.get(next.getKey());
            if (subregistry == null) {
                return Collections.emptySet();
            }
            return subregistry.getOrderedChildTypes(iterator, next.getValue());
        } else {
            checkPermission();
            Map<String, Empty> snapshot = orderedChildUpdater.get(this);
            if (snapshot == null || snapshot.size() == 0) {
                return Collections.emptySet();
            }

            return new HashSet<>(snapshot.keySet());
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
            NodeSubregistry parent = reg.getParent();
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
        if (resourceDefinition == null) {
            throw ControllerLogger.ROOT_LOGGER.nullVar("resourceDefinition");
        }
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
        final String key = address.getKey();
        final NodeSubregistry child = getOrCreateSubregistry(key);
        final boolean ordered = resourceDefinition.isOrderedChild();
        final AbstractResourceRegistration resourceRegistration =
                new ConcreteResourceRegistration(address.getValue(), child, resourceDefinition, constraintUtilizationRegistry, ordered, capabilityRegistry);
        if (ordered) {
            AbstractResourceRegistration parentRegistration = child.getParent();
            parentRegistration.setOrderedChild(key);
        }
        resourceDefinition.registerAttributes(resourceRegistration);
        resourceDefinition.registerOperations(resourceRegistration);
        resourceDefinition.registerNotifications(resourceRegistration);
        resourceDefinition.registerChildren(resourceRegistration);
        resourceDefinition.registerCapabilities(resourceRegistration);
        if (constraintUtilizationRegistry != null) {
            PathAddress childAddress = getPathAddress().append(address);
            List<AccessConstraintDefinition> constraintDefinitions = resourceDefinition.getAccessConstraints();
            for (AccessConstraintDefinition acd : constraintDefinitions) {
                constraintUtilizationRegistry.registerAccessConstraintResourceUtilization(acd.getKey(), childAddress);
            }
        }
        return child.register(address.getValue(), resourceRegistration);
    }

    @Override
    public void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler, boolean inherited) {
        checkPermission();
        if (operationsUpdater.putIfAbsent(this, definition.getName(), new OperationEntry(handler, definition.getDescriptionProvider(), inherited, definition.getEntryType(),
                definition.getFlags(), definition.getAccessConstraints())) != null) {
            throw alreadyRegistered("operation handler", definition.getName());
        }
        registerOperationAccessConstraints(definition);
    }

    public void unregisterSubModel(final PathElement address) throws IllegalArgumentException {
        final Map<String, NodeSubregistry> snapshot = childrenUpdater.get(this);
        final NodeSubregistry subregistry = snapshot.get(address.getKey());
        if (subregistry != null) {
            subregistry.unregisterSubModel(address.getValue());
        }
        unregisterAccessConstraints(address);
    }

    @Override
    OperationEntry getOperationEntry(final ListIterator<PathElement> iterator, final String operationName, OperationEntry inherited) {
        if (iterator.hasNext()) {
            OperationEntry ourInherited = getInheritableOperationEntry(operationName);
            OperationEntry inheritance = ourInherited == null ? inherited : ourInherited;
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = children.get(next.getKey());
            if (subregistry == null) {
                return null;
            }
            return subregistry.getOperationEntry(iterator, next.getValue(), operationName, inheritance);
        } else {
            checkPermission();
            final OperationEntry entry = operationsUpdater.get(this, operationName);
            return entry == null ? inherited : entry;
        }
    }

    @Override
    OperationEntry getInheritableOperationEntry(final String operationName) {
        checkPermission();
        final OperationEntry entry = operationsUpdater.get(this, operationName);
        if (entry != null && entry.isInherited()) {
            return entry;
        }
        return null;
    }

    @Override
    void getOperationDescriptions(final ListIterator<PathElement> iterator, final Map<String, OperationEntry> providers, final boolean inherited) {

        if (!iterator.hasNext() ) {
            checkPermission();
            providers.putAll(operationsUpdater.get(this));
            if (inherited) {
                getInheritedOperations(providers, true);
            }
            return;
        }
        final PathElement next = iterator.next();
        try {
            final String key = next.getKey();
            final Map<String, NodeSubregistry> snapshot = childrenUpdater.get(this);
            final NodeSubregistry subregistry = snapshot.get(key);
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
        for (final Map.Entry<String, OperationEntry> entry : operationsUpdater.get(this).entrySet()) {
            if (entry.getValue().isInherited() && !providers.containsKey(entry.getKey())) {
                providers.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void unregisterOperationHandler(final String operationName) {
        checkPermission();
        if (operationsUpdater.remove(this, operationName) == null) {
            throw operationNotRegisteredException(operationName, resourceDefinition.getPathElement());
        }
    }

    @Override
    public void registerReadWriteAttribute(final AttributeDefinition definition, final OperationStepHandler readHandler, final OperationStepHandler writeHandler) {
        assert definition.getUndefinedMetricValue() == null : "Attributes cannot have undefined metric value set";
        checkPermission();
        final EnumSet<AttributeAccess.Flag> flags = definition.getFlags();
        final String attributeName = definition.getName();
        AttributeAccess.Storage storage = (flags != null && flags.contains(AttributeAccess.Flag.STORAGE_RUNTIME)) ? Storage.RUNTIME : Storage.CONFIGURATION;
        AttributeAccess aa = new AttributeAccess(AccessType.READ_WRITE, storage, readHandler, writeHandler, definition, flags);
        if (attributesUpdater.putIfAbsent(this, attributeName, aa) != null) {
            throw alreadyRegistered("attribute", attributeName);
        }
        registerAttributeAccessConstraints(definition);
    }

    @Override
    public void registerReadOnlyAttribute(final AttributeDefinition definition, final OperationStepHandler readHandler) {
        assert definition.getUndefinedMetricValue() == null : "Attributes cannot have undefined metric value set";
        checkPermission();
        final EnumSet<AttributeAccess.Flag> flags = definition.getFlags();
        final String attributeName = definition.getName();
        AttributeAccess.Storage storage = (flags != null && flags.contains(AttributeAccess.Flag.STORAGE_RUNTIME)) ? Storage.RUNTIME : Storage.CONFIGURATION;
        AttributeAccess aa = new AttributeAccess(AccessType.READ_ONLY, storage, readHandler, null, definition, flags);
        if (attributesUpdater.putIfAbsent(this, attributeName, aa) != null) {
            throw alreadyRegistered("attribute", attributeName);
        }
        registerAttributeAccessConstraints(definition);
    }

    @Override
    public void unregisterAttribute(String attributeName) {
        checkPermission();
        attributesUpdater.remove(this, attributeName);
    }

    @Override
    public void registerNotification(NotificationDefinition notification, boolean inherited) {
        NotificationEntry entry = new NotificationEntry(notification.getDescriptionProvider(), inherited);
        checkPermission();
        if (notificationsUpdater.putIfAbsent(this, notification.getType(), entry) != null) {
            throw alreadyRegistered(NOTIFICATION, notification.getType());
        }
    }

    @Override
    public void registerNotification(NotificationDefinition notification) {
        registerNotification(notification, false);
    }

    @Override
         public void unregisterNotification(String notificationType) {
        checkPermission();
        notificationsUpdater.remove(this, notificationType);
    }


    @Override
    public void registerMetric(AttributeDefinition definition, OperationStepHandler metricHandler) {
        assert assertMetricValues(definition); //The real message will be in an assertion thrown by assertMetricValues
        checkPermission();
        AttributeAccess aa = new AttributeAccess(AccessType.METRIC, AttributeAccess.Storage.RUNTIME, metricHandler, null, definition, definition.getFlags());
        if (attributesUpdater.putIfAbsent(this, definition.getName(), aa) != null) {
            throw alreadyRegistered("attribute", definition.getName());
        }
        registerAttributeAccessConstraints(definition);
    }

    private boolean assertMetricValues(AttributeDefinition definition) {
        if (definition.isAllowNull() && definition.getUndefinedMetricValue() != null) {
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

    private void registerOperationAccessConstraints(OperationDefinition od) {
        if (constraintUtilizationRegistry != null) {
            for (AccessConstraintDefinition acd : od.getAccessConstraints()) {
                constraintUtilizationRegistry.registerAccessConstraintOperationUtilization(acd.getKey(), getPathAddress(), od.getName());
            }
        }
    }

    private void unregisterAccessConstraints(PathElement childAddress) {
        if (constraintUtilizationRegistry != null) {
            constraintUtilizationRegistry.unregisterAccessConstraintUtilizations(getPathAddress().append(childAddress));
        }
    }

    @Override
    void getNotificationDescriptions(final ListIterator<PathElement> iterator, final Map<String, NotificationEntry> providers, final boolean inherited) {

        if (!iterator.hasNext() ) {
            checkPermission();
            providers.putAll(notificationsUpdater.get(this));
            if (inherited) {
                getInheritedNotifications(providers, true);
            }
            return;
        }
        final PathElement next = iterator.next();
        try {
            final String key = next.getKey();
            final Map<String, NodeSubregistry> snapshot = childrenUpdater.get(this);
            final NodeSubregistry subregistry = snapshot.get(key);
            if (subregistry != null) {
                subregistry.getNotificationDescriptions(iterator, next.getValue(), providers, inherited);
            }
        } finally {
            iterator.previous();
        }
    }

    @Override
    void getInheritedNotificationEntries(final Map<String, NotificationEntry> providers) {
        checkPermission();
        for (final Map.Entry<String, NotificationEntry> entry : notificationsUpdater.get(this).entrySet()) {
            if (entry.getValue().isInherited() && !providers.containsKey(entry.getKey())) {
                providers.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    Set<RuntimeCapability> getCapabilities(ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = children.get(next.getKey());
            if (subregistry == null) {
                return Collections.emptySet();
            }
            return subregistry.getCapabilities(iterator, next.getValue());
        } else {
            checkPermission();
            return Collections.unmodifiableSet(capabilities);
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
        final Map<String, NodeSubregistry> snapshot = childrenUpdater.get(this);
        final NodeSubregistry subregistry = snapshot.get(address.getKey());
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
        final Map<String, NodeSubregistry> snapshot = childrenUpdater.get(this);
        final NodeSubregistry subregistry = snapshot.get(address.getKey());
        if (subregistry != null) {
            subregistry.unregisterAlias(address.getValue());
        }
    }

    @Override
    public void registerCapability(RuntimeCapability capability){
        capabilities.add(capability);
        if (capabilityRegistry != null) {
            capabilityRegistry.registerPossibleCapability(capability, getPathAddress());
        }
    }

    NodeSubregistry getOrCreateSubregistry(final String key) {
        for (;;) {
            final Map<String, NodeSubregistry> snapshot = childrenUpdater.get(this);
            final NodeSubregistry subregistry = snapshot.get(key);
            if (subregistry != null) {
                return subregistry;
            } else {
                checkPermission();
                final NodeSubregistry newRegistry = new NodeSubregistry(key, this, constraintUtilizationRegistry, capabilityRegistry);
                final NodeSubregistry appearing = childrenUpdater.putAtomic(this, key, newRegistry, snapshot);
                if (appearing == null) {
                    return newRegistry;
                } else if (appearing != newRegistry) {
                    // someone else added one
                    return appearing;
                }
                // otherwise, retry the loop because the map changed
            }
        }
    }

    @Override
    DescriptionProvider getModelDescription(final ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = children.get(next.getKey());
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
            final NodeSubregistry subregistry = children.get(next.getKey());
            if (subregistry == null) {
                return Collections.emptySet();
            }
            return subregistry.getAttributeNames(iterator, next.getValue());
        } else {
            checkPermission();
            final Map<String, AttributeAccess> snapshot = attributesUpdater.get(this);
            return snapshot.keySet();
        }
    }

    @Override
    AttributeAccess getAttributeAccess(final ListIterator<PathElement> iterator, final String attributeName) {

        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = children.get(next.getKey());
            if (subregistry == null) {
                return null;
            }
            return subregistry.getAttributeAccess(iterator, next.getValue(), attributeName);
        } else {
            checkPermission();
            final Map<String, AttributeAccess> snapshot = attributesUpdater.get(this);
            return snapshot.get(attributeName);
        }
    }

    @Override
    Set<String> getChildNames(final ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = children.get(next.getKey());
            if (subregistry == null) {
                return Collections.emptySet();
            }
            return subregistry.getChildNames(iterator, next.getValue());
        } else {
            checkPermission();
            final Map<String, NodeSubregistry> children = this.children;
            if (children != null) {
                return Collections.unmodifiableSet(children.keySet());
            }
            return Collections.emptySet();
        }
    }

    @Override
    Set<PathElement> getChildAddresses(final ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = children.get(next.getKey());
            if (subregistry == null) {
                return Collections.emptySet();
            }
            return subregistry.getChildAddresses(iterator, next.getValue());
        } else {
            checkPermission();
            final Map<String, NodeSubregistry> children = this.children;
            if (children != null) {
                final Set<PathElement> elements = new HashSet<PathElement>();
                for (final Map.Entry<String, NodeSubregistry> entry : children.entrySet()) {
                    for (final String entryChild : entry.getValue().getChildNames()) {
                        elements.add(PathElement.pathElement(entry.getKey(), entryChild));
                    }
                }
                return elements;
            }
            return Collections.emptySet();
        }
    }

    @Override
    ProxyController getProxyController(ListIterator<PathElement> iterator) {
        if (iterator.hasNext()) {
            final PathElement next = iterator.next();
            final NodeSubregistry subregistry = children.get(next.getKey());
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
            final NodeSubregistry subregistry = children.get(next.getKey());
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
            final Map<String, NodeSubregistry> snapshot = childrenUpdater.get(this);
            for (NodeSubregistry subregistry : snapshot.values()) {
                subregistry.getProxyControllers(iterator, null, controllers);
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
            final Map<String, NodeSubregistry> snapshot = childrenUpdater.get(this);
            final NodeSubregistry subregistry = snapshot.get(address.getKey());
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
        if (orderedChildUpdater.putIfAbsent(this, type, Empty.INSTANCE) != null) {
            throw alreadyRegistered("Ordered child", type);
        }

    }

    private static class Empty {
        static final Empty INSTANCE = new Empty();
    }
}

