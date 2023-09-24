/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
* Date: 16.11.2011
*
* @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
*/
public class ObjectTypeValidator extends ModelTypeValidator {

    private final Map<String, AttributeDefinition> allowedValues;

    public ObjectTypeValidator(final boolean nullable, final AttributeDefinition... attributes) {
        super(nullable, true, false, ModelType.OBJECT);
        allowedValues = new HashMap<String, AttributeDefinition>(attributes.length);
        for (AttributeDefinition attribute : attributes) {
            allowedValues.put(attribute.getName(), attribute);
        }
    }

    @Override
    public void validateParameter(final String parameterName, final ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            for (AttributeDefinition ad : allowedValues.values()) {
                String key = ad.getName();
                // Don't modify the value by calls to get(), because that's best in general.
                // Plus modifying it results in an irrelevant test failure in full where the test
                // isn't expecting the modification and complains.
                // Changing the test is too much trouble.
                ModelNode toTest = value.has(key) ? value.get(key) : new ModelNode();
                ad.getValidator().validateParameter(key, toTest);
            }
        }
    }
}
