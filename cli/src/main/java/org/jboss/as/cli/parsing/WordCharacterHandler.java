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
public class WordCharacterHandler extends LineBreakHandler {

    public static final WordCharacterHandler IGNORE_LB_ESCAPE_ON = new WordCharacterHandler(false, true);
    public static final WordCharacterHandler IGNORE_LB_ESCAPE_OFF = new WordCharacterHandler(false, false);
    public static final WordCharacterHandler LB_LEAVE_ESCAPE_ON = new WordCharacterHandler(true, true);

    public WordCharacterHandler(boolean leaveOnLnBreak, boolean fallbackToEscape) {
        super(leaveOnLnBreak, fallbackToEscape);
    }

    @Override
    public void doHandle(ParsingContext ctx) throws CommandFormatException {
        //System.out.println("word: '" + ctx.getCharacter() + "'");
        ctx.getCallbackHandler().character(ctx);
    }
}