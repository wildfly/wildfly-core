/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jboss.as.controller.CapabilityReferenceRecorder;

import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.OverrideDescriptionProvider;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.version.FeatureStream;
import org.jboss.dmr.ModelNode;
import org.wildfly.common.Assert;

/**
 * A registry of model node information.  This registry is thread-safe.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings("deprecation")
abstract class AbstractResourceRegistration implements ManagementResourceRegistration {

    private final String valueString;
    private final NodeSubregistry parent;
    private final PathAddress pathAddress;
    private final ProcessType processType;
    private final FeatureStream stream;
    private RootInvocation rootInvocation;

    /** Constructor for a root MRR */
    AbstractResourceRegistration(final ProcessType processType, FeatureStream stream) {
        checkPermission();
        this.valueString = null;
        this.parent = null;
        this.pathAddress = PathAddress.EMPTY_ADDRESS;
        this.processType = Assert.checkNotNullParam("processType", processType);
        this.stream = Assert.checkNotNullParam("stream", stream);
    }

    /** Constructor for a non-root MRR */
    AbstractResourceRegistration(final String valueString, final NodeSubregistry parent) {
        checkPermission();
        this.valueString = Assert.checkNotNullParam("valueString", valueString);
        this.parent = Assert.checkNotNullParam("parent", parent);
        this.pathAddress = parent.getPathAddress(valueString);
        this.processType = parent.getProcessType();
        this.stream = parent.getFeatureStream();
    }

    static void checkPermission() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ImmutableManagementResourceRegistration.ACCESS_PERMISSION);
        }
    }

    NodeSubregistry getParentSubRegistry() {
        return parent;
    }

    void addAccessConstraints(List<AccessConstraintDefinition> list) {
        // no-op in the base class
    }

    @Override
    public ProcessType getProcessType() {
        return processType;
    }

    @Override
    public FeatureStream getFeatureStream() {
        return this.stream;
    }

    /** {@inheritDoc} */
    @Override
    public abstract ManagementResourceRegistration registerSubModel(final ResourceDefinition resourceDefinition);

    @Override
    public boolean isAllowsOverride() {
        checkPermission();
        return !isRemote() && parent != null && PathElement.WILDCARD_VALUE.equals(valueString);
    }

    @Override
    public ManagementResourceRegistration registerOverrideModel(String name, OverrideDescriptionProvider descriptionProvider) {
        Assert.checkNotNullParam("name", name);
        Assert.checkNotNullParam("descriptionProvider", descriptionProvider);

        if (parent == null) {
            throw ControllerLogger.ROOT_LOGGER.cannotOverrideRootRegistration();
        }

        if (!PathElement.WILDCARD_VALUE.equals(valueString)) {
            throw ControllerLogger.ROOT_LOGGER.cannotOverrideNonWildCardRegistration(valueString);
        }
        PathElement pe = PathElement.pathElement(parent.getKeyName(), name);

        final SimpleResourceDefinition rd = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(pe, new OverrideDescriptionCombiner(getModelDescription(PathAddress.EMPTY_ADDRESS), descriptionProvider))) {
            @Override
            public List<AccessConstraintDefinition> getAccessConstraints() {
                return AbstractResourceRegistration.this.getAccessConstraints();
            }
        };
        return parent.getParent().registerSubModel(rd);
    }

    @Override
    public void unregisterOverrideModel(String name) {
        Assert.checkNotNullParam("name", name);
        if (PathElement.WILDCARD_VALUE.equals(name)) {
            throw ControllerLogger.ROOT_LOGGER.wildcardRegistrationIsNotAnOverride();
        }
        if (parent == null) {
            throw ControllerLogger.ROOT_LOGGER.rootRegistrationIsNotOverridable();
        }
        PathElement pe = PathElement.pathElement(parent.getKeyName(), name);
        parent.getParent().unregisterSubModel(pe);
    }

    @Override
    public void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler){
        registerOperationHandler(definition, handler, false);
    }

    @Override
    public abstract void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler,boolean inherited);

    /** {@inheritDoc} */
    @Override
    public abstract void unregisterOperationHandler(final String operationName);

    /** {@inheritDoc} */
    @Override
    public abstract void registerProxyController(final PathElement address, final ProxyController controller) throws IllegalArgumentException;

    /** {@inheritDoc} */
    @Override
    public abstract void unregisterProxyController(PathElement address);

    /** {@inheritDoc} */
    @Override
    public final OperationEntry getOperationEntry(final PathAddress pathAddress, final String operationName) {

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getOperationEntry(ri.pathAddress.append(pathAddress), operationName);
        }
        // else we are the root

        OperationEntry inheritable = getInheritableOperationEntry(operationName);
        return getOperationEntry(pathAddress.iterator(), operationName, inheritable);
    }

    abstract OperationEntry getOperationEntry(ListIterator<PathElement> iterator, String operationName, OperationEntry inherited);
    abstract OperationEntry getInheritableOperationEntry(String operationName);

    /** {@inheritDoc} */
    @Override
    public final OperationStepHandler getOperationHandler(final PathAddress pathAddress, final String operationName) {
        OperationEntry entry = getOperationEntry(pathAddress, operationName);
        return entry == null ? null : entry.getOperationHandler();
    }

    /** {@inheritDoc} */
    @Override
    public final DescriptionProvider getOperationDescription(final PathAddress address, final String operationName) {
        OperationEntry entry = getOperationEntry(address, operationName);
        return entry == null ? null : entry.getDescriptionProvider();
    }

    /** {@inheritDoc} */
    @Override
    public final Set<OperationEntry.Flag> getOperationFlags(final PathAddress pathAddress, final String operationName) {
        OperationEntry entry = getOperationEntry(pathAddress, operationName);
        return entry == null ? null : entry.getFlags();
    }

    @Override
    public final AttributeAccess getAttributeAccess(final PathAddress address, final String attributeName) {

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getAttributeAccess(ri.pathAddress.append(address), attributeName);
        }
        // else we are the root
        return getAttributeAccess(address.iterator(), attributeName);
    }

    abstract AttributeAccess getAttributeAccess(final ListIterator<PathElement> address, final String attributeName);

    /**
     * Get all the handlers at a specific address.
     *
     * @param address the address
     * @param inherited true to include the inherited operations
     * @return the handlers
     */
    @Override
    public final Map<String, OperationEntry> getOperationDescriptions(final PathAddress address, boolean inherited) {

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getOperationDescriptions(ri.pathAddress.append(address), inherited);
        }
        // else we are the root
        Map<String, OperationEntry> providers = new TreeMap<String, OperationEntry>();
        getOperationDescriptions(address.iterator(), providers, inherited);
        return providers;
    }

    abstract void getOperationDescriptions(ListIterator<PathElement> iterator, Map<String, OperationEntry> providers, boolean inherited);

    /**
     * Get all the handlers at a specific address.
     *
     * @param address the address
     * @param inherited true to include the inherited notifcations
     * @return the handlers
     */
    @Override
    public final Map<String, NotificationEntry> getNotificationDescriptions(final PathAddress address, boolean inherited) {

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getNotificationDescriptions(ri.pathAddress.append(address), inherited);
        }
        // else we are the root
        Map<String, NotificationEntry> providers = new TreeMap<String, NotificationEntry>();
        getNotificationDescriptions(address.iterator(), providers, inherited);
        return providers;
    }

    abstract void getNotificationDescriptions(ListIterator<PathElement> iterator, Map<String, NotificationEntry> providers, boolean inherited);


    /** {@inheritDoc} */
    @Override
    public final DescriptionProvider getModelDescription(final PathAddress address) {

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getModelDescription(ri.pathAddress.append(address));
        }
        // else we are the root
        return getModelDescription(address.iterator());
    }

    abstract DescriptionProvider getModelDescription(ListIterator<PathElement> iterator);

    @Override
    public final Set<String> getAttributeNames(final PathAddress address) {

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getAttributeNames(ri.pathAddress.append(address));
        }
        // else we are the root
        return getAttributeNames(address.iterator());
    }

    abstract Set<String> getAttributeNames(ListIterator<PathElement> iterator);

    @Override
    public final Map<String, AttributeAccess> getAttributes(final PathAddress address) {

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getAttributes(ri.pathAddress.append(address));
        }
        // else we are the root
        return getAttributes(address.iterator());
    }

    abstract Map<String, AttributeAccess> getAttributes(ListIterator<PathElement> iterator);

    @Override
    public final Set<String> getChildNames(final PathAddress address) {

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getChildNames(ri.pathAddress.append(address));
        }
        // else we are the root
        return getChildNames(address.iterator());
    }

    abstract Set<String> getChildNames(ListIterator<PathElement> iterator);

    @Override
    public final Set<PathElement> getChildAddresses(final PathAddress address){

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getChildAddresses(ri.pathAddress.append(address));
        }
        // else we are the root
        return getChildAddresses(address.iterator());
    }

    abstract Set<PathElement> getChildAddresses(ListIterator<PathElement> iterator);

    @Override
    public final ProxyController getProxyController(final PathAddress address) {

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getProxyController(ri.pathAddress.append(address));
        }
        // else we are the root
        return getProxyController(address.iterator());
    }

    abstract ProxyController getProxyController(ListIterator<PathElement> iterator);

    @Override
    public final Set<ProxyController> getProxyControllers(PathAddress address){

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getProxyControllers(ri.pathAddress.append(address));
        }
        // else we are the root

        Set<ProxyController> controllers = new HashSet<ProxyController>();
        getProxyControllers(address.iterator(), controllers);
        return controllers;
    }

    abstract void getProxyControllers(ListIterator<PathElement> iterator, Set<ProxyController> controllers);

    /** {@inheritDoc} */
    @Override
    public final ManagementResourceRegistration getOverrideModel(String name) {

        Assert.checkNotNullParam("name", name);

        if (parent == null) {
            throw ControllerLogger.ROOT_LOGGER.cannotOverrideRootRegistration();
        }

        if (!PathElement.WILDCARD_VALUE.equals(valueString)) {
            throw ControllerLogger.ROOT_LOGGER.cannotOverrideNonWildCardRegistration(valueString);
        }
        PathElement pe = PathElement.pathElement(parent.getKeyName(),name);

        // TODO https://issues.jboss.org/browse/WFLY-2883
//        ManagementResourceRegistration candidate = parent.getParent().getSubModel(PathAddress.pathAddress(pe));
//        // We may have gotten back the wildcard reg; detect this by checking for allowing override
//        return candidate.isAllowsOverride() ? null : candidate;
        return parent.getParent().getSubModel(PathAddress.pathAddress(pe));
    }

    /** {@inheritDoc} */
    @Override
    public final ManagementResourceRegistration getSubModel(PathAddress address) {

        return getSubRegistration(address);
    }

    final ManagementResourceRegistration getSubRegistration(PathAddress address) {


        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getSubRegistration(ri.pathAddress.append(address));
        }
        // else we are the root
        return getResourceRegistration(address.iterator());

    }

    abstract ManagementResourceRegistration getResourceRegistration(ListIterator<PathElement> iterator);

    final String getLocationString() {
        return getPathAddress().toCLIStyleString();
    }

    final void getInheritedOperations(final Map<String, OperationEntry> providers, boolean skipSelf) {
        if (!skipSelf) {
            getInheritedOperationEntries(providers);
        }
        if (parent != null) {
            parent.getParent().getInheritedOperations(providers, false);
        }
    }

    final void getInheritedNotifications(final Map<String, NotificationEntry> providers, boolean skipSelf) {
        if (!skipSelf) {
            getInheritedNotificationEntries(providers);
        }
        if (parent != null) {
            parent.getParent().getInheritedNotifications(providers, false);
        }
    }

    /** Gets whether this registration has an alternative wildcard registration */
    boolean hasNoAlternativeWildcardRegistration() {
        return parent == null || PathElement.WILDCARD_VALUE.equals(valueString) || !parent.getChildNames().contains(PathElement.WILDCARD_VALUE);
    }

    abstract void getInheritedOperationEntries(final Map<String, OperationEntry> providers);

    abstract void getInheritedNotificationEntries(final Map<String, NotificationEntry> providers);

    @Override
    public final Set<RuntimeCapability> getCapabilities() {

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getCapabilities(ri.pathAddress.iterator());
        }
        // else we are the root
        return getCapabilities(pathAddress.iterator());
    }

    abstract Set<RuntimeCapability> getCapabilities(ListIterator<PathElement> iterator);

    @Override
    public final Set<RuntimeCapability> getIncorporatingCapabilities() {

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getIncorporatingCapabilities(ri.pathAddress.iterator());
        }
        // else we are the root
        return getIncorporatingCapabilities(pathAddress.iterator());
    }

    abstract Set<RuntimeCapability> getIncorporatingCapabilities(ListIterator<PathElement> iterator);

    private RootInvocation getRootInvocation() {
        RootInvocation result = rootInvocation;
        if (result == null && parent != null) {
            synchronized (this) {
                if (rootInvocation == null) {
                    NodeSubregistry ancestorSubregistry = parent;
                    AbstractResourceRegistration ancestorReg = this;
                    while (ancestorSubregistry != null) {
                        ancestorReg = ancestorSubregistry.getParent();
                        ancestorSubregistry = ancestorReg.parent;
                    }
                    rootInvocation = new RootInvocation(ancestorReg, pathAddress);
                }
                result = rootInvocation;
            }
        }
        return result;
    }

    @Override
    public final Set<CapabilityReferenceRecorder> getRequirements() {

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getRequirements(ri.pathAddress.iterator());
        }
        // else we are the root
        return getRequirements(pathAddress.iterator());
    }

    abstract Set<CapabilityReferenceRecorder> getRequirements(ListIterator<PathElement> iterator);

    protected AbstractResourceRegistration getRootResourceRegistration() {
        if (parent == null) {
            return this;
        }
        RootInvocation invocation = getRootInvocation();
        return invocation.root;

    }

    @Override
    public void registerAlias(PathElement address, AliasEntry alias) {
        RootInvocation rootInvocation = parent == null ? null : getRootInvocation();
        AbstractResourceRegistration root = rootInvocation == null ? this : rootInvocation.root;
        PathAddress myaddr = rootInvocation == null ? PathAddress.EMPTY_ADDRESS : rootInvocation.pathAddress;

        PathAddress targetAddress = alias.getTarget().getPathAddress();
        alias.setAddresses(targetAddress, myaddr.append(address));
        AbstractResourceRegistration target = (AbstractResourceRegistration)root.getSubModel(alias.getTargetAddress());
        if (target == null) {
            throw ControllerLogger.ROOT_LOGGER.aliasTargetResourceRegistrationNotFound(alias.getTargetAddress());
        }

        registerAlias(address, alias, target);
    }

    protected abstract void registerAlias(PathElement address, AliasEntry alias, AbstractResourceRegistration target);

    @Override
    public boolean isAlias() {
        //Overridden by AliasResourceRegistration
        return false;
    }

    @Override
    public AliasEntry getAliasEntry() {
        //Overridden by AliasResourceRegistration
        throw ControllerLogger.ROOT_LOGGER.resourceRegistrationIsNotAnAlias();
    }

    @Override
    public PathAddress getPathAddress() {
        return pathAddress;
    }

    @Override
    public ImmutableManagementResourceRegistration getParent() {
        return parent == null ? null : parent.getParent();
    }

    @Override
    public Set<String> getOrderedChildTypes() {

        if (parent != null) {
            RootInvocation ri = getRootInvocation();
            return ri.root.getOrderedChildTypes(ri.pathAddress.iterator());
        }
        // else we are the root
        return getOrderedChildTypes(pathAddress.iterator());
    }

    abstract Set<String> getOrderedChildTypes(ListIterator<PathElement> iterator);

    protected abstract void setOrderedChild(String key);

    private static class RootInvocation {
        final AbstractResourceRegistration root;
        final PathAddress pathAddress;

        RootInvocation(AbstractResourceRegistration root, PathAddress pathAddress) {
            this.root = root;
            this.pathAddress = pathAddress;
        }
    }

    private static class OverrideDescriptionCombiner implements DescriptionProvider {
        private final DescriptionProvider mainDescriptionProvider;
        private final OverrideDescriptionProvider overrideDescriptionProvider;

        private OverrideDescriptionCombiner(DescriptionProvider mainDescriptionProvider, OverrideDescriptionProvider overrideDescriptionProvider) {
            this.mainDescriptionProvider = mainDescriptionProvider;
            this.overrideDescriptionProvider = overrideDescriptionProvider;
        }

        @Override
        public ModelNode getModelDescription(Locale locale) {
            ModelNode result = mainDescriptionProvider.getModelDescription(locale);
            ModelNode attrs = result.get(ModelDescriptionConstants.ATTRIBUTES);
            for (Map.Entry<String, ModelNode> entry : overrideDescriptionProvider.getAttributeOverrideDescriptions(locale).entrySet()) {
                attrs.get(entry.getKey()).set(entry.getValue());
            }
            ModelNode children = result.get(ModelDescriptionConstants.CHILDREN);
            for (Map.Entry<String, ModelNode> entry : overrideDescriptionProvider.getChildTypeOverrideDescriptions(locale).entrySet()) {
                children.get(entry.getKey()).set(entry.getValue());
            }
            return result;
        }
    }
}
