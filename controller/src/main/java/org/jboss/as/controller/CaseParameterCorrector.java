/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Locale;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Parameter correctors that can be used to change the case of a {@link ModelNode model node} that is of {@link
 * ModelType#STRING type string}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CaseParameterCorrector {

    /**
     * Converts the string value of the {@code newValue} into uppercase only if the value is not already in uppercase.
     */
    public static final ParameterCorrector TO_UPPER = new ParameterCorrector() {
        @Override
        public ModelNode correct(final ModelNode newValue, final ModelNode currentValue) {
            if (newValue.getType() == ModelType.UNDEFINED) {
                return newValue;
            }
            if (newValue.getType() != ModelType.STRING || currentValue.getType() != ModelType.STRING) {
                return newValue;
            }
            final String stringValue = newValue.asString();
            final String uCase = stringValue.toUpperCase(Locale.ENGLISH);
            if (!stringValue.equals(uCase)) {
                newValue.set(uCase);
            }
            return newValue;
        }
    };

    /**
     * Converts the string value of the {@code newValue} into lowercase only if the value is not already in lowercase.
     */
    public static final ParameterCorrector TO_LOWER = new ParameterCorrector() {
        @Override
        public ModelNode correct(final ModelNode newValue, final ModelNode currentValue) {
            if (newValue.getType() == ModelType.UNDEFINED) {
                return newValue;
            }
            if (newValue.getType() != ModelType.STRING || currentValue.getType() != ModelType.STRING) {
                return newValue;
            }
            final String stringValue = newValue.asString();
            final String uCase = stringValue.toLowerCase(Locale.ENGLISH);
            if (!stringValue.equals(uCase)) {
                newValue.set(uCase);
            }
            return newValue;
        }
    };
}
