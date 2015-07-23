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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.as.controller.capability.Capability;
import org.jboss.as.controller.capability.registry.CapabilityContext;
import org.jboss.as.controller.capability.registry.CapabilityId;
import org.jboss.as.controller.capability.registry.CapabilityRegistration;
import org.jboss.as.controller.capability.registry.CapabilityResolutionContext;
import org.jboss.as.controller.capability.registry.ImmutableCapabilityRegistry;
import org.jboss.as.controller.capability.registry.ImmutableCapabilityRegistryImpl;
import org.jboss.as.controller.capability.registry.PossibleCapabilityRegistry;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.capability.registry.RuntimeRequirementRegistration;
import org.jboss.as.controller.logging.ControllerLogger;
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
    private final Set<CapabilityContext> knownContexts;
    private final ResolutionContextImpl resolutionContext = new ResolutionContextImpl();
    private final Map<CapabilityId, CapabilityRegistration> possibleCapabilities = new ConcurrentHashMap<>();


    private final ImmutableCapabilityRegistry readOnlyRegistry;
    //holds reference to parent published registry
    private final CapabilityRegistry publishedFullRegistry;
    private final ImmutableCapabilityRegistry publishedCombinedRegistry;


    public CapabilityRegistry(boolean forServer) {
        this(forServer, null);
    }

    private CapabilityRegistry(boolean forServer, CapabilityRegistry parent) {//for published view
        this.forServer = forServer;
        this.knownContexts = forServer ? null : new HashSet<>();
        this.publishedFullRegistry = parent;
        readOnlyRegistry = createReadOnlyRegistry();
        if (parent==null){
            publishedCombinedRegistry = readOnlyRegistry;
        }else{
            publishedCombinedRegistry = new CombinedImmutableCapabilityRegistry(this);
        }
    }

    /**
     * Creates updateable version of capability registry that on publish pushes all changes to main registry
     * this is used to create context local registry that only on completion commits changes to main registry
     * @return writable registry
     */
    CapabilityRegistry createShadowCopy() {
        return new CapabilityRegistry(forServer, this);
    }


    /**
     * Registers a capability with the system. Any
     * {@link org.jboss.as.controller.capability.AbstractCapability#getRequirements() requirements}
     * associated with the capability will be recorded as requirements.
     *
     * @param capabilityRegistration the capability. Cannot be {@code null}
     */
    @Override
    public synchronized void registerCapability(RuntimeCapabilityRegistration capabilityRegistration) {

        CapabilityId capabilityId = capabilityRegistration.getCapabilityId();
        RegistrationPoint rp = capabilityRegistration.getOldestRegistrationPoint();
        RuntimeCapabilityRegistration currentRegistration = capabilities.get(capabilityId);
        if (currentRegistration != null) {
            // The actual capability must be the same, and we must not already have a registration
            // from this resource
            if (!Objects.equals(capabilityRegistration.getCapability(), currentRegistration.getCapability())
                    || !currentRegistration.addRegistrationPoint(rp)) {
                throw ControllerLogger.MGMT_OP_LOGGER.capabilityAlreadyRegisteredInContext(capabilityId.getName(),
                        capabilityId.getContext().getName());
            }
            // else it was ok, and we just recorded the additional registration point
        } else {
            capabilities.put(capabilityId, capabilityRegistration);
        }

        // Add any hard requirements
        for (String req : capabilityRegistration.getCapability().getRequirements()) {
            registerRequirement(new RuntimeRequirementRegistration(req, capabilityId.getName(),
                    capabilityId.getContext(), rp));
        }

        if (!forServer) {
            CapabilityContext capContext = capabilityId.getContext();
            knownContexts.add(capContext);
        }
    }

    /**
     * Registers an additional requirement a capability has beyond what it was aware of when {@code capability}
     * was passed to {@link #registerCapability(RuntimeCapabilityRegistration)}. Used for cases
     * where a capability optionally depends on another capability, and whether or not that requirement is needed is
     * not known when the capability is first registered.
     *
     * @param requirement the requirement
     * @throws java.lang.IllegalArgumentException if no matching capability is currently
     *                                            {@link #registerCapability(RuntimeCapabilityRegistration) registered} for either {@code required} or {@code dependent}
     */
    @Override
    public boolean registerAdditionalCapabilityRequirement(RuntimeRequirementRegistration requirement) {
        if (hasCapability(requirement.getRequiredName(), requirement.getDependentName(), requirement.getDependentContext())) {
            registerRequirement(requirement);
            return true;
        }
        return false;
    }

    private void registerRequirement(RuntimeRequirementRegistration requirement) {
        CapabilityId dependentId = requirement.getDependentId();
        if (!capabilities.containsKey(dependentId)) {
            throw ControllerLogger.MGMT_OP_LOGGER.unknownCapabilityInContext(dependentId.getName(),
                    dependentId.getContext().getName());
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
    }

    /**
     * Remove a previously registered requirement for a capability.
     *
     * @param requirementRegistration the requirement. Cannot be {@code null}
     * @see #registerAdditionalCapabilityRequirement(org.jboss.as.controller.capability.registry.RuntimeRequirementRegistration)
     */
    @Override
    public synchronized void removeCapabilityRequirement(RuntimeRequirementRegistration requirementRegistration) {
        // We don't know if this got registered as an runtime-only requirement or a hard one
        // so clean it from both maps
        removeRequirement(requirementRegistration, false);
        removeRequirement(requirementRegistration, true);
    }

    /**
     * Remove a previously registered capability if all registration points for it have been removed.
     *
     * @param capabilityName    the name of the capability. Cannot be {@code null}
     * @param context           the context in which the capability is registered. Cannot be {@code null}
     * @param registrationPoint the specific registration point that is being removed
     * @return the capability that was removed, or {@code null} if no matching capability was registered or other
     * registration points for the capability still exist
     */
    @Override
    public synchronized RuntimeCapabilityRegistration removeCapability(String capabilityName, CapabilityContext context,
                                                                       PathAddress registrationPoint) {
        CapabilityId capabilityId = new CapabilityId(capabilityName, context);
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
                            removeRequirement(new RuntimeRequirementRegistration(req, capabilityName, context, rp), false);
                        }
                    }
                    candidateRequirements = runtimeOnlyRequirements.get(capabilityId);
                    if (candidateRequirements != null) {
                        // Iterate over array to avoid ConcurrentModificationException
                        for (String req : candidateRequirements.keySet().toArray(new String[candidateRequirements.size()])) {
                            removeRequirement(new RuntimeRequirementRegistration(req, capabilityName, context, rp), true);
                        }
                    }
                }
            }
        }
        return removed;
    }

    private synchronized void removeRequirement(RuntimeRequirementRegistration requirementRegistration, boolean optional) {
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
            }
        }
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
        final CapabilityId capabilityId = new CapabilityId(capability.getName(), CapabilityContext.GLOBAL);
        RegistrationPoint point = new RegistrationPoint(registrationPoint, null);
        CapabilityRegistration capabilityRegistration = new CapabilityRegistration<>(capability, CapabilityContext.GLOBAL, point);


        possibleCapabilities.computeIfPresent(capabilityId, (capabilityId1, currentRegistration) -> {
            RegistrationPoint rp = capabilityRegistration.getOldestRegistrationPoint();
            // The actual capability must be the same, and we must not already have a registration
            // from this resource
            if (!Objects.equals(capabilityRegistration.getCapability(), currentRegistration.getCapability())
                    || !currentRegistration.addRegistrationPoint(rp)) {
                throw ControllerLogger.MGMT_OP_LOGGER.capabilityAlreadyRegisteredInContext(capabilityId.getName(),
                        capabilityId.getContext().getName());
            }
            return capabilityRegistration;
        });
        possibleCapabilities.putIfAbsent(capabilityId, capabilityRegistration);
    }


    /**
     * Remove a previously registered capability if all registration points for it have been removed.
     *
     * @param capabilityName    the name of the capability. Cannot be {@code null}
     * @param context           the context in which the capability is registered. Cannot be {@code null}
     * @param registrationPoint the specific registration point that is being removed
     * @return the capability that was removed, or {@code null} if no matching capability was registered or other
     * registration points for the capability still exist
     */
    @Override
    public CapabilityRegistration removePossibleCapability(String capabilityName, CapabilityContext context, PathAddress registrationPoint) {
        return capabilities.remove(new CapabilityId(capabilityName, context));
    }


    private ImmutableCapabilityRegistry getPublishedRegistry() {
        return publishedCombinedRegistry;
    }

    //ImmutableCapabilityRegistry methods

    @Override
    public boolean hasCapability(String capabilityName, String dependentName, CapabilityContext context) {
        return getPublishedRegistry().hasCapability(capabilityName, dependentName, context);
    }

    @Override
    public <T> T getCapabilityRuntimeAPI(String capabilityName, CapabilityContext context, Class<T> apiType) {
        // Here we can't know the dependent name. So this can only be called when resolution is complete.
        assert resolutionContext.resolutionComplete;
        return getPublishedRegistry().getCapabilityRuntimeAPI(capabilityName, context, apiType);
    }

    @Override
    public Set<CapabilityRegistration> getCapabilities() {
        return getPublishedRegistry().getCapabilities();
    }

    @Override
    public Set<CapabilityRegistration> getPossibleCapabilities() {
        return getPublishedRegistry().getPossibleCapabilities();
    }

    @Override
    public ServiceName getCapabilityServiceName(String capabilityName, CapabilityContext context, Class<?> serviceType) {
        // Here we can't know the dependent name. So this can only be called when resolution is complete.
        assert resolutionContext.resolutionComplete;
        return getPublishedRegistry().getCapabilityServiceName(capabilityName, context, serviceType);
    }

    @Override
    public Set<RegistrationPoint> getPossibleProviderPoints(CapabilityId capabilityId) {
        return getPublishedRegistry().getPossibleProviderPoints(capabilityId);
    }

    /**
     * Publish the changes to main registry
     */
    synchronized void publish() {
        if (publishedFullRegistry == null) {
            throw new RuntimeException("Cannot write directly to main registry");
        }


        CapabilityRegistry published = publishedFullRegistry;

        published.capabilities.putAll(capabilities);
        published.possibleCapabilities.putAll(possibleCapabilities);
        published.requirements.putAll(requirements);
        published.runtimeOnlyRequirements.putAll(runtimeOnlyRequirements);
    }

    /**
     * Discard the changes.
     */
    synchronized void rollback() {
        capabilities.clear();
        possibleCapabilities.clear();
        requirements.clear();
        runtimeOnlyRequirements.clear();
    }


    /**
     * creates read only view of capabilities in registry
     */
    private ImmutableCapabilityRegistry createReadOnlyRegistry() {
        return new ImmutableCapabilityRegistryImpl(
                Collections.unmodifiableMap(capabilities),
                Collections.unmodifiableMap(possibleCapabilities),
                forServer,
                knownContexts != null ? Collections.unmodifiableSet(knownContexts) : null,
                resolutionContext);
    }

    private class CombinedImmutableCapabilityRegistry implements ImmutableCapabilityRegistry {

        private final ImmutableCapabilityRegistry current;
        private final ImmutableCapabilityRegistry published;

        CombinedImmutableCapabilityRegistry(CapabilityRegistry registry) {
            this.current = registry.readOnlyRegistry;
            this.published = registry.publishedFullRegistry.readOnlyRegistry;
        }

        @Override
        public boolean hasCapability(String capabilityName, String dependentName, CapabilityContext context) {
            boolean result = current.hasCapability(capabilityName, dependentName, context);
            if (!result) {
                result = published.hasCapability(capabilityName, dependentName, context);
            }
            return result;
        }

        @Override
        public <T> T getCapabilityRuntimeAPI(String capabilityName, CapabilityContext context, Class<T> apiType) {
            T result = current.getCapabilityRuntimeAPI(capabilityName, context, apiType);
            if (result == null) {
                result = published.getCapabilityRuntimeAPI(capabilityName, context, apiType);
            }
            return result;

        }

        @Override
        public Set<RegistrationPoint> getPossibleProviderPoints(CapabilityId capabilityId) {
            Set<RegistrationPoint> result = current.getPossibleProviderPoints(capabilityId);
            if (result == null) {
                result = published.getPossibleProviderPoints(capabilityId);
            }
            return result;
        }

        @Override
        public Set<CapabilityRegistration> getCapabilities() {
            Set<CapabilityRegistration> result = current.getCapabilities();
            if (result == null) {
                result = published.getCapabilities();
            }
            return result;
        }

        @Override
        public Set<CapabilityRegistration> getPossibleCapabilities() {
            Set<CapabilityRegistration> result = current.getPossibleCapabilities();
            if (result == null) {
                result = published.getPossibleCapabilities();
            }
            return result;
        }

        @Override
        public ServiceName getCapabilityServiceName(String capabilityName, CapabilityContext context, Class<?> serviceType) {
            ServiceName result = current.getCapabilityServiceName(capabilityName, context, serviceType);
            if (result == null) {
                result = published.getCapabilityServiceName(capabilityName, context, serviceType);
            }
            return result;
        }
    }

    synchronized CapabilityValidation resolveCapabilities(Resource rootResource, boolean hostXmlOnly) {

        resolutionContext.setRootResource(rootResource);

        Map<CapabilityId, Set<RuntimeRequirementRegistration>> missing = new HashMap<>();

        // Vars for tracking inconsistent contexts
        boolean isInconsistent = false;
        Map<CapabilityContext, Set<RuntimeRequirementRegistration>> requiresConsistency = null;
        Map<CapabilityContext, Set<CapabilityContext>> consistentSets = null;

        for (Map.Entry<CapabilityId, Map<String, RuntimeRequirementRegistration>> entry : requirements.entrySet()) {
            CapabilityId dependentId = entry.getKey();
            String dependentName = dependentId.getName();
            CapabilityContext dependentContext = dependentId.getContext();
            Set<CapabilityContext> consistentSet = consistentSets == null ? null : consistentSets.get(dependentContext);
            for (RuntimeRequirementRegistration req : entry.getValue().values()) {
                SatisfactoryCapability satisfactory = findSatisfactoryCapability(req.getRequiredName(), dependentContext, dependentName, !forServer);
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

                    CapabilityContext reqDependent = req.getDependentContext();
                    recordConsistentSets(requiresConsistency, consistentSets, reqDependent, consistentSet, req, satisfactory, reqDependent);
                    isInconsistent = isInconsistent || (consistentSet != null && consistentSet.size() == 0);

                    // Record for any contexts that include this one
                    for (CapabilityContext including : dependentContext.getIncludingContexts(resolutionContext)) {
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
    }

    private void recordConsistentSets(Map<CapabilityContext, Set<RuntimeRequirementRegistration>> requiresConsistency, Map<CapabilityContext, Set<CapabilityContext>> consistentSets, CapabilityContext dependentContext, Set<CapabilityContext> consistentSet, RuntimeRequirementRegistration req, SatisfactoryCapability satisfactory, CapabilityContext reqDependent) {
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

    private SatisfactoryCapability findSatisfactoryCapability(String capabilityName, CapabilityContext capabilityContext,
                                                              String dependentName, boolean requireConsistency) {

        // Check for a simple match
        CapabilityId requestedId = new CapabilityId(capabilityName, capabilityContext);
        if (capabilities.containsKey(requestedId)) {
            return new SatisfactoryCapability(requestedId);
        }

        if (!forServer) {
            // Try other contexts that satisfy the requested one
            Set<CapabilityContext> multiple = null;
            for (CapabilityContext satisfies : knownContexts) {
                if (satisfies.equals(capabilityContext)) {
                    // We already know this one doesn't exist
                    continue;
                }
                CapabilityId satisfiesId = new CapabilityId(capabilityName, satisfies);
                if (capabilities.containsKey(satisfiesId) && satisfies.canSatisfyRequirement(requestedId, dependentName, resolutionContext)) {
                    if (!requireConsistency || !satisfies.requiresConsistencyCheck()) {
                        return new SatisfactoryCapability(satisfiesId);
                    } else {
                        if (multiple == null) {
                            multiple = new HashSet<>();
                        }
                        multiple.add(satisfies);
                        multiple.addAll(satisfies.getIncludingContexts(resolutionContext));
                    }
                }
            }
            if (multiple != null) {
                return new SatisfactoryCapability(multiple);
            }
        }
        return null;
    }

    private static Set<RuntimeRequirementRegistration> findInconsistent(Map<CapabilityContext, Set<RuntimeRequirementRegistration>> requiresConsistency,
                                                                        Map<CapabilityContext, Set<CapabilityContext>> consistentSets) {
        Set<RuntimeRequirementRegistration> result = new HashSet<>();
        for (Map.Entry<CapabilityContext, Set<CapabilityContext>> entry : consistentSets.entrySet()) {
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
        private final CapabilityId singleCapability;
        private final Set<CapabilityContext> multipleCapabilities;

        SatisfactoryCapability(CapabilityId singleCapability) {
            this.singleCapability = singleCapability;
            this.multipleCapabilities = null;
        }

        SatisfactoryCapability(Set<CapabilityContext> multipleCapabilities) {
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

        public Map<CapabilityId, Set<RuntimeRequirementRegistration>> getMissingRequirements() {
            return missingRequirements;
        }

        public Set<RuntimeRequirementRegistration> getInconsistentRequirements() {
            return inconsistentRequirements;
        }

        public CapabilityResolutionContext getCapabilityResolutionContext() {
            return resolutionContext;
        }

        public boolean isValid() {
            return missingRequirements.isEmpty() && inconsistentRequirements.isEmpty();
        }
    }

}
