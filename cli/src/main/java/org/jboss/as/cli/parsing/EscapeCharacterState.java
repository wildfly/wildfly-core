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
public final class EscapeCharacterState extends BaseParsingState {

    public static final String ID = "ESCAPED_CHARACTER";

/*    private static final CharacterHandler EOC = GlobalCharacterHandlers.newErrorCharacterHandler(
    "Error parsing escaped character: the character after '\' is missing.");
*/

    public static final EscapeCharacterState INSTANCE = new EscapeCharacterState(false);
    /**
     * This one is useful when the escaping should be recognized but postponed
     * (for characters that otherwise would have affected the parsing flow, such as '"').
     */
    public static final EscapeCharacterState KEEP_ESCAPE = new EscapeCharacterState(true);

    private final CharacterHandler handler = new CharacterHandler() {
        @Override
        public void handle(ParsingContext ctx)
                throws CommandFormatException {
            if(!keepEscape) {
                ctx.replaceSpecialChars();
            }
            ctx.getCallbackHandler().character(ctx);
            ctx.leaveState();
        }
    };

    private final boolean keepEscape;

    EscapeCharacterState() {
        this(false);
    }

    EscapeCharacterState(boolean keepEscape) {
        super(ID);
        this.keepEscape = keepEscape;
        setEnterHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(EscapeCharacterState.this.keepEscape ||
                        // not sure about this: if the input ends on '\' it's added to the content
                        ctx.getInput().length() - ctx.getLocation() == 1) {
                    ctx.getCallbackHandler().character(ctx);
                }
            }});
    }

    @Override
    public CharacterHandler getHandler(char ch) {
        return handler;
    }
}