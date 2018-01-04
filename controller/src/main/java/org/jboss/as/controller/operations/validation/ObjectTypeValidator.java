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
