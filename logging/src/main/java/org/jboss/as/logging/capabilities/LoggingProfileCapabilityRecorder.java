/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.logging.capabilities;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.BiFunction;

import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.logging.CommonAttributes;

/**
 * Allows the name of the logging profile to be appended to the capability name.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggingProfileCapabilityRecorder implements CapabilityReferenceRecorder {

    private final String dependentName;
    private final String requirementName;
    private final BiFunction<String[], String, Collection<String>> valueConverter;

    LoggingProfileCapabilityRecorder(final String dependentName, final String requirementName) {
        this(dependentName, requirementName, (values, attributeName) -> Arrays.asList(values));
    }

    LoggingProfileCapabilityRecorder(final String dependentName, final String requirementName, final BiFunction<String[], String, Collection<String>> valueConverter) {
        this.dependentName = dependentName;
        this.requirementName = requirementName;
        this.valueConverter = valueConverter;
    }

    @Override
    public void addCapabilityRequirements(final OperationContext context, final Resource resource, final String attributeName, final String... attributeValues) {
        final Collection<String> values = valueConverter.apply(attributeValues, attributeName);
        String dependentName = getDependentName(context);
        for (String value : values) {
            if (value != null) {
                context.registerAdditionalCapabilityRequirement(getRequirementName(context, value), dependentName, attributeName);
            }
        }
    }

    @Override
    public void removeCapabilityRequirements(final OperationContext context, final Resource resource, final String attributeName, final String... attributeValues) {
        final Collection<String> values = valueConverter.apply(attributeValues, attributeName);
        String dependentName = getDependentName(context);
        for (String value : values) {
            if (value != null) {
                context.deregisterCapabilityRequirement(getRequirementName(context, value), dependentName, attributeName);
            }
        }
    }

    @Override
    public String getBaseDependentName() {
        return dependentName;
    }

    @Override
    public String getBaseRequirementName() {
        return requirementName;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isDynamicDependent() {
        return true;
    }

    @Override
    public String[] getRequirementPatternSegments(final String name, final PathAddress address) {
        // Find the logging profile if it exists and add the profile name to the capability name
        for (PathElement pathElement : address) {
            if (CommonAttributes.LOGGING_PROFILE.equals(pathElement.getKey())) {
                return new String[] {pathElement.getValue(), name};
            }
        }
        return new String[] {name};
    }

    private String getDependentName(final OperationContext context) {
        final StringBuilder result = new StringBuilder(getBaseDependentName());
        for (String name : getRequirementPatternSegments(context.getCurrentAddressValue(), context.getCurrentAddress())) {
            result.append('.').append(name);
        }
        return result.toString();
    }

    private String getRequirementName(final OperationContext context, final String value) {
        final StringBuilder result = new StringBuilder(getBaseRequirementName());
        for (String name : getRequirementPatternSegments(value, context.getCurrentAddress())) {
            result.append('.').append(name);
        }
        return result.toString();
    }
}
