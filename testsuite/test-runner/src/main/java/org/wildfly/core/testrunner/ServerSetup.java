/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.testrunner;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that can be applied to an test to allow for server setup to be performed
 * before the deployment is performed.
 *
 * This will be run before the first deployment is performed for each server.
 *
 * @author Stuart Douglas
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ServerSetup {

    Class<? extends ServerSetupTask>[] value();

}
