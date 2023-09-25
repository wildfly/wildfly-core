/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.util.List;

import org.jboss.dmr.ModelNode;

/**
 * A validator that requires that values match one of the items in a defined list.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@FunctionalInterface
public interface AllowedValuesValidator {

    /**
     * Gets the allowed values, or {@code null} if any value is allowed.
     *
     * @return the allowed values, or {@code null}
     */
    List<ModelNode> getAllowedValues();
}
