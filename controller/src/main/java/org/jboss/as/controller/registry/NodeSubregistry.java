/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.jboss.as.controller.CapabilityReferenceRecorder;

import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.FeatureStream;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.AccessConstraintUtilizationRegistry;
import org.jboss.as.controller.capability.Capability;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.logging.ControllerLogger;

/**
 * A registry of values within a specific key type.
 */
final class NodeSubregistry {

    private static void checkPermission() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ImmutableManagementResourceRegistration.ACCESS_PERMISSION);
        }
    }

    private static final ListIterator<PathElement> EMPTY_ITERATOR = PathAddress.EMPTY_ADDRESS.iterator();

    private static final String WILDCARD_VALUE = PathElement.WILDCARD_VALUE;

    private final String keyName;
    private final ConcreteResourceRegistration parent;
    private final AccessConstraintUtilizationRegistry constraintUtilizationRegistry;
    private final CapabilityRegistry capabilityRegistry;
    private final ProcessType processType;
    private final FeatureStream stream;
    @SuppressWarnings( { "unused" })
    private volatile Map<String, AbstractResourceRegistration> childRegistries;

    private static final AtomicMapFieldUpdater<NodeSubregistry, String, AbstractResourceRegistration> childRegistriesUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(NodeSubregistry.class, Map.class, "childRegistries"));

    NodeSubregistry(final String keyName, final ConcreteResourceRegistration parent, AccessConstraintUtilizationRegistry constraintUtilizationRegistry, CapabilityRegistry capabilityRegistry) {
        this.keyName = keyName;
        this.parent = parent;
        this.constraintUtilizationRegistry = constraintUtilizationRegistry;
        this.capabilityRegistry = capabilityRegistry;
        this.processType = parent.getProcessType();
        this.stream = parent.getFeatureStream();
        childRegistriesUpdater.clear(this);
    }

    AbstractResourceRegistration getParent() {
        return parent;
    }

    Set<String> getChildNames(){
        final Map<String, AbstractResourceRegistration> snapshot = this.childRegistries;
        if (snapshot == null) {
            return Collections.emptySet();
        }
        return new HashSet<String>(snapshot.keySet());
    }

    ManagementResourceRegistration registerChild(final String elementValue, final ResourceDefinition provider) {
        boolean ordered = provider.isOrderedChild();

        final ConcreteResourceRegistration newRegistry =
                new ConcreteResourceRegistration(elementValue, this, provider, constraintUtilizationRegistry, ordered, capabilityRegistry);

        newRegistry.beginInitialization();
        try {

            final AbstractResourceRegistration existingRegistry = childRegistriesUpdater.putIfAbsent(this, elementValue, newRegistry);
            if (existingRegistry != null) {
                throw ControllerLogger.ROOT_LOGGER.nodeAlreadyRegistered(getLocationString(elementValue));
            }

            provider.registerAttributes(newRegistry);
            provider.registerOperations(newRegistry);
            provider.registerNotifications(newRegistry);
            provider.registerChildren(newRegistry);
            provider.registerCapabilities(newRegistry);
            provider.registerAdditionalRuntimePackages(newRegistry);

            if (constraintUtilizationRegistry != null) {
                PathAddress childAddress = newRegistry.getPathAddress();
                List<AccessConstraintDefinition> constraintDefinitions = provider.getAccessConstraints();
                for (AccessConstraintDefinition acd : constraintDefinitions) {
                    constraintUtilizationRegistry.registerAccessConstraintResourceUtilization(acd.getKey(), childAddress);
                }
            }
        } finally {
            newRegistry.initialized();
        }

        if (ordered) {
            AbstractResourceRegistration parentRegistration = getParent();
            parentRegistration.setOrderedChild(keyName);
        }

        return newRegistry;
    }

    ProxyControllerRegistration registerProxyController(final String elementValue, final ProxyController proxyController) {
        final ProxyControllerRegistration newRegistry = new ProxyControllerRegistration(elementValue, this, proxyController);
        final AbstractResourceRegistration appearingRegistry = childRegistriesUpdater.putIfAbsent(this, elementValue, newRegistry);
        if (appearingRegistry != null) {
            throw ControllerLogger.ROOT_LOGGER.nodeAlreadyRegistered(getLocationString(elementValue));
        }
        //register(elementValue, newRegistry);
        return newRegistry;
    }

    void unregisterProxyController(final String elementValue) {
        checkPermission();
        childRegistriesUpdater.remove(this, elementValue);
    }

    public AliasResourceRegistration registerAlias(final String elementValue, AliasEntry aliasEntry, AbstractResourceRegistration target) {
        final AliasResourceRegistration newRegistry = new AliasResourceRegistration(elementValue, this, aliasEntry, target);
        final AbstractResourceRegistration existingRegistry = childRegistriesUpdater.putIfAbsent(this, elementValue, newRegistry);
        if (existingRegistry != null) {
            throw ControllerLogger.ROOT_LOGGER.nodeAlreadyRegistered(getLocationString(elementValue));
        }
        return newRegistry;
    }

    public void unregisterAlias(final String elementValue) {
        checkPermission();
        childRegistriesUpdater.remove(this, elementValue);
    }


    void unregisterSubModel(final String elementValue) {
        checkPermission();
        AbstractResourceRegistration rr = childRegistriesUpdater.remove(this, elementValue);
        if (rr != null) {
            // We want to remove the possible capabilities.
            // We've removed the MRR so the normal getCapabilities() won't work as it
            // relies on walking the tree from the root. So we just use the local call
            // with a iterator whose hasNext() returns false
            PathAddress pa = getPathAddress(elementValue);
            for (Capability c : rr.getCapabilities(EMPTY_ITERATOR)) {
                capabilityRegistry.removePossibleCapability(c, pa);
            }
        }
    }

    OperationEntry getOperationEntry(final ListIterator<PathElement> iterator, final String child, final String operationName, OperationEntry inherited) {

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        // First search the non-wildcard child; if not found, search the wildcard child
        OperationEntry result = null;

        if (searchControl.getSpecifiedRegistry() != null) {
            result = searchControl.getSpecifiedRegistry().getOperationEntry(searchControl.getIterator(), operationName, inherited);
        }

        if (result == null && searchControl.getWildCardRegistry() != null) {
            result = searchControl.getWildCardRegistry().getOperationEntry(searchControl.getIterator(), operationName, inherited);
        }

        // If there is no concrete registry and wildcard query
        if (result == null && child.equals("*")) {
            return inherited;
        }

        return result;
    }

    void getHandlers(final ListIterator<PathElement> iterator, final String child, final Map<String, OperationEntry> providers, final boolean inherited) {

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        // First search the wildcard child, then if there is a non-wildcard child search it
        // Non-wildcard goes second so its description overwrites in case of duplicates

        if (searchControl.getWildCardRegistry() != null) {
            searchControl.getWildCardRegistry().getOperationDescriptions(searchControl.getIterator(), providers, inherited);
        }

        if (searchControl.getSpecifiedRegistry() != null) {
            searchControl.getSpecifiedRegistry().getOperationDescriptions(searchControl.getIterator(), providers, inherited);
        }
    }

    void getNotificationDescriptions(final ListIterator<PathElement> iterator, final String child, final Map<String, NotificationEntry> providers, final boolean inherited) {

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        // First search the wildcard child, then if there is a non-wildcard child search it
        // Non-wildcard goes second so its description overwrites in case of duplicates

        if (searchControl.getWildCardRegistry() != null) {
            searchControl.getWildCardRegistry().getNotificationDescriptions(searchControl.getIterator(), providers, inherited);
        }

        if (searchControl.getSpecifiedRegistry() != null) {
            searchControl.getSpecifiedRegistry().getNotificationDescriptions(searchControl.getIterator(), providers, inherited);
        }
    }

    private String getLocationString(String value) {
        return parent.getPathAddress().append(keyName, value).toCLIStyleString();
    }

    DescriptionProvider getModelDescription(final ListIterator<PathElement> iterator, final String child) {

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        // First search the non-wildcard child; if not found, search the wildcard child
        DescriptionProvider result = null;

        if (searchControl.getSpecifiedRegistry() != null) {
            result = searchControl.getSpecifiedRegistry().getModelDescription(searchControl.getIterator());
        }

        if (result == null && searchControl.getWildCardRegistry() != null) {
            result = searchControl.getWildCardRegistry().getModelDescription(searchControl.getIterator());
        }

        return result;
    }

    Set<String> getChildNames(final ListIterator<PathElement> iterator, final String child){

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        Set<String> result = null;
        if (searchControl.getSpecifiedRegistry() != null) {
            result = searchControl.getSpecifiedRegistry().getChildNames(searchControl.getIterator());
        }

        if (searchControl.getWildCardRegistry() != null) {
            final Set<String> wildCardChildren = searchControl.getWildCardRegistry().getChildNames(searchControl.getIterator());
            if (result == null) {
                result = wildCardChildren;
            } else if (wildCardChildren != null) {
                // Merge
                result = new HashSet<String>(result);
                result.addAll(wildCardChildren);
            }
        }
        return result;
    }

    Set<String> getAttributeNames(final ListIterator<PathElement> iterator, final String child){

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        Set<String> result = null;
        if (searchControl.getSpecifiedRegistry() != null) {
            result = searchControl.getSpecifiedRegistry().getAttributeNames(searchControl.getIterator());
        }

        if (searchControl.getWildCardRegistry() != null) {
            final Set<String> wildCardChildren = searchControl.getWildCardRegistry().getAttributeNames(searchControl.getIterator());
            if (result == null) {
                result = wildCardChildren;
            } else if (wildCardChildren != null) {
                // Merge
                result = new HashSet<String>(result);
                result.addAll(wildCardChildren);
            }
        }
        return result;
    }

    AttributeAccess getAttributeAccess(final ListIterator<PathElement> iterator, final String child, final String attributeName) {

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        // First search the non-wildcard child; if not found, search the wildcard child
        AttributeAccess result = null;

        if (searchControl.getSpecifiedRegistry() != null) {
            result = searchControl.getSpecifiedRegistry().getAttributeAccess(searchControl.getIterator(), attributeName);
        }

        if (result == null && searchControl.getWildCardRegistry() != null) {
            result = searchControl.getWildCardRegistry().getAttributeAccess(searchControl.getIterator(), attributeName);
        }

        return result;
    }

    Map<String, AttributeAccess> getAttributes(final ListIterator<PathElement> iterator, final String child){

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        // First search the wildcard child, then if there is a non-wildcard child search it
        // Non-wildcard goes second so its description overwrites in case of duplicates

        Map<String, AttributeAccess> result = null;
        if (searchControl.getWildCardRegistry() != null) {
            result = searchControl.getWildCardRegistry().getAttributes(searchControl.getIterator());
        }

        if (searchControl.getSpecifiedRegistry() != null) {
            final Map<String, AttributeAccess> specifiedChildren = searchControl.getSpecifiedRegistry().getAttributes(searchControl.getIterator());
            if (result == null) {
                result = specifiedChildren;
            } else if (specifiedChildren != null) {
                // Merge
                result = new HashMap<String, AttributeAccess>(result);
                result.putAll(specifiedChildren);
            }
        }
        return result;
    }


    Set<PathElement> getChildAddresses(final ListIterator<PathElement> iterator, final String child){

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        Set<PathElement> result = null;
        if (searchControl.getSpecifiedRegistry() != null) {
            result = searchControl.getSpecifiedRegistry().getChildAddresses(searchControl.getIterator());
        }

        if (searchControl.getWildCardRegistry() != null) {
            final Set<PathElement> wildCardChildren = searchControl.getWildCardRegistry().getChildAddresses(searchControl.getIterator());
            if (result == null) {
                result = wildCardChildren;
            } else if (wildCardChildren != null) {
                // Merge
                result = new HashSet<PathElement>(result);
                result.addAll(wildCardChildren);
            }
        }
        return result;
    }

    ProxyController getProxyController(final ListIterator<PathElement> iterator, final String child) {

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        // First search the non-wildcard child; if not found, search the wildcard child
        ProxyController result = null;

        if (searchControl.getSpecifiedRegistry() != null) {
            result = searchControl.getSpecifiedRegistry().getProxyController(searchControl.getIterator());
        }

        if (result == null && searchControl.getWildCardRegistry() != null) {
            result = searchControl.getWildCardRegistry().getProxyController(searchControl.getIterator());
        }

        return result;
    }

    ManagementResourceRegistration getResourceRegistration(final ListIterator<PathElement> iterator, final String child) {

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        // First search the non-wildcard child; if not found, search the wildcard child
        ManagementResourceRegistration result = null;

        if (searchControl.getSpecifiedRegistry() != null) {
            result = searchControl.getSpecifiedRegistry().getResourceRegistration(searchControl.getIterator());
        }

        if (result == null && searchControl.getWildCardRegistry() != null) {
            result = searchControl.getWildCardRegistry().getResourceRegistration(searchControl.getIterator());
        }

        return result;
    }

    void getProxyControllers(final ListIterator<PathElement> iterator, final String child, Set<ProxyController> controllers) {
        if (child != null) {
            final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

            // First search the wildcard child, then if there is a non-wildcard child search it

            if (searchControl.getWildCardRegistry() != null) {
                searchControl.getWildCardRegistry().getProxyControllers(searchControl.getIterator(), controllers);
            }

            if (searchControl.getSpecifiedRegistry() != null) {
                searchControl.getSpecifiedRegistry().getProxyControllers(searchControl.getIterator(), controllers);
            }
        } else {
            final Map<String, AbstractResourceRegistration> snapshot = childRegistriesUpdater.get(NodeSubregistry.this);
            for (AbstractResourceRegistration childRegistry : snapshot.values()) {
                childRegistry.getProxyControllers(iterator, controllers);
            }
        }
    }

    PathAddress getPathAddress(String valueString) {
        return parent.getPathAddress().append(PathElement.pathElement(keyName, valueString));
    }

    Set<RuntimeCapability> getCapabilities(ListIterator<PathElement> iterator, String child) {

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        Set<RuntimeCapability> result = null;
        if (searchControl.getSpecifiedRegistry() != null) {
            result = searchControl.getSpecifiedRegistry().getCapabilities(searchControl.getIterator());
        }

        if (searchControl.getWildCardRegistry() != null) {
            final Set<RuntimeCapability> wildCardChildren = searchControl.getWildCardRegistry().getCapabilities(searchControl.getIterator());
            if (result == null) {
                result = wildCardChildren;
            } else if (wildCardChildren != null) {
                // Merge
                result = new HashSet<RuntimeCapability>(result);
                result.addAll(wildCardChildren);
            }
        }
        return result;
    }

    Set<RuntimeCapability> getIncorporatingCapabilities(ListIterator<PathElement> iterator, String child) {

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        Set<RuntimeCapability> result = null;
        if (searchControl.getSpecifiedRegistry() != null) {
            result = searchControl.getSpecifiedRegistry().getIncorporatingCapabilities(searchControl.getIterator());
        }

        if (searchControl.getWildCardRegistry() != null) {
            final Set<RuntimeCapability> wildCardChildren = searchControl.getWildCardRegistry().getIncorporatingCapabilities(searchControl.getIterator());
            if (result == null) {
                result = wildCardChildren;
            } else if (wildCardChildren != null) {
                // Merge
                result = new HashSet<RuntimeCapability>(result);
                result.addAll(wildCardChildren);
            }
        }
        return result;
    }

    Set<CapabilityReferenceRecorder> getRequirements(ListIterator<PathElement> iterator, String child) {

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        Set<CapabilityReferenceRecorder> result = null;
        if (searchControl.getSpecifiedRegistry() != null) {
            result = searchControl.getSpecifiedRegistry().getRequirements(searchControl.getIterator());
        }

        if (searchControl.getWildCardRegistry() != null) {
            final Set<CapabilityReferenceRecorder> wildCardChildren = searchControl.getWildCardRegistry().getRequirements(searchControl.getIterator());
            if (result == null) {
                result = wildCardChildren;
            } else if (wildCardChildren != null) {
                // Merge
                result = new HashSet<CapabilityReferenceRecorder>(result);
                result.addAll(wildCardChildren);
            }
        }
        return result;
    }

    Set<String> getOrderedChildTypes(ListIterator<PathElement> iterator, String child) {

        final RegistrySearchControl searchControl = new RegistrySearchControl(iterator, child);

        Set<String> result = null;
        if (searchControl.getSpecifiedRegistry() != null) {
            result = searchControl.getSpecifiedRegistry().getOrderedChildTypes(searchControl.getIterator());
        }

        if (searchControl.getWildCardRegistry() != null) {
            final Set<String> wildCardChildren = searchControl.getWildCardRegistry().getOrderedChildTypes(searchControl.getIterator());
            if (result == null) {
                result = wildCardChildren;
            } else if (wildCardChildren != null) {
                // Merge
                result = new HashSet<String>(result);
                result.addAll(wildCardChildren);
            }
        }
        return result;
    }

    ProcessType getProcessType() {
        return processType;
    }

    FeatureStream getFeatureStream() {
        return this.stream;
    }

    boolean isRuntimeOnly() {
        return parent.isRuntimeOnly();
    }

    /**
     * Encapsulates data and behavior to help with searches in both a specified child and in the wildcard child if
     * it exists and is different from the specified child
     */
    private class RegistrySearchControl {
        private final AbstractResourceRegistration specifiedRegistry;
        private final AbstractResourceRegistration wildCardRegistry;
        private final ListIterator<PathElement> iterator;
        private final int restoreIndex;
        private boolean backupRequired;

        private RegistrySearchControl(final ListIterator<PathElement> iterator, final String childName) {
            final Map<String, AbstractResourceRegistration> snapshot = childRegistriesUpdater.get(NodeSubregistry.this);
            this.specifiedRegistry = snapshot.get(childName);
            this.wildCardRegistry = WILDCARD_VALUE.equals(childName) ? null : snapshot.get(WILDCARD_VALUE);
            this.iterator = iterator;
            this.restoreIndex = (specifiedRegistry != null && wildCardRegistry != null) ? iterator.nextIndex() : -1;
        }

        private AbstractResourceRegistration getSpecifiedRegistry() {
            return specifiedRegistry;
        }

        private AbstractResourceRegistration getWildCardRegistry() {
            return wildCardRegistry;
        }

        private ListIterator<PathElement> getIterator() {
            if (backupRequired) {
                if (restoreIndex == -1) {
                    // Coding mistake; someone wants to search twice for no reason, since we only have a single registration
                    throw new IllegalStateException("Multiple iterator requests are not supported since both " +
                            "named and wildcard entries were not present");
                }
                // Back the iterator to the restore index
                while (iterator.nextIndex() > restoreIndex) {
                    iterator.previous();
                }
            }
            backupRequired = true;
            return iterator;
        }
    }

    String getKeyName() {
        return keyName;
    }
}
