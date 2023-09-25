/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.validators;

import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH;
import static org.jboss.as.logging.CommonAttributes.RELATIVE_TO;
import static org.jboss.as.logging.Logging.createOperationFailure;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.services.path.AbstractPathService;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Validates the {@link org.jboss.as.logging.CommonAttributes#FILE file} attribute.
 * <p/>
 * A valid {@link org.jboss.as.logging.CommonAttributes#FILE file} attribute must have an absolute
 * {@link org.jboss.as.controller.services.path.PathResourceDefinition#PATH path} attribute or a
 * {@link org.jboss.as.controller.services.path.PathResourceDefinition#PATH path} attribute with a valid
 * {@link org.jboss.as.controller.services.path.PathResourceDefinition#RELATIVE_TO relative-to} attribute.
 * <p/>
 * Date: 28.11.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class FileValidator extends ModelTypeValidator {

    public FileValidator() {
        super(ModelType.OBJECT);
    }

    @Override
    public void validateParameter(final String parameterName, final ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        final ModelNode clone = value.clone();
        RELATIVE_TO.getValidator().validateParameter(parameterName, clone.get(RELATIVE_TO.getName()));
        PATH.getValidator().validateParameter(parameterName, clone.get(PATH.getName()));
        if (value.isDefined()) {
            // Could have relative-to
            if (value.hasDefined(RELATIVE_TO.getName())) {
                final String relativeTo = value.get(RELATIVE_TO.getName()).asString();
                // Can't be an absolute path
                if (AbstractPathService.isAbsoluteUnixOrWindowsPath(relativeTo)) {
                    throw createOperationFailure(LoggingLogger.ROOT_LOGGER.invalidRelativeTo(relativeTo));
                }
            }
        }
    }
}
