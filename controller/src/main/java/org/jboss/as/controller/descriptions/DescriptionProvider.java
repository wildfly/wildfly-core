/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.descriptions;

import java.util.Locale;

import org.jboss.dmr.ModelNode;

/**
 * Provides information (description, list of attributes, list of children)
 * describing the structure of an addressable model node or operation.
 *
 * @author Brian Stansberry
 */
public interface DescriptionProvider {

    /**
     * Gets the descriptive information (human-friendly description, list of attributes,
     * list of children) describing a single model node or operation.
     * <p>
     * The implementation must assume that the caller intends to modify the
     * returned {@code ModelNode} so it should not hand out a reference to any internal data structures.
     *
     * @param locale the locale to use to generate any localized text used in the description.
     *               May be {@code null}, in which case {@link Locale#getDefault()} should be used
     *
     * @return {@link ModelNode} describing the model node's structure
     */
    ModelNode getModelDescription(Locale locale);
}
