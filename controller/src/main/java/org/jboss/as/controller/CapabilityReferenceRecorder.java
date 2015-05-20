/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

import org.jboss.as.controller.capability.RuntimeCapability;

/**
 * Records information about capability reference information encoded in an attribute's value.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public interface CapabilityReferenceRecorder {

    /**
     * Registers capability requirement information to the given context.
     * @param context the context
     * @param attributeName the name of the attribute
     * @param attributeValue the values of the attribute
     */
    void addCapabilityRequirements(OperationContext context, String attributeName, String... attributeValues);

    /**
     * Deregisters capability requirement information from the given context.
     * @param context the context
     * @param attributeName the name of the attribute
     * @param attributeValue the values of the attribute
     */
    void removeCapabilityRequirements(OperationContext context, String attributeName, String... attributeValues);

    /**
     * Default implementation of {@link org.jboss.as.controller.CapabilityReferenceRecorder}.
     * Derives the required capability name from the {@code baseRequirementName} provided to the constructor and from
     * the attribute value. Derives the dependent capability name from the {@code baseDependentName} provided to the
     * constructor, and, if the dependent name is dynamic, from the address of the resource currently being processed.
     */
    public static class DefaultCapabilityReferenceRecorder implements CapabilityReferenceRecorder {

        private final String baseRequirementName;
        private final String baseDependentName;
        private final boolean dynamicDependent;

        public DefaultCapabilityReferenceRecorder(String baseRequirementName, String baseDependentName, boolean dynamicDependent) {
            this.baseRequirementName = baseRequirementName;
            this.baseDependentName = baseDependentName;
            this.dynamicDependent = dynamicDependent;
        }

        @Override
        public final void addCapabilityRequirements(OperationContext context, String attributeName, String... attributeValues) {
            processCapabilityRequirement(context, attributeName, false, attributeValues);
        }

        @Override
        public final void removeCapabilityRequirements(OperationContext context, String attributeName, String... attributeValues) {
            processCapabilityRequirement(context, attributeName, true, attributeValues);
        }

        private void processCapabilityRequirement(OperationContext context, String attributeName, boolean remove, String... attributeValues) {
            String dependentName;
            if (dynamicDependent) {
                dependentName = RuntimeCapability.buildDynamicCapabilityName(baseDependentName, getDynamicDependentName(context.getCurrentAddress()));
            } else {
                dependentName = baseDependentName;
            }

            for (String attributeValue : attributeValues) {
                String requirementName = RuntimeCapability.buildDynamicCapabilityName(baseRequirementName, attributeValue);
                if (remove) {
                    context.registerAdditionalCapabilityRequirement(requirementName, dependentName, attributeName);
                } else {
                    context.deregisterCapabilityRequirement(requirementName, dependentName);
                }
            }
        }

        /**
         * Determines the dynamic portion of the dependent capability's name. Only invoked if {@code dynamicDependent}
         * is set to {@code true} in the constructor.
         * <p>
         * This base implementation returns the value of the last element in {@code currentAddress}. Subclasses that
         * wish to extract the relevant name from some other element in the address may override this.
         * </p>
         * @param currentAddress the address of the resource currently being processed. Will not be {@code null}
         * @return the dynamic portion of the dependenty capability name. Cannot be {@code null}
         */
        protected String getDynamicDependentName(PathAddress currentAddress) {
            return currentAddress.getLastElement().getValue();
        }
    }
}