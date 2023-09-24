/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.descriptions;

import java.util.Locale;
import java.util.Map;

import org.jboss.dmr.ModelNode;

/**
 * Provides description elements to override the description of a resource produced by a {@link DescriptionProvider}.
 * For use with specifically named resources (i.e. those whose {@link org.jboss.as.controller.registry.ManagementResourceRegistration} path is identified
 * with a {@link org.jboss.as.controller.PathElement#pathElement(String, String) two-argument PathElement}) that expose additional attributes or
 * operations not provided by the generic resource description (i.e. the {@link org.jboss.as.controller.registry.ManagementResourceRegistration} whose
 * path is identified with a {@link org.jboss.as.controller.PathElement#pathElement(String) one-argument PathElement}.)
 *
 * @see org.jboss.as.controller.registry.ManagementResourceRegistration#registerOverrideModel(String, OverrideDescriptionProvider)
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface OverrideDescriptionProvider {

    /**
     * Provides descriptions for attributes that are in addition to those provided by the generic resource.
     *
     * @param locale locale to use for generating internationalized descriptions
     *
     * @return map whose keys are attribute names and whose values are the descriptions of the attribute to
     *         incorporate into the overall resource description.
     */
    Map<String, ModelNode> getAttributeOverrideDescriptions(Locale locale);

    /**
     * Provides descriptions for child types that are in addition to those provided by the generic resource.
     *
     * @param locale locale to use for generating internationalized descriptions
     *
     * @return map whose keys are child type names and whose values are the descriptions of the child type to
     *         incorporate into the overall resource description.
     */
    Map<String, ModelNode> getChildTypeOverrideDescriptions(Locale locale);
}
