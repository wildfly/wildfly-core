/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.validation;

import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;

import java.net.URI;
import java.net.URISyntaxException;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * A {@link ParameterValidator} to verify that a parameter is a correctly formed URI.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class URIValidator extends StringLengthValidator {

    public URIValidator(final boolean nullable, final boolean allowExpressions) {
        super(1, Integer.MAX_VALUE, nullable, allowExpressions);
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);

        String str = value.asString();

        try {
            new URI(str);
        } catch (URISyntaxException e) {
            throw ROOT_LOGGER.badUriSyntax(str);
        }
    }

}
