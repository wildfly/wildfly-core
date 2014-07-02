/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.OverrideDescriptionProvider;

/**
 * {@link ManagementResourceRegistration} implementation that simply delegates to another
 * {@link ManagementResourceRegistration}. Intended as a convenience class to allow overriding
 * of standard behaviors and also as a means to support a copy-on-write/publish-on-commit
 * semantic for the management resource tree.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
@SuppressWarnings("deprecation")
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
    public ManagementResourceRegistration registerSubModel(PathElement address, DescriptionProvider descriptionProvider) {
        return getDelegate().registerSubModel(address, descriptionProvider);
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
    public void setRuntimeOnly(boolean runtimeOnly) {
        getDelegate().setRuntimeOnly(runtimeOnly);
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
    public void registerOperationHandler(String operationName, OperationStepHandler handler, DescriptionProvider descriptionProvider, EnumSet<OperationEntry.Flag> flags) {
        getDelegate().registerOperationHandler(operationName, handler, descriptionProvider, flags);
    }

    @Override
    public void registerOperationHandler(String operationName, OperationStepHandler handler, DescriptionProvider descriptionProvider, boolean inherited) {
        getDelegate().registerOperationHandler(operationName, handler, descriptionProvider, inherited);
    }

    @Override
    public void registerOperationHandler(String operationName, OperationStepHandler handler, DescriptionProvider descriptionProvider, boolean inherited, OperationEntry.EntryType entryType) {
        getDelegate().registerOperationHandler(operationName, handler, descriptionProvider, inherited, entryType);
    }

    @Override
    public void registerOperationHandler(String operationName, OperationStepHandler handler, DescriptionProvider descriptionProvider, boolean inherited, EnumSet<OperationEntry.Flag> flags) {
        getDelegate().registerOperationHandler(operationName, handler, descriptionProvider, inherited, flags);
    }

    @Override
    public void registerOperationHandler(String operationName, OperationStepHandler handler, DescriptionProvider descriptionProvider, boolean inherited, OperationEntry.EntryType entryType, EnumSet<OperationEntry.Flag> flags) {
        getDelegate().registerOperationHandler(operationName, handler, descriptionProvider, inherited, entryType, flags);
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
    public void registerReadOnlyAttribute(String attributeName, OperationStepHandler readHandler, AttributeAccess.Storage storage) {
        getDelegate().registerReadOnlyAttribute(attributeName, readHandler, storage);
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

    private ManagementResourceRegistration getDelegate() {
        return delegateProvider.getDelegateRegistration();
    }
}
