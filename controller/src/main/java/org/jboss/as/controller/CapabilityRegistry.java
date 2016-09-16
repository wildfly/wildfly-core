/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.jboss.as.controller.capability.Capability;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.CapabilityId;
import org.jboss.as.controller.capability.registry.CapabilityRegistration;
import org.jboss.as.controller.capability.registry.CapabilityResolutionContext;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.ImmutableCapabilityRegistry;
import org.jboss.as.controller.capability.registry.PossibleCapabilityRegistry;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.capability.registry.RuntimeRequirementRegistration;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.msc.service.ServiceName;

/**
 * Registry of {@link org.jboss.as.controller.capability.AbstractCapability capabilities} available in the system.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public final class CapabilityRegistry implements ImmutableCapabilityRegistry, PossibleCapabilityRegistry, RuntimeCapabilityRegistry {

    private final Map<CapabilityId, RuntimeCapabilityRegistration> capabilities = new HashMap<>();
    private final Map<CapabilityId, Map<String, RuntimeRequirementRegistration>> requirements = new HashMap<>();
    private final Map<CapabilityId, Map<String, RuntimeRequirementRegistration>> runtimeOnlyRequirements = new HashMap<>();
    private final boolean forServer;
    private final Set<CapabilityScope> knownContexts;
    private final ResolutionContextImpl resolutionContext = new ResolutionContextImpl();
    private final Map<CapabilityId, CapabilityRegistration> possibleCapabilities = new ConcurrentHashMap<>();
    private final Set<CapabilityId> reloadCapabilities = new HashSet<>();
    private final Set<CapabilityId> restartCapabilities = new HashSet<>();

    private final ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = reentrantReadWriteLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = reentrantReadWriteLock.writeLock();
    //holds reference to parent published registry
    private final CapabilityRegistry publishedFullRegistry;
    private boolean modified = false;

    public CapabilityRegistry(boolean forServer) {
        this(forServer, null);
    }

    private CapabilityRegistry(boolean forServer, CapabilityRegistry parent) {//for published view
        this.forServer = forServer;
        this.knownContexts = forServer ? null : new HashSet<>();
        this.publishedFullRegistry = parent;
    }

    /**
     * Creates updateable version of capability registry that on publish pushes all changes to main registry
     * this is used to create context local registry that only on completion commits changes to main registry
     *
     * @return writable registry
     */
    CapabilityRegistry createShadowCopy() {
        CapabilityRegistry result = new CapabilityRegistry(forServer, this);
        readLock.lock();
        try {
            try {
                result.writeLock.lock();
                copy(this, result);
            } finally {
                result.writeLock.unlock();
            }
        } finally {
            readLock.unlock();
        }
        return result;
    }

    private static void copyCapabilities(final Map<CapabilityId, RuntimeCapabilityRegistration> source,
                                         final Map<CapabilityId, RuntimeCapabilityRegistration> dest) {
        for (Map.Entry<CapabilityId, RuntimeCapabilityRegistration> entry : source.entrySet()) {
            dest.put(entry.getKey(), new RuntimeCapabilityRegistration(entry.getValue()));
        }
    }

    private static void copyRequirements(Map<CapabilityId, Map<String, RuntimeRequirementRegistration>> source,
                                         Map<CapabilityId, Map<String, RuntimeRequirementRegistration>> dest) {
        for (Map.Entry<CapabilityId, Map<String, RuntimeRequirementRegistration>> entry : source.entrySet()) {
            Map<String, RuntimeRequirementRegistration> mapCopy = new HashMap<>();
            for (Map.Entry<String, RuntimeRequirementRegistration> innerEntry : entry.getValue().entrySet()) {
                mapCopy.put(innerEntry.getKey(), new RuntimeRequirementRegistration(innerEntry.getValue()));
            }
            dest.put(entry.getKey(), mapCopy);
        }

    }


    /**
     * Registers a capability with the system. Any
     * {@link org.jboss.as.controller.capability.AbstractCapability#getRequirements() requirements}
     * associated with the capability will be recorded as requirements.
     *
     * @param capabilityRegistration the capability. Cannot be {@code null}
     */
    @Override
    public void registerCapability(RuntimeCapabilityRegistration capabilityRegistration) {
        writeLock.lock();
        try {
            CapabilityId capabilityId = capabilityRegistration.getCapabilityId();
            RegistrationPoint rp = capabilityRegistration.getOldestRegistrationPoint();
            RuntimeCapabilityRegistration currentRegistration = capabilities.get(capabilityId);
            if (currentRegistration != null) {
                // The actual capability must be the same, and we must not already have a registration
                // from this resource
                if (!Objects.equals(capabilityRegistration.getCapability(), currentRegistration.getCapability())
                        || !currentRegistration.addRegistrationPoint(rp)) {
                    throw ControllerLogger.MGMT_OP_LOGGER.capabilityAlreadyRegisteredInContext(capabilityId.getName(),
                            capabilityId.getScope().getName());
                }
                // else it was ok, and we just recorded the additional registration point
            } else {
                capabilities.put(capabilityId, capabilityRegistration);
            }

            // Add any hard requirements
            for (String req : capabilityRegistration.getCapability().getRequirements()) {
                registerRequirement(new RuntimeRequirementRegistration(req, capabilityId.getName(),
                        capabilityId.getScope(), rp));
            }

            if (!forServer) {
                CapabilityScope capContext = capabilityId.getScope();
                knownContexts.add(capContext);
            }
            modified = true;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Registers an additional requirement a capability has beyond what it was aware of when {@code capability}
     * was passed to {@link #registerCapability(RuntimeCapabilityRegistration)}. Used for cases
     * where a capability optionally depends on another capability, and whether or not that requirement is needed is
     * not known when the capability is first registered.
     *
     *
     * @param requirement the requirement
     * @throws java.lang.IllegalArgumentException if no matching capability is currently
     *                                            {@link #registerCapability(RuntimeCapabilityRegistration) registered} for either {@code required} or {@code dependent}
     */
    @Override
    public void registerAdditionalCapabilityRequirement(RuntimeRequirementRegistration requirement) {
        writeLock.lock();
        try {
            registerRequirement(requirement);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * This must be called with the write lock held.
     * @param requirement the requirement
     */
    private void registerRequirement(RuntimeRequirementRegistration requirement) {
        assert writeLock.isHeldByCurrentThread();
        CapabilityId dependentId = requirement.getDependentId();
        if (!capabilities.containsKey(dependentId)) {
            throw ControllerLogger.MGMT_OP_LOGGER.unknownCapabilityInContext(dependentId.getName(),
                    dependentId.getScope().getName());
        }
        Map<CapabilityId, Map<String, RuntimeRequirementRegistration>> requirementMap =
                requirement.isRuntimeOnly() ? runtimeOnlyRequirements : requirements;

        Map<String, RuntimeRequirementRegistration> dependents = requirementMap.get(dependentId);
        if (dependents == null) {
            dependents = new HashMap<>();
            requirementMap.put(dependentId, dependents);
        }
        RuntimeRequirementRegistration existing = dependents.get(requirement.getRequiredName());
        if (existing == null) {
            dependents.put(requirement.getRequiredName(), requirement);
        } else {
            existing.addRegistrationPoint(requirement.getOldestRegistrationPoint());
        }
        modified = true;
    }

    /**
     * Remove a previously registered requirement for a capability.
     *
     * @param requirementRegistration the requirement. Cannot be {@code null}
     * @see #registerAdditionalCapabilityRequirement(org.jboss.as.controller.capability.registry.RuntimeRequirementRegistration)
     */
    @Override
    public void removeCapabilityRequirement(RuntimeRequirementRegistration requirementRegistration) {
        // We don't know if this got registered as an runtime-only requirement or a hard one
        // so clean it from both maps
        writeLock.lock();
        try {
            removeRequirement(requirementRegistration, false);
            removeRequirement(requirementRegistration, true);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Remove a previously registered capability if all registration points for it have been removed.
     *
     * @param capabilityName    the name of the capability. Cannot be {@code null}
     * @param scope           the context in which the capability is registered. Cannot be {@code null}
     * @param registrationPoint the specific registration point that is being removed
     * @return the capability that was removed, or {@code null} if no matching capability was registered or other
     * registration points for the capability still exist
     */
    @Override
    public RuntimeCapabilityRegistration removeCapability(String capabilityName, CapabilityScope scope,
                                                          PathAddress registrationPoint) {
        writeLock.lock();
        try {
            CapabilityId capabilityId = new CapabilityId(capabilityName, scope);
            RuntimeCapabilityRegistration removed = null;
            RuntimeCapabilityRegistration candidate = capabilities.get(capabilityId);
            if (candidate != null) {
                RegistrationPoint rp = new RegistrationPoint(registrationPoint, null);
                if (candidate.removeRegistrationPoint(rp)) {
                    if (candidate.getRegistrationPointCount() == 0) {
                        removed = capabilities.remove(capabilityId);
                        requirements.remove(capabilityId);
                        runtimeOnlyRequirements.remove(capabilityId);
                    } else {
                        // There are still registration points for this capability.
                        // So just remove the requirements for this registration point
                        Map<String, RuntimeRequirementRegistration> candidateRequirements = requirements.get(capabilityId);
                        if (candidateRequirements != null) {
                            // Iterate over array to avoid ConcurrentModificationException
                            for (String req : candidateRequirements.keySet().toArray(new String[candidateRequirements.size()])) {
                                removeRequirement(new RuntimeRequirementRegistration(req, capabilityName, scope, rp), false);
                            }
                        }
                        candidateRequirements = runtimeOnlyRequirements.get(capabilityId);
                        if (candidateRequirements != null) {
                            // Iterate over array to avoid ConcurrentModificationException
                            for (String req : candidateRequirements.keySet().toArray(new String[candidateRequirements.size()])) {
                                removeRequirement(new RuntimeRequirementRegistration(req, capabilityName, scope, rp), true);
                            }
                        }
                    }
                }
            }

            if (removed != null) {
                modified = true;
            }
            return removed;
        } finally {
            writeLock.unlock();
        }
    }

    private void removeRequirement(RuntimeRequirementRegistration requirementRegistration, boolean optional) {
        assert writeLock.isHeldByCurrentThread();
        Map<CapabilityId, Map<String, RuntimeRequirementRegistration>> requirementMap = optional ? runtimeOnlyRequirements : requirements;
        Map<String, RuntimeRequirementRegistration> dependents = requirementMap.get(requirementRegistration.getDependentId());
        if (dependents != null) {
            RuntimeRequirementRegistration rrr = dependents.get(requirementRegistration.getRequiredName());
            if (rrr != null) {
                rrr.removeRegistrationPoint(requirementRegistration.getOldestRegistrationPoint());
                if (rrr.getRegistrationPointCount() == 0) {
                    dependents.remove(requirementRegistration.getRequiredName());
                }
                if (dependents.size() == 0) {
                    requirementMap.remove(requirementRegistration.getDependentId());
                }
                modified = true;
            }
        }
    }

    @Override
    public Map<CapabilityId, RuntimeStatus> getRuntimeStatus(PathAddress address, ImmutableManagementResourceRegistration resourceRegistration) {
        readLock.lock();
        try {
            Map<CapabilityId, RuntimeStatus> result;
            Set<CapabilityId> ids = getCapabilitiesForAddress(address, resourceRegistration);
            int size = ids.size();
            if (size == 0) {
                result = Collections.emptyMap();
            } else {
                Set<CapabilityId> examined = new HashSet<>();
                if (size == 1) {
                    CapabilityId id = ids.iterator().next();
                    result = Collections.singletonMap(id, getCapabilityStatus(id, examined));
                } else {
                    result = new HashMap<>(size);
                    for (CapabilityId id : ids) {
                        result.put(id, getCapabilityStatus(id, examined));
                    }
                }
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }

    private RuntimeStatus getCapabilityStatus(CapabilityId id, Set<CapabilityId> examined) {
        // This is meant for checking runtime stuff, which should only be for servers or
        // HC runtime stuff, both of which use CapabilityScope.GLOBAL or HostCapabilityScope. So this assert
        // is to check that assumption is valid, as further thought is needed if not (e.g. see WFCORE-1710).
        // The id.getScope().getName().equals(HOST) check is a bit of a hack into HostCapabilityScope's
        // internals, but oh well.
        assert id.getScope().equals(CapabilityScope.GLOBAL) || id.getScope().getName().equals(HOST);

        if (restartCapabilities.contains(id)) {
            return RuntimeStatus.RESTART_REQUIRED;
        }
        if (reloadCapabilities.contains(id)) {
            return RuntimeStatus.RELOAD_REQUIRED;
        }
        examined.add(id);

        Map<String, RuntimeRequirementRegistration> dependents = requirements.get(id);
        return getDependentCapabilityStatus(dependents, id, examined);
    }

    private RuntimeStatus getDependentCapabilityStatus(Map<String, RuntimeRequirementRegistration> dependents, CapabilityId requiror, Set<CapabilityId> examined) {
        RuntimeStatus result = RuntimeStatus.NORMAL;
        if (dependents != null) {
            for (String dependent : dependents.keySet()) {
                CapabilityScope requirorScope = requiror.getScope();
                List<CapabilityScope> toCheck = requirorScope == CapabilityScope.GLOBAL
                        ? Collections.singletonList(requirorScope)
                        : Arrays.asList(requirorScope, CapabilityScope.GLOBAL);
                for (CapabilityScope scope : toCheck) {
                    CapabilityId dependentId = new CapabilityId(dependent, scope);
                    if (!examined.contains(dependentId)) {
                        RuntimeStatus status = getCapabilityStatus(dependentId, examined);
                        if (status == RuntimeStatus.RESTART_REQUIRED) {
                            return status; // no need to check anything else
                        } else if (status == RuntimeStatus.RELOAD_REQUIRED) {
                            result = status;
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void capabilityReloadRequired(PathAddress address, ImmutableManagementResourceRegistration resourceRegistration) {
        writeLock.lock();
        try {
            reloadCapabilities.addAll(getCapabilitiesForAddress(address, resourceRegistration));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void capabilityRestartRequired(PathAddress address, ImmutableManagementResourceRegistration resourceRegistration) {
        writeLock.lock();
        try {
            restartCapabilities.addAll(getCapabilitiesForAddress(address, resourceRegistration));
        } finally {
            writeLock.unlock();
        }
    }

    private Set<CapabilityId> getCapabilitiesForAddress(PathAddress address, ImmutableManagementResourceRegistration resourceRegistration) {
        Set<CapabilityId> result = null;
        PathAddress curAddress = address;
        ImmutableManagementResourceRegistration curReg = resourceRegistration;
        while (result == null) {

            // Track the names of any incorporating capabilities associated with this address
            Set<RuntimeCapability> incorporating = curReg.getIncorporatingCapabilities();
            Set<String> incorporatingDynamic = null;
            Set<String> incorporatingFull = null;
            if (incorporating != null && !incorporating.isEmpty()) {
                for (RuntimeCapability rc : incorporating) {
                    if (rc.isDynamicallyNamed()) {
                        if (incorporatingDynamic == null) {
                            incorporatingDynamic = new HashSet<>();
                        }
                        incorporatingDynamic.add(rc.getName());
                    } else {
                        if (incorporatingFull == null) {
                            incorporatingFull = new HashSet<>();
                        }
                        incorporatingFull.add(rc.getName());
                    }
                }
            }

            // TODO this is inefficient. But it's only called for post-boot write ops
            // when the process is already reload-required
            for (Map.Entry<CapabilityId, RuntimeCapabilityRegistration> entry : capabilities.entrySet()) {
                boolean checkIncorporating = false;
                if (incorporatingFull != null) {
                    checkIncorporating = incorporatingFull.contains(entry.getKey().getName());
                }
                if (!checkIncorporating && incorporatingDynamic != null) {
                    String name = entry.getKey().getName();
                    int lastDot = name.lastIndexOf('.');
                    if (lastDot > 0) {
                        String baseName = name.substring(0, lastDot);
                        checkIncorporating = incorporatingDynamic.contains(baseName);
                    }
                }
                for (RegistrationPoint point : entry.getValue().getRegistrationPoints()) {
                    PathAddress pointAddress = point.getAddress();
                    if (curAddress.equals(pointAddress)
                            || (checkIncorporating && curAddress.size() > pointAddress.size()
                                && pointAddress.equals(curAddress.subAddress(0, pointAddress.size())))) {
                        if (result == null) {
                            result = new HashSet<>();
                        }
                        result.add(entry.getKey());
                        break;
                    }
                }
            }

            if (result == null && incorporating != null) {
                // No match, but incorporating != null means the MRR doesn't want us to keep looking higher
                result = Collections.emptySet();
            }

            if (result == null) {
                // This address exposed no capability, but it may represent a config chunk for
                // a capability exposed by a parent resource, so we need to check parents.
                //
                // Here we check up to the first child level. The root resource for a process
                // will not expose a capability that is configured by children, so it is incorrect
                // to check beyond the first level. In the domain mode /host=* tree, we
                // only check up to the 2nd child level, because the /host=x level is the root node
                // for the HC process.
                // We stop navigating up when the current address' final path element is subsystem=X,
                // because above that level are kernel resources, and kernel resources do not expose
                // capabilities that are configured by subsystem children.
                int addrSize = curAddress.size();
                if (addrSize > 1 && !SUBSYSTEM.equals(curAddress.getLastElement().getKey())
                        && !(addrSize == 2 && HOST.equals(curAddress.getElement(0).getKey()))) {
                    curAddress = curAddress.getParent();
                    curReg = curReg.getParent();
                    // loop continues
                } else {
                    result = Collections.emptySet();
                }
            }
        }
        return result;
    }


    /**
     * Registers a capability with the system. Any
     * {@link org.jboss.as.controller.capability.AbstractCapability#getRequirements() requirements}
     * associated with the capability will be recorded as requirements.
     *
     * @param capability the capability. Cannot be {@code null}
     */
    @Override
    public void registerPossibleCapability(Capability capability, PathAddress registrationPoint) {
        final CapabilityId capabilityId = new CapabilityId(capability.getName(), CapabilityScope.GLOBAL);
        RegistrationPoint point = new RegistrationPoint(registrationPoint, null);
        CapabilityRegistration capabilityRegistration = new CapabilityRegistration<>(capability, CapabilityScope.GLOBAL, point);
        writeLock.lock();
        try {
            possibleCapabilities.computeIfPresent(capabilityId, (capabilityId1, currentRegistration) -> {
                RegistrationPoint rp = capabilityRegistration.getOldestRegistrationPoint();
                // The actual capability must be the same, and we must not already have a registration
                // from this resource
                if (!Objects.equals(capabilityRegistration.getCapability(), currentRegistration.getCapability())
                        || !currentRegistration.addRegistrationPoint(rp)) {
                    throw ControllerLogger.MGMT_OP_LOGGER.capabilityAlreadyRegisteredInContext(capabilityId.getName(),
                            capabilityId.getScope().getName());
                }
                return currentRegistration;
            });
            possibleCapabilities.putIfAbsent(capabilityId, capabilityRegistration);
            modified = true;
        } finally {
            writeLock.unlock();
        }
    }


    /**
     * Remove a previously registered capability if all registration points for it have been removed.
     *
     * @param capability        the capability. Cannot be {@code null}
     * @param registrationPoint the specific registration point that is being removed
     * @return the capability that was removed, or {@code null} if no matching capability was registered or other
     * registration points for the capability still exist
     */
    @Override
    public CapabilityRegistration removePossibleCapability(Capability capability, PathAddress registrationPoint) {
        CapabilityId capabilityId = new CapabilityId(capability.getName(), CapabilityScope.GLOBAL);
        CapabilityRegistration removed = null;
        writeLock.lock();
        try {
            CapabilityRegistration candidate = possibleCapabilities.get(capabilityId);
            if (candidate != null) {
                RegistrationPoint rp = new RegistrationPoint(registrationPoint, null);
                if (candidate.removeRegistrationPoint(rp)) {
                    if (candidate.getRegistrationPointCount() == 0) {
                        removed = possibleCapabilities.remove(capabilityId);
                    } else {
                        removed = candidate;
                    }
                }
            }

            if (removed != null) {
                modified = true;
            }
            return removed;
        } finally {
            writeLock.unlock();
        }
    }


    //ImmutableCapabilityRegistry methods

    @Override
    public boolean hasCapability(String capabilityName, CapabilityScope scope) {
        readLock.lock();
        try {
            return findSatisfactoryCapability(capabilityName, scope, !forServer) != null;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <T> T getCapabilityRuntimeAPI(String capabilityName, CapabilityScope scope, Class<T> apiType) {
        // Here we can't know the dependent name. So this can only be called when resolution is complete.
        assert resolutionContext.resolutionComplete;
        readLock.lock();
        try {
            RuntimeCapabilityRegistration reg = getCapabilityRegistration(capabilityName, scope);
            Object api = reg.getCapability().getRuntimeAPI();
            if (api == null) {
                throw ControllerLogger.MGMT_OP_LOGGER.capabilityDoesNotExposeRuntimeAPI(capabilityName);
            }
            return apiType.cast(api);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Set<CapabilityRegistration> getCapabilities() {
        readLock.lock();
        try {
            return Collections.unmodifiableSet(new TreeSet<>(capabilities.values()));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Set<CapabilityRegistration> getPossibleCapabilities() {
        readLock.lock();
        try {
            return Collections.unmodifiableSet(new TreeSet<>(possibleCapabilities.values()));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public ServiceName getCapabilityServiceName(String capabilityName, CapabilityScope scope, Class<?> serviceType) {
        // Here we can't know the dependent name. So this can only be called when resolution is complete.
        assert resolutionContext.resolutionComplete;
        readLock.lock();
        try {
            RuntimeCapabilityRegistration reg = getCapabilityRegistration(capabilityName, scope);
            RuntimeCapability<?> cap = reg.getCapability();
            return cap.getCapabilityServiceName(serviceType);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Set<PathAddress> getPossibleProviderPoints(CapabilityId capabilityId) {
        Set<PathAddress> result = new LinkedHashSet<>();
        readLock.lock();
        try {
            capabilityId = capabilityId.getScope() == CapabilityScope.GLOBAL ? capabilityId : new CapabilityId(capabilityId.getName(), CapabilityScope.GLOBAL); //possible registry is only in global scope
            CapabilityRegistration<RuntimeCapability> reg =  possibleCapabilities.get(capabilityId);
            if (reg != null) {
                result.addAll(reg.getRegistrationPoints().stream().map(RegistrationPoint::getAddress).collect(Collectors.toList()));
            }

        } finally {
            readLock.unlock();
        }
        return result;
    }

    public CapabilityRegistration getCapability(CapabilityId capabilityId){
        readLock.lock();
        try {
            CapabilityRegistration<RuntimeCapability> reg = capabilities.get(capabilityId);
            return reg != null ? new CapabilityRegistration<>(reg) : null;
        } finally {
            readLock.unlock();
        }

    }

    //end ImmutableCapabilityRegistry methods

    /**
     * Publish the changes to main registry
     */
    void publish() {
        assert publishedFullRegistry != null : "Cannot write directly to main registry";

        writeLock.lock();
        try {
            if (!modified) {
                return;
            }
            publishedFullRegistry.writeLock.lock();
            try {
                publishedFullRegistry.clear(true);
                copy(this, publishedFullRegistry);
                modified = false;
            } finally {
                publishedFullRegistry.writeLock.unlock();
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Discard the changes.
     */
    void rollback() {
        if (publishedFullRegistry == null) {
            return;
        }
        writeLock.lock();
        try {
            publishedFullRegistry.readLock.lock();
            try {
                clear(true);
                copy(publishedFullRegistry, this);
                modified = false;
            } finally {
                publishedFullRegistry.readLock.unlock();
            }
        } finally {
            writeLock.unlock();
        }
    }

    boolean isModified() {
        readLock.lock();
        try {
            return modified;
        } finally {
            readLock.unlock();
        }
    }

    private void copy(CapabilityRegistry source, CapabilityRegistry target) {
        assert target.writeLock.isHeldByCurrentThread();
        copyCapabilities(source.capabilities, target.capabilities);
        source.possibleCapabilities.entrySet().stream().forEach(entry -> {
            target.possibleCapabilities.put(entry.getKey(), new CapabilityRegistration(entry.getValue()));
        });
        copyRequirements(source.requirements, target.requirements);
        copyRequirements(source.runtimeOnlyRequirements, target.runtimeOnlyRequirements);
        target.reloadCapabilities.addAll(source.reloadCapabilities);
        target.restartCapabilities.addAll(source.restartCapabilities);
        if (!forServer) {
            target.knownContexts.addAll(source.knownContexts);
        }
    }

    /**
     * Clears capability registry
     */
    void clear() {
        clear(false);
    }

    private void clear(boolean restartRequired) {
        writeLock.lock();
        try {
            capabilities.clear();
            possibleCapabilities.clear();
            requirements.clear();
            runtimeOnlyRequirements.clear();
            reloadCapabilities.clear();
            if (restartRequired) {
                restartCapabilities.clear();
            }
            modified = true;
        } finally {
            writeLock.unlock();
        }
    }


    CapabilityValidation resolveCapabilities(Resource rootResource, boolean hostXmlOnly) {
        readLock.lock();
        try {
            resolutionContext.setRootResource(rootResource);
            assert resolutionContext.rootResource != null;
            Map<CapabilityId, Set<RuntimeRequirementRegistration>> missing = new HashMap<>();

            // Vars for tracking inconsistent contexts
            boolean isInconsistent = false;
            Map<CapabilityScope, Set<RuntimeRequirementRegistration>> requiresConsistency = null;
            Map<CapabilityScope, Set<CapabilityScope>> consistentSets = null;

            for (Map.Entry<CapabilityId, Map<String, RuntimeRequirementRegistration>> entry : requirements.entrySet()) {
                CapabilityId dependentId = entry.getKey();
                String dependentName = dependentId.getName();
                CapabilityScope dependentContext = dependentId.getScope();
                Set<CapabilityScope> consistentSet = consistentSets == null ? null : consistentSets.get(dependentContext);
                for (RuntimeRequirementRegistration req : entry.getValue().values()) {
                    SatisfactoryCapability satisfactory = findSatisfactoryCapability(req.getRequiredName(), dependentContext, !forServer);
                    if (satisfactory == null) {
                        // Missing
                        if (hostXmlOnly && dependentName.startsWith("org.wildfly.domain.server-config.")
                                && (req.getRequiredName().startsWith("org.wildfly.domain.server-group.")
                                || req.getRequiredName().startsWith("org.wildfly.domain.socket-binding-group."))) {
                            // HACK. We can't resolve these now as we have no domain model at this part of boot
                            // We can resolve them when the domain model ops run, so wait to validate then
                            ControllerLogger.MGMT_OP_LOGGER.tracef("Ignoring that dependent %s cannot resolve required capability %s as the 'hostXmlOnly' param is set", dependentId, req.getRequiredName());
                            continue;
                        }
                        CapabilityId basicId = new CapabilityId(req.getRequiredName(), dependentContext);
                        Set<RuntimeRequirementRegistration> set = missing.get(basicId);
                        if (set == null) {
                            set = new HashSet<>();
                            missing.put(basicId, set);
                        }
                        set.add(req);
                    } else if (satisfactory.multipleCapabilities != null) {
                        // This requirement is one that needs tracking to ensure that all similar ones for this
                        // dependent context can be resolved against at least one context
                        if (requiresConsistency == null) {
                            requiresConsistency = new HashMap<>();
                            consistentSets = new HashMap<>();
                        }

                        CapabilityScope reqDependent = req.getDependentContext();
                        recordConsistentSets(requiresConsistency, consistentSets, reqDependent, consistentSet, req, satisfactory, reqDependent);
                        isInconsistent = isInconsistent || (consistentSet != null && consistentSet.size() == 0);

                        // Record for any contexts that include this one
                        for (CapabilityScope including : dependentContext.getIncludingScopes(resolutionContext)) {
                            consistentSet = consistentSets.get(including);
                            recordConsistentSets(requiresConsistency, consistentSets, including, consistentSet, req, satisfactory, reqDependent);
                            isInconsistent = isInconsistent || (consistentSet != null && consistentSet.size() == 0);
                        }
                    } // else simple capability match
                }
            }

            // We've finished resolution
            resolutionContext.resolutionComplete = true;

            if (isInconsistent) {
                // This is the exception case. Figure out the details of the problems
                return new CapabilityValidation(missing, findInconsistent(requiresConsistency, consistentSets), resolutionContext);
            } else if (!missing.isEmpty()) {
                return new CapabilityValidation(missing, null, resolutionContext);
            }

            return CapabilityValidation.OK;
        } finally {
            readLock.unlock();
        }
    }

    private void recordConsistentSets(Map<CapabilityScope, Set<RuntimeRequirementRegistration>> requiresConsistency, Map<CapabilityScope, Set<CapabilityScope>> consistentSets, CapabilityScope dependentContext, Set<CapabilityScope> consistentSet, RuntimeRequirementRegistration req, SatisfactoryCapability satisfactory, CapabilityScope reqDependent) {
        Set<RuntimeRequirementRegistration> requiresForDependent = requiresConsistency.get(reqDependent);
        if (requiresForDependent == null) {
            requiresForDependent = new HashSet<>();
            requiresConsistency.put(reqDependent, requiresForDependent);
        }
        requiresForDependent.add(req);
        if (consistentSet == null) {
            consistentSet = new HashSet<>(satisfactory.multipleCapabilities); // copy okContexts so retainAll calls won't mutate it
            consistentSets.put(dependentContext, consistentSet);
        } else {
            consistentSet.retainAll(satisfactory.multipleCapabilities);
        }
    }

    private static Set<RuntimeRequirementRegistration> findInconsistent(Map<CapabilityScope, Set<RuntimeRequirementRegistration>> requiresConsistency,
                                                                        Map<CapabilityScope, Set<CapabilityScope>> consistentSets) {
        Set<RuntimeRequirementRegistration> result = new HashSet<>();
        for (Map.Entry<CapabilityScope, Set<CapabilityScope>> entry : consistentSets.entrySet()) {
            if (entry.getValue().isEmpty()) {
                // This one is a problem; see what all requirements are from the dependent context
                Set<RuntimeRequirementRegistration> contextDependents = requiresConsistency.get(entry.getKey());
                if (contextDependents != null) {
                    result.addAll(contextDependents);
                }
            }
        }
        return result;
    }

    private RuntimeCapabilityRegistration getCapabilityRegistration(String capabilityName, CapabilityScope capabilityScope) {
        SatisfactoryCapability satisfactoryCapability = findSatisfactoryCapability(capabilityName, capabilityScope, false);
        if (satisfactoryCapability == null) {
            if (forServer) {
                throw ControllerLogger.MGMT_OP_LOGGER.unknownCapability(capabilityName);
            } else {
                throw ControllerLogger.MGMT_OP_LOGGER.unknownCapabilityInContext(capabilityName, capabilityScope.getName());
            }
        }
        return capabilities.get(satisfactoryCapability.singleCapability);
    }

    private SatisfactoryCapability findSatisfactoryCapability(String capabilityName, CapabilityScope dependentContext,
                                                              boolean requireConsistency) {

        // Check for a simple match
        CapabilityId requestedId = new CapabilityId(capabilityName, dependentContext);
        if (capabilities.containsKey(requestedId)) {
            return new SatisfactoryCapability(requestedId);
        }

        if (!forServer) {
            // Try other contexts that satisfy the requested one
            Set<CapabilityScope> multiple = null;
            for (CapabilityScope satisfies : knownContexts) {
                if (satisfies.equals(dependentContext)) {
                    // We already know this one doesn't exist
                    continue;
                }
                CapabilityId satisfiesId = new CapabilityId(capabilityName, satisfies);
                if (capabilities.containsKey(satisfiesId) && satisfies.canSatisfyRequirement(capabilityName, dependentContext, resolutionContext)) {
                    if (!requireConsistency || !satisfies.requiresConsistencyCheck()) {
                        return new SatisfactoryCapability(satisfiesId);
                    } else {
                        if (multiple == null) {
                            multiple = new HashSet<>();
                        }
                        multiple.add(satisfies);
                        multiple.addAll(satisfies.getIncludingScopes(resolutionContext));
                    }
                }
            }
            if (multiple != null) {
                return new SatisfactoryCapability(multiple);
            }
        }
        return null;
    }


    private static class ResolutionContextImpl extends CapabilityResolutionContext {
        private boolean resolutionComplete;
        private Resource rootResource;

        @Override
        public Resource getResourceRoot() {
            assert rootResource != null;
            return rootResource;
        }

        void setRootResource(Resource rootResource) {
            this.rootResource = rootResource;
            reset();
            this.resolutionComplete = false;
        }
    }

    private static class SatisfactoryCapability {
        final CapabilityId singleCapability;
        final Set<CapabilityScope> multipleCapabilities;

        SatisfactoryCapability(CapabilityId singleCapability) {
            this.singleCapability = singleCapability;
            this.multipleCapabilities = null;
        }

        SatisfactoryCapability(Set<CapabilityScope> multipleCapabilities) {
            this.singleCapability = null;
            this.multipleCapabilities = multipleCapabilities;
        }
    }

    /**
     *
     */
    static class CapabilityValidation {

        public static final CapabilityValidation OK = new CapabilityValidation(null, null, null);
        private final Map<CapabilityId, Set<RuntimeRequirementRegistration>> missingRequirements;
        private final Set<RuntimeRequirementRegistration> inconsistentRequirements;
        private final CapabilityResolutionContext resolutionContext;

        private CapabilityValidation(Map<CapabilityId, Set<RuntimeRequirementRegistration>> missingRequirements,
                                     Set<RuntimeRequirementRegistration> inconsistentRequirements,
                                     CapabilityResolutionContext resolutionContext) {
            this.resolutionContext = resolutionContext;
            this.missingRequirements = missingRequirements == null
                    ? Collections.<CapabilityId, Set<RuntimeRequirementRegistration>>emptyMap() : missingRequirements;
            this.inconsistentRequirements = inconsistentRequirements == null
                    ? Collections.emptySet() : inconsistentRequirements;
        }

        /**
         * @return a map whose keys are missing capabilities and whose values are the requirement registrations
         * from other capabilities that require that capability. Will not return {@code null}
         */
        public Map<CapabilityId, Set<RuntimeRequirementRegistration>> getMissingRequirements() {
            return missingRequirements;
        }

        /**
         * @return requirement registrations that cannot be consistently resolved. Will nto return {@code null}
         */
        public Set<RuntimeRequirementRegistration> getInconsistentRequirements() {
            return inconsistentRequirements;
        }

        /**
         * @return the resolution context used to perform the resolution. May be {@code null} if no resolution
         * problems occurred
         */
        public CapabilityResolutionContext getCapabilityResolutionContext() {
            return resolutionContext;
        }

        public boolean isValid() {
            return missingRequirements.isEmpty() && inconsistentRequirements.isEmpty();
        }
    }

}
