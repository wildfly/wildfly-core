/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

/**
 *
 * @author Alexey Loubyansky
 */
public interface CommandLineFormat {

    String getNodeSeparator();

    String getAddressOperationSeparator();

    String getPropertyListStart();

    String getPropertyListEnd();

    boolean isPropertySeparator(char ch);

    String getPropertySeparator();
}
