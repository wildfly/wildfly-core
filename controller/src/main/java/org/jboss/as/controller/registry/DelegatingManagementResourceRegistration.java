/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.OverrideDescriptionProvider;
import org.jboss.as.version.FeatureStream;

/**
 * {@link ManagementResourceRegistration} implementation that simply delegates to another
 * {@link ManagementResourceRegistration}. Intended as a convenience class to allow overriding
 * of standard behaviors and also as a means to support a copy-on-write/publish-on-commit
 * semantic for the management resource tree.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class DelegatingManagementResourceRegistration implements ManagementResourceRegistration {

    /**
     * Provides a delegate for use by a {@code DelegatingManagementResourceRegistration}.
     * Does not need to provide the same delegate for every call, allowing a copy-on-write
     * semantic for the underlying @{code ManagementResourceRegistration}.
     */
    public interface RegistrationDelegateProvider {
        /**
         * Gets the delegate.
         * @return the delegate. Cannot return {@code null}
         */
        ManagementResourceRegistration getDelegateRegistration();
    }

    private final RegistrationDelegateProvider delegateProvider;

    /**
     * Creates a new DelegatingManagementResourceRegistration with a fixed delegate.
     *
     * @param delegate the delegate. Cannot be {@code null}
     */
    public DelegatingManagementResourceRegistration(final ManagementResourceRegistration delegate) {
        this(new RegistrationDelegateProvider() {
            @Override
            public ManagementResourceRegistration getDelegateRegistration() {
                return delegate;
            }
        });
    }

    /**
     * Creates a new DelegatingManagementResourceRegistration with a possibly changing delegate.
     *
     * @param delegateProvider provider of the delegate. Cannot be {@code null}
     */
    public DelegatingManagementResourceRegistration(final RegistrationDelegateProvider delegateProvider) {
        assert delegateProvider != null;
        assert delegateProvider.getDelegateRegistration() != null;
        this.delegateProvider = delegateProvider;
    }

    @Override
    public PathAddress getPathAddress() {
        return getDelegate().getPathAddress();
    }

    @Override
    public ProcessType getProcessType() {
        return getDelegate().getProcessType();
    }

    @Override
    public FeatureStream getFeatureStream() {
        return this.getDelegate().getFeatureStream();
    }

    @Override
    public ImmutableManagementResourceRegistration getParent() {
        return getDelegate().getParent();
    }

    @Override
    public int getMaxOccurs() {
        return getDelegate().getMaxOccurs();
    }

    @Override
    public int getMinOccurs() {
        return getDelegate().getMinOccurs();
    }

    @Override
    public String getFeature() {
        return getDelegate().getFeature();
    }

    @Override
    public boolean isFeature() {
        return getDelegate().isFeature();
    }

    @Override
    public boolean isRuntimeOnly() {
        return getDelegate().isRuntimeOnly();
    }

    @Override
    public boolean isRemote() {
        return getDelegate().isRemote();
    }

    @Override
    public boolean isAlias() {
        return getDelegate().isAlias();
    }

    @Override
    public OperationEntry getOperationEntry(PathAddress address, String operationName) {
        return getDelegate().getOperationEntry(address, operationName);
    }

    @Override
    public OperationStepHandler getOperationHandler(PathAddress address, String operationName) {
        return getDelegate().getOperationHandler(address, operationName);
    }

    @Override
    public DescriptionProvider getOperationDescription(PathAddress address, String operationName) {
        return getDelegate().getOperationDescription(address, operationName);
    }

    @Override
    public Set<OperationEntry.Flag> getOperationFlags(PathAddress address, String operationName) {
        return getDelegate().getOperationFlags(address, operationName);
    }

    @Override
    public Set<String> getAttributeNames(PathAddress address) {
        return getDelegate().getAttributeNames(address);
    }

    @Override
    public AttributeAccess getAttributeAccess(PathAddress address, String attributeName) {
        return getDelegate().getAttributeAccess(address, attributeName);
    }

    @Override
    public Map<String, AttributeAccess> getAttributes(PathAddress address) {
        return getDelegate().getAttributes(address);
    }

    @Override
    public Set<String> getChildNames(PathAddress address) {
        return getDelegate().getChildNames(address);
    }

    @Override
    public Set<PathElement> getChildAddresses(PathAddress address) {
        return getDelegate().getChildAddresses(address);
    }

    @Override
    public DescriptionProvider getModelDescription(PathAddress address) {
        return getDelegate().getModelDescription(address);
    }

    @Override
    public Map<String, OperationEntry> getOperationDescriptions(PathAddress address, boolean inherited) {
        return getDelegate().getOperationDescriptions(address, inherited);
    }

    @Override
    public Map<String, NotificationEntry> getNotificationDescriptions(PathAddress address, boolean inherited) {
        return getDelegate().getNotificationDescriptions(address, inherited);
    }

    @Override
    public ProxyController getProxyController(PathAddress address) {
        return getDelegate().getProxyController(address);
    }

    @Override
    public Set<ProxyController> getProxyControllers(PathAddress address) {
        return getDelegate().getProxyControllers(address);
    }

    @Override
    public ManagementResourceRegistration getOverrideModel(String name) {
        return getDelegate().getOverrideModel(name);
    }

    @Override
    public ManagementResourceRegistration getSubModel(PathAddress address) {
        return getDelegate().getSubModel(address);
    }

    @Override
    public ManagementResourceRegistration registerSubModel(ResourceDefinition resourceDefinition) {
        return getDelegate().registerSubModel(resourceDefinition);
    }

    @Override
    public void unregisterSubModel(PathElement address) {
        getDelegate().unregisterSubModel(address);
    }

    @Override
    public boolean isAllowsOverride() {
        return getDelegate().isAllowsOverride();
    }

    @Override
    public ManagementResourceRegistration registerOverrideModel(String name, OverrideDescriptionProvider descriptionProvider) {
        return getDelegate().registerOverrideModel(name, descriptionProvider);
    }

    @Override
    public void unregisterOverrideModel(String name) {
        getDelegate().unregisterOverrideModel(name);
    }

    @Override
    public void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler) {
        getDelegate().registerOperationHandler(definition, handler);
    }

    @Override
    public void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler, boolean inherited) {
        getDelegate().registerOperationHandler(definition, handler, inherited);
    }

    @Override
    public void unregisterOperationHandler(String operationName) {
        getDelegate().unregisterOperationHandler(operationName);
    }

    @Override
    public void registerReadWriteAttribute(AttributeDefinition definition, OperationStepHandler readHandler, OperationStepHandler writeHandler) {
        getDelegate().registerReadWriteAttribute(definition, readHandler, writeHandler);
    }

    @Override
    public void registerReadOnlyAttribute(AttributeDefinition definition, OperationStepHandler readHandler) {
        getDelegate().registerReadOnlyAttribute(definition, readHandler);
    }

    @Override
    public void registerMetric(AttributeDefinition definition, OperationStepHandler metricHandler) {
        getDelegate().registerMetric(definition, metricHandler);
    }

    @Override
    public void unregisterAttribute(String attributeName) {
        getDelegate().unregisterAttribute(attributeName);
    }

    @Override
    public void registerNotification(NotificationDefinition notification, boolean inherited) {
        getDelegate().registerNotification(notification, inherited);
    }

    @Override
    public void registerNotification(NotificationDefinition notification) {
        getDelegate().registerNotification(notification);
    }

    @Override
    public void unregisterNotification(String notificationType) {
        getDelegate().unregisterNotification(notificationType);
    }

    @Override
    public void registerProxyController(PathElement address, ProxyController proxyController) {
        getDelegate().registerProxyController(address, proxyController);
    }

    @Override
    public void unregisterProxyController(PathElement address) {
        getDelegate().unregisterProxyController(address);
    }

    @Override
    public void registerAlias(PathElement address, AliasEntry aliasEntry) {
        getDelegate().registerAlias(address, aliasEntry);
    }

    @Override
    public void unregisterAlias(PathElement address) {
        getDelegate().unregisterAlias(address);
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        return getDelegate().getAccessConstraints();
    }

    @Override
    public AliasEntry getAliasEntry() {
        return getDelegate().getAliasEntry();
    }

    @Override
    public Set<String> getOrderedChildTypes() {
        return getDelegate().getOrderedChildTypes();
    }

    @Override
    public boolean isOrderedChildResource() {
        return getDelegate().isOrderedChildResource();
    }

    @Override
    public void registerCapability(RuntimeCapability capability) {
        getDelegate().registerCapability(capability);
    }

    @Override
    public void registerIncorporatingCapabilities(Set<RuntimeCapability> capabilities) {
        getDelegate().registerIncorporatingCapabilities(capabilities);
    }

    @Override
    public void registerRequirements(Set<? extends CapabilityReferenceRecorder> requirements) {
        getDelegate().registerRequirements(requirements);
    }

    @Override
    public Set<RuntimeCapability> getCapabilities() {
        return getDelegate().getCapabilities();
    }

    @Override
    public Set<RuntimeCapability> getIncorporatingCapabilities() {
        return getDelegate().getIncorporatingCapabilities();
    }

    private ManagementResourceRegistration getDelegate() {
        return delegateProvider.getDelegateRegistration();
    }

    @Override
    public Set<CapabilityReferenceRecorder> getRequirements() {
        return getDelegate().getRequirements();
    }

    @Override
    public void registerAdditionalRuntimePackages(RuntimePackageDependency... pkgs) {
        getDelegate().registerAdditionalRuntimePackages(pkgs);
    }

    @Override
    public Set<RuntimePackageDependency> getAdditionalRuntimePackages() {
        return getDelegate().getAdditionalRuntimePackages();
    }

}
