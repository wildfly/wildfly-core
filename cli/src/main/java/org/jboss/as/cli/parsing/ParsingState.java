/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

/**
 *
 * @author Alexey Loubyansky
 */
public interface ParsingState {

    String getId();

    CharacterHandler getEnterHandler();

    CharacterHandler getLeaveHandler();

    CharacterHandler getHandler(char ch);

    CharacterHandler getReturnHandler();

    CharacterHandler getEndContentHandler();

    /**
     * Whether the index of the value corresponding to this state
     * in the command line being parsed should be set to the index
     * when parsing enters this state.
     *
     * @return true if the index of the current value should be updated
     * when parsing enters this state, false - otherwise.
     */
    boolean updateValueIndex();

    /**
     * Whether the index of the current value being parsed should remain
     * the same until parsing leaves this state even if there are other
     * nested states that might want to update the value index
     * (i.e. states that return true from updateValueIndex).
     *
     * @return true if the value index should remain unchanged until
     * this state is left.
     */
    boolean lockValueIndex();
}
