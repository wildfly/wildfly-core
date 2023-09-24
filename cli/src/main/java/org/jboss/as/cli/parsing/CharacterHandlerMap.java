/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

/**
 *
 * @author Alexey Loubyansky
 */
public interface CharacterHandlerMap {

    CharacterHandler getHandler(char ch);

    void putHandler(char ch, CharacterHandler handler);

    void removeHandler(char ch);
}
