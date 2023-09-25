/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Classes and resources for a trivial application deployment that does not require any
 * subsystems to be installed in order to affect the server runtime, plus utility
 * code related to using said deployment in a test.
 * <p>
 * The intent is the deployment can be used in tests of core deployment behavior without
 * introducing the need for the relevant testsuite to install an extension/subsystem.
 */
package org.wildfly.test.jmx;
