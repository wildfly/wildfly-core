/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

/**
 *
 * @author Alexey Loubyansky
 */
public interface EscapeSelector {

    boolean isEscape(char ch);
}
