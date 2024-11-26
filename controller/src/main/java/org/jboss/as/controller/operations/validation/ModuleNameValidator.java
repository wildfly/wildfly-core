/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Validates that a given parameter is a syntactically valid module name within JBoss Modules.
 * N.B. This does not validate that the module actually exists, i.e. can be loaded.
 */
public class ModuleNameValidator extends ModelTypeValidator {
    public static final ParameterValidator INSTANCE = new ModuleNameValidator();
    // Ensure module name is valid with filesystem module repository, permitting deprecated slot, if present
    private static final Predicate<String> MODULE_NAME_TESTER = Pattern.compile("(?:^\\w+|\\w+\\.\\w+|\\w+\\Q\\:\\E\\w+)+(?:\\:(?:\\w+|\\w+\\.\\w+))?$").asMatchPredicate();

    private ModuleNameValidator() {
        super(ModelType.STRING);
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            String moduleName = value.asString();
            if (!MODULE_NAME_TESTER.test(moduleName)) {
                throw ControllerLogger.MGMT_OP_LOGGER.invalidModuleNameParameter(parameterName, moduleName);
            }
        }
    }
}
