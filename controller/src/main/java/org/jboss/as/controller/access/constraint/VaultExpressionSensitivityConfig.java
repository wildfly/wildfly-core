/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

/**
 * Allows configuration as to whether reads and writes of data involving vault expressions
 * are to be considered sensitive.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class VaultExpressionSensitivityConfig extends AbstractSensitivity {

    public static final VaultExpressionSensitivityConfig INSTANCE = new VaultExpressionSensitivityConfig();

    private VaultExpressionSensitivityConfig() {
        super(false, true, true);
    }
}
