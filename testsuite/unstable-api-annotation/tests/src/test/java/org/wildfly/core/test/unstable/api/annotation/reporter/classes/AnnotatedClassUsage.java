/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.unstable.api.annotation.reporter.classes;

import org.wildfly.core.test.unstable.api.annotation.classes.api.TestClassWithAnnotationForUsage;
import org.wildfly.core.test.unstable.api.annotation.classes.api.Unstable;

@Unstable
public class AnnotatedClassUsage {

    public void test(TestClassWithAnnotationForUsage clazz) {
        clazz.methodWithNoAnnotation();
    }
}
