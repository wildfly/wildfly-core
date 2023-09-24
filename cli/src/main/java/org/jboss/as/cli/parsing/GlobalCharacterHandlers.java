/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

import java.util.HashMap;
import java.util.Map;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.operation.OperationFormatException;

/**
 *
 * @author Alexey Loubyansky
 */
public class GlobalCharacterHandlers {

    private static final Map<Character, CharacterHandler> handlers = new HashMap<Character, CharacterHandler>();

    static final CharacterHandlerMap GLOBAL_ENTER_STATE_HANDLERS = new CharacterHandlerMap() {
        @Override
        public CharacterHandler getHandler(char ch) {
            return GlobalCharacterHandlers.getHandler(ch, null);
        }

        @Override
        public void putHandler(char ch, CharacterHandler handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeHandler(char ch) {
            throw new UnsupportedOperationException();
        }};

    public static final CharacterHandler NOOP_CHARACTER_HANDLER = new CharacterHandler(){
        @Override
        public void handle(ParsingContext ctx)
                throws OperationFormatException {
        }
        public String toString() {
            return "NOOPHANDLER";
        }
    };

    public static final CharacterHandler CONTENT_CHARACTER_HANDLER = new CharacterHandler() {

        @Override
        public void handle(ParsingContext ctx)
                throws CommandFormatException {
            ctx.getCallbackHandler().character(ctx);
        }
    };

    public static final CharacterHandler LEAVE_STATE_HANDLER = new CharacterHandler() {

        @Override
        public void handle(ParsingContext ctx)
                throws CommandFormatException {
            ctx.leaveState();
        }
    };

    static CharacterHandler getHandler(char ch, CharacterHandler defaultHandler) {
        CharacterHandler handler = handlers.get(ch);
        if(handler == null) {
            return defaultHandler;
        }
        return handler;
    }

    static CharacterHandler getHandler(char ch) {
        return getHandler(ch, CONTENT_CHARACTER_HANDLER);
    }

    private static void addHandler(String id, char start, char end) {
        addHandler(start, new DefaultStateWithEndCharacter(id, end, true, false, GLOBAL_ENTER_STATE_HANDLERS));
    }

    private static void addHandler(char start, ParsingState state) {
        handlers.put(start, new EnterStateCharacterHandler(state));
    }

    //private static void addHandler(char start, )
    static {
        addHandler("STRING_IN_PARENTHESIS", '(', ')');
        addHandler("STRING_IN_BRACKETS", '[', ']');
        addHandler("STRING_IN_BRACES", '{', '}');
        //addHandler("STRING_IN_CHEVRONS", '<', '>', "The closing '>' is missing.");
        addHandler('\\', EscapeCharacterState.INSTANCE);
        addHandler('"', QuotesState.QUOTES_EXCLUDED);
    }
}
