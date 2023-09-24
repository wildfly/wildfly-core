/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import java.net.URI;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.discovery.ServiceURL;

/**
 * Validates the URI of a ServiceURL.
 * @author Paul Ferraro
 */
public class ServiceURIValidator extends ModelTypeValidator {

    public ServiceURIValidator() {
        super(ModelType.STRING);
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.getType() == ModelType.STRING) {
            try {
                // Ensure this is a valid URI
                URI uri = URI.create(value.asString());
                // Ensure the URI is a valid for use in a ServiceURL
                new ServiceURL.Builder().setUri(uri);
            } catch (IllegalArgumentException e) {
                throw new OperationFailedException(e);
            }
        }
    }
}
