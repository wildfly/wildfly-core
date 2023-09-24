/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;

/**
 *
 * @author Alexey Loubyansky
 */
public class LineBreakHandler implements CharacterHandler {

    private final Boolean fallbackToEscape;
    private final boolean leaveOnLnBreak;

    public LineBreakHandler(boolean leaveOnLnBreak) {
        this.leaveOnLnBreak = leaveOnLnBreak;
        this.fallbackToEscape = null;
    }

    public LineBreakHandler(boolean leaveOnLnBreak, boolean fallbackToEscape) {
        this.leaveOnLnBreak = leaveOnLnBreak;
        this.fallbackToEscape = fallbackToEscape;
    }

    @Override
    public void handle(ParsingContext ctx) throws CommandFormatException {
        if(ctx.getCharacter() == '\\') {
            if(ctx.getInput().length() > ctx.getLocation() + Util.LINE_SEPARATOR.length() &&
                    ctx.getInput().startsWith(Util.LINE_SEPARATOR, ctx.getLocation() + 1)) {
                if(leaveOnLnBreak) {
                    ctx.leaveState();
                    ctx.advanceLocation(Util.LINE_SEPARATOR.length());
                }
                return;
            }

            if(fallbackToEscape == null) {
                // the escape will be handled in doHandle() impl
                doHandle(ctx);
                return;
            }

            if(fallbackToEscape){
                ctx.enterState(EscapeCharacterState.INSTANCE);
            } else {
                ctx.enterState(EscapeCharacterState.KEEP_ESCAPE);
            }
        } else {
            doHandle(ctx);
        }
    }

    protected void doHandle(ParsingContext ctx) throws CommandFormatException {}
}
