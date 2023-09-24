/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.validators;

import org.jboss.as.controller.operations.validation.StringLengthValidator;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Validators {

    public static final StringLengthValidator NOT_EMPTY_NULLABLE_STRING_VALIDATOR = new StringLengthValidator(1, true, true);
}
