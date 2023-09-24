/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.testrunner;

import org.junit.runners.model.FrameworkField;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ParameterDescription {
    final FrameworkField field;
    final Object value;

    ParameterDescription(final FrameworkField field, final Object value) {
        this.field = field;
        this.value = value;
    }
}
