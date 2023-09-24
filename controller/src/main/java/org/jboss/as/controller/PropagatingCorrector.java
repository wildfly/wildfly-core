/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import java.util.Set;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * This implementation propagates properties present in the current
 * attribute value but missing from the new value.
 * Although, if the new value is of type UNDEFINED, the value
 * will remain UNDEFINED.
 *
 * @author Alexey Loubyansky
 */
public class PropagatingCorrector implements ParameterCorrector {

    public static final PropagatingCorrector INSTANCE = new PropagatingCorrector();

    /* (non-Javadoc)
     * @see org.jboss.as.controller.AttributeValueCorrector#correct(org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)
     */
    @Override
    public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
        if(newValue.getType() == ModelType.UNDEFINED) {
            return newValue;
        }
        if(newValue.getType() != ModelType.OBJECT || currentValue.getType() != ModelType.OBJECT) {
            return newValue;
        }
        final Set<String> operationKeys = newValue.keys();
        final Set<String> currentKeys = currentValue.keys();
        for(String currentKey : currentKeys) {
            if(!operationKeys.contains(currentKey)) {
                newValue.get(currentKey).set(currentValue.get(currentKey));
            }
        }
        return newValue;
    }
}
