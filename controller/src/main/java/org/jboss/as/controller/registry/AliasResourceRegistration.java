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


import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.OverrideDescriptionProvider;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * An alias registration that maps to another part of the model
 *
 * @author Kabir Khan
 */
final class AliasResourceRegistration extends AbstractResourceRegistration implements DescriptionProvider {

    private final AliasEntry aliasEntry;
    private final AliasStepHandler handler;
    private final AbstractResourceRegistration target;

    AliasResourceRegistration(final String valueString, final NodeSubregistry parent, final AliasEntry aliasEntry, final AbstractResourceRegistration target) {
        super(valueString, parent);
        this.aliasEntry = aliasEntry;
        this.handler = new AliasStepHandler(aliasEntry);
        this.target = target;
    }

    @Override
    public int getMaxOccurs() {
        return target.getMaxOccurs();
    }

    @Override
    public int getMinOccurs() {
        return target.getMaxOccurs();
    }

    @Override
    OperationEntry getOperationEntry(final ListIterator<PathElement> iterator, final String operationName, OperationEntry inherited) {
        OperationEntry targetOp = target.getOperationEntry(iterator, operationName, inherited);
        if (targetOp == null) {
            return null;
        }
        return new OperationEntry(targetOp.getOperationDefinition(), handler, targetOp.isInherited());
    }

    @Override
    OperationEntry getInheritableOperationEntry(String operationName) {
        return target.getInheritableOperationEntry(operationName);
    }

    @Override
    public boolean isRuntimeOnly() {
        //TODO use target resource?
        return target.isRuntimeOnly();
    }

    @Override
    public boolean isRemote() {
        return target.isRemote();
    }

    @Override
    public boolean isAlias() {
        return true;
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        return target.getAccessConstraints();
    }

    @Override
    public boolean isFeature() {
        return target.isFeature();
    }

    @Override
    public ManagementResourceRegistration registerSubModel(final ResourceDefinition resourceDefinition) {
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
        throw alreadyRegistered();
    }

    @Override
    public void unregisterOperationHandler(final String operationName) {
        throw alreadyRegistered();
    }

    @Override
    public void registerReadWriteAttribute(AttributeDefinition definition, OperationStepHandler readHandler, OperationStepHandler writeHandler) {
        throw alreadyRegistered();
    }

    @Override
    public void registerReadOnlyAttribute(AttributeDefinition definition, OperationStepHandler readHandler) {
        throw alreadyRegistered();
    }

    @Override
    public void registerMetric(AttributeDefinition definition, OperationStepHandler metricHandler) {
        throw alreadyRegistered();
    }

    @Override
    public void unregisterAttribute(String attributeName) {
       throw alreadyRegistered();
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
    public void registerCapability(RuntimeCapability capability) {
        throw alreadyRegistered();
    }

    @Override
    public void registerIncorporatingCapabilities(Set<RuntimeCapability> capabilities) {
        throw alreadyRegistered();
    }

    @Override
    public void registerRequirements(Set<CapabilityReferenceRecorder> requirements) {
        throw alreadyRegistered();
    }

    @Override
    void getOperationDescriptions(final ListIterator<PathElement> iterator, final Map<String, OperationEntry> providers, final boolean inherited) {
        Map<String, OperationEntry> temp = new HashMap<String, OperationEntry>();
        target.getOperationDescriptions(iterator, temp, inherited);
        for (Map.Entry<String, OperationEntry> entry : providers.entrySet()) {
            OperationEntry value = entry.getValue();
            providers.put(entry.getKey(),
                    new OperationEntry(value.getOperationDefinition(), handler, value.isInherited()));
        }
    }

    @Override
    void getInheritedOperationEntries(final Map<String, OperationEntry> providers) {
        target.getInheritedOperationEntries(providers);
    }

    @Override
    void getNotificationDescriptions(ListIterator<PathElement> iterator, Map<String, NotificationEntry> providers, boolean inherited) {
        Map<String, NotificationEntry> temp = new HashMap<String, NotificationEntry>();
        target.getNotificationDescriptions(iterator, temp, inherited);
        for (Map.Entry<String, NotificationEntry> entry : providers.entrySet()) {
            providers.put(entry.getKey(),
                    new NotificationEntry(entry.getValue().getDescriptionProvider(), entry.getValue().isInherited()));
        }
    }

    @Override
    void getInheritedNotificationEntries(Map<String, NotificationEntry> providers) {
        target.getInheritedNotificationEntries(providers);
    }

    @Override
    DescriptionProvider getModelDescription(final ListIterator<PathElement> iterator) {
        return target.getModelDescription(iterator);
    }

    @Override
    Set<String> getAttributeNames(final ListIterator<PathElement> iterator) {
        return target.getAttributeNames(iterator);
    }

    @Override
    Map<String, AttributeAccess> getAttributes(final ListIterator<PathElement> iterator) {
        return target.getAttributes(iterator);
    }

    @Override
    Set<String> getChildNames(final ListIterator<PathElement> iterator) {
        return target.getChildNames(iterator);
    }

    @Override
    Set<PathElement> getChildAddresses(final ListIterator<PathElement> iterator) {
        return target.getChildAddresses(iterator);
    }

    @Override
    AttributeAccess getAttributeAccess(final ListIterator<PathElement> address, final String attributeName) {
        return target.getAttributeAccess(address, attributeName);
    }

    @Override
    ProxyController getProxyController(ListIterator<PathElement> iterator) {
        return target.getProxyController(iterator);
    }

    @Override
    void getProxyControllers(ListIterator<PathElement> iterator, Set<ProxyController> controllers) {
    }

    @Override
    ManagementResourceRegistration getResourceRegistration(ListIterator<PathElement> iterator) {
        if (!iterator.hasNext()) {
            return this;
        }
        return target.getResourceRegistration(iterator);
    }

    @Override
    public ModelNode getModelDescription(Locale locale) {
        //TODO
        return new ModelNode();
    }

    private IllegalArgumentException alreadyRegistered() {
        return ControllerLogger.ROOT_LOGGER.aliasAlreadyRegistered(getLocationString());
    }

    @Override
    public AliasEntry getAliasEntry() {
        return aliasEntry;
    }

    @Override
    protected void registerAlias(PathElement address, AliasEntry alias, AbstractResourceRegistration target) {
        throw alreadyRegistered();
    }

    @Override
    public boolean isOrderedChildResource() {
        return target.isOrderedChildResource();
    }

    @Override
    Set<String> getOrderedChildTypes(ListIterator<PathElement> iterator) {
        return target.getOrderedChildTypes(iterator);
    }

    @Override
    public void setOrderedChild(String key) {
        throw alreadyRegistered();
    }

    @Override
    Set<RuntimeCapability> getCapabilities(ListIterator<PathElement> iterator) {
        return target.getCapabilities(iterator);
    }

    @Override
    Set<RuntimeCapability> getIncorporatingCapabilities(ListIterator<PathElement> iterator) {
        return target.getIncorporatingCapabilities(iterator);
    }

    @Override
    Set<CapabilityReferenceRecorder> getRequirements(ListIterator<PathElement> iterator) {
        return target.getRequirements(iterator);
    }

    @Override
    public void registerAdditionalRuntimePackages(RuntimePackageDependency... pkgs) {
        throw alreadyRegistered();
    }

    @Override
    public Set<RuntimePackageDependency> getAdditionalRuntimePackages() {
        return target.getAdditionalRuntimePackages();
    }
}
