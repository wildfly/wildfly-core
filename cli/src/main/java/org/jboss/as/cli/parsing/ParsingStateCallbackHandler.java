/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

import org.jboss.as.cli.CommandFormatException;

/**
 *
 * @author Alexey Loubyansky
 */
public interface ParsingStateCallbackHandler {

    void enteredState(ParsingContext ctx)throws CommandFormatException;

    void leavingState(ParsingContext ctx) throws CommandFormatException;

    void character(ParsingContext ctx) throws CommandFormatException;
}
