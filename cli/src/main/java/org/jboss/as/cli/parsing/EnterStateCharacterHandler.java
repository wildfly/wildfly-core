/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

import static org.wildfly.common.Assert.checkNotNullParam;

import org.jboss.as.cli.CommandFormatException;

/**
*
* @author Alexey Loubyansky
*/
public class EnterStateCharacterHandler implements CharacterHandler {

    protected final ParsingState state;

    public EnterStateCharacterHandler(ParsingState state) {
        this.state = checkNotNullParam("state", state);
    }

    @Override
    public void handle(ParsingContext ctx)
            throws CommandFormatException {
        ctx.enterState(state);
    }
}