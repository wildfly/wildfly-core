/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.testrunner;

import org.junit.runners.model.InitializationError;

/**
 * A lightweight test runner for running management based tests
 * @deprecated Use {@link WildFlyRunner} instead
 * @author Stuart Douglas
 */
@Deprecated
public class WildflyTestRunner extends WildFlyRunner {

    /**
     * Creates a BlockJUnit4ClassRunner to run {@code klass}
     *
     * @param klass
     * @throws InitializationError if the test class is malformed.
     */
    public WildflyTestRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }
}
