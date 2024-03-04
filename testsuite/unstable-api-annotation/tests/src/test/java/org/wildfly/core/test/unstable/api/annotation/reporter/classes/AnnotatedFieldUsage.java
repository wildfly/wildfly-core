/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.unstable.api.annotation.reporter.classes;

import org.wildfly.core.test.unstable.api.annotation.classes.api.TestClassWithAnnotatedField;

public class AnnotatedFieldUsage {

    public void test() {
        TestClassWithAnnotatedField.annotatedField = 10;
    }
}
