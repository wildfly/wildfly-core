/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.as.controller.CapabilityReferenceRecorder;

import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.version.Stability;

/**
 * {@link ImmutableManagementResourceRegistration} implementation that simply delegates to another
 * {@link ImmutableManagementResourceRegistration} (typically a mutable implementation of sub-interface
 * {@link ManagementResourceRegistration}).
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DelegatingImmutableManagementResourceRegistration implements ImmutableManagementResourceRegistration {

    private final ImmutableManagementResourceRegistration delegate;

    /**
     * Creates a new ImmutableManagementResourceRegistration.
     *
     * @param delegate the delegate. Cannot be {@code null}
     */
    public DelegatingImmutableManagementResourceRegistration(ImmutableManagementResourceRegistration delegate) {
        this.delegate = delegate;
    }

    @Override
    public PathAddress getPathAddress() {
        return delegate.getPathAddress();
    }

    @Override
    public ProcessType getProcessType() {
        return delegate.getProcessType();
    }

    @Override
    public Stability getStability() {
        return this.delegate.getStability();
    }

    @Override
    public ImmutableManagementResourceRegistration getParent() {
        return delegate.getParent();
    }

    @Override
    public int getMaxOccurs() {
        return delegate.getMaxOccurs();
    }

    @Override
    public int getMinOccurs() {
        return delegate.getMinOccurs();
    }

    @Override
    public String getFeature() {
        return delegate.getFeature();
    }

    @Override
    public boolean isFeature() {
        return delegate.isFeature();
    }

    @Override
    public boolean isRuntimeOnly() {
        return delegate.isRuntimeOnly();
    }

    @Override
    public boolean isRemote() {
        return delegate.isRemote();
    }

    @Override
    public boolean isAlias() {
        return delegate.isAlias();
    }

    @Override
    public OperationEntry getOperationEntry(PathAddress address, String operationName) {
        return delegate.getOperationEntry(address, operationName);
    }

    @Override
    public OperationStepHandler getOperationHandler(PathAddress address, String operationName) {
        return delegate.getOperationHandler(address, operationName);
    }

    @Override
    public DescriptionProvider getOperationDescription(PathAddress address, String operationName) {
        return delegate.getOperationDescription(address, operationName);
    }

    @Override
    public Set<OperationEntry.Flag> getOperationFlags(PathAddress address, String operationName) {
        return delegate.getOperationFlags(address, operationName);
    }

    @Override
    public Set<String> getAttributeNames(PathAddress address) {
        return delegate.getAttributeNames(address);
    }

    @Override
    public AttributeAccess getAttributeAccess(PathAddress address, String attributeName) {
        return delegate.getAttributeAccess(address, attributeName);
    }

    @Override
    public Map<String, AttributeAccess> getAttributes(PathAddress address) {
        return delegate.getAttributes(address);
    }

    @Override
    public Set<String> getChildNames(PathAddress address) {
        return delegate.getChildNames(address);
    }

    @Override
    public Set<PathElement> getChildAddresses(PathAddress address) {
        return delegate.getChildAddresses(address);
    }

    @Override
    public DescriptionProvider getModelDescription(PathAddress address) {
        return delegate.getModelDescription(address);
    }

    @Override
    public Map<String, OperationEntry> getOperationDescriptions(PathAddress address, boolean inherited) {
        return delegate.getOperationDescriptions(address, inherited);
    }

    @Override
    public Map<String, NotificationEntry> getNotificationDescriptions(PathAddress address, boolean inherited) {
        return delegate.getNotificationDescriptions(address, inherited);
    }

    @Override
    public ProxyController getProxyController(PathAddress address) {
        return delegate.getProxyController(address);
    }

    @Override
    public Set<ProxyController> getProxyControllers(PathAddress address) {
        return delegate.getProxyControllers(address);
    }

    @Override
    public ImmutableManagementResourceRegistration getSubModel(PathAddress address) {
        ImmutableManagementResourceRegistration sub = delegate.getSubModel(address);
        return sub == null ? null : new DelegatingImmutableManagementResourceRegistration(sub);
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        return delegate.getAccessConstraints();
    }

    @Override
    public AliasEntry getAliasEntry() {
        return delegate.getAliasEntry();
    }

    @Override
    public boolean isOrderedChildResource() {
        return delegate.isOrderedChildResource();
    }

    @Override
    public Set<String> getOrderedChildTypes() {
        return delegate.getOrderedChildTypes();
    }

    @Override
    public Set<RuntimeCapability> getCapabilities() {
        return delegate.getCapabilities();
    }

    @Override
    public Set<RuntimeCapability> getIncorporatingCapabilities() {
        return delegate.getIncorporatingCapabilities();
    }

    @Override
    public Set<CapabilityReferenceRecorder> getRequirements() {
        return delegate.getRequirements();
    }

    @Override
    public Set<RuntimePackageDependency> getAdditionalRuntimePackages() {
        return delegate.getAdditionalRuntimePackages();
    }
}
