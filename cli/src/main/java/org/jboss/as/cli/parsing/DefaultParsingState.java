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
public class DefaultParsingState extends BaseParsingState {

    protected final CharacterHandlerMap enterStateHandlers;
    private final CharacterHandlerMap handlers;
    private boolean ignoreWhitespaces;
    private boolean leaveOnWhitespace;
    private CharacterHandler wsHandler;

    private CharacterHandler defaultHandler = GlobalCharacterHandlers.NOOP_CHARACTER_HANDLER;

    public DefaultParsingState(String id) {
        this(id, false);
    }

    public DefaultParsingState(String id, boolean enterLeaveContent) {
        this(id, enterLeaveContent, new DefaultCharacterHandlerMap());
    }

    public DefaultParsingState(String id, boolean enterLeaveContent, CharacterHandlerMap enterStateHandlers) {
        super(id);

        if(enterLeaveContent) {
            setEnterHandler(GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER);
            setLeaveHandler(new CharacterHandler() {
                @Override
                public void handle(ParsingContext ctx) throws CommandFormatException {
                    if(!ctx.isEndOfContent()) {
                        ctx.getCallbackHandler().character(ctx);
                    }
                }});
        }

        this.handlers = new DefaultCharacterHandlerMap();
        this.enterStateHandlers = enterStateHandlers;
    }

    public void setIgnoreWhitespaces(boolean ignoreWhitespaces) {
        this.ignoreWhitespaces = ignoreWhitespaces;
    }

    public boolean isIgnoreWhitespaces() {
        return this.ignoreWhitespaces;
    }

    public void setLeaveOnWhitespace(boolean leaveOnWhitespace) {
        this.leaveOnWhitespace = leaveOnWhitespace;
    }

    public boolean isLeaveOnWhitespace() {
        return this.leaveOnWhitespace;
    }

    public void setDefaultHandler(CharacterHandler handler) {
        this.defaultHandler = checkNotNullParam("handler", handler);
    }

    public CharacterHandler getDefaultHandler() {
        return this.defaultHandler;
    }

    public void putHandler(char ch, CharacterHandler handler) {
        handlers.putHandler(ch, handler);
    }

    public void enterState(char ch, ParsingState state) {
        enterStateHandlers.putHandler(ch, new EnterStateCharacterHandler(state));
    }

    public void leaveState(char ch) {
        enterStateHandlers.putHandler(ch, GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
    }

    public void setHandleEntrance(boolean handleEntrance) {
        if (handleEntrance) {
            setEnterHandler(new CharacterHandler() {
                @Override
                public void handle(ParsingContext ctx) throws CommandFormatException {
                    getHandler(ctx.getCharacter()).handle(ctx);
                }
            });
        } else {
            setEnterHandler(GlobalCharacterHandlers.NOOP_CHARACTER_HANDLER);
        }
    }

    public void setWhitespaceHandler(CharacterHandler handler) {
        wsHandler = handler;
    }

    public CharacterHandler getWhitespaceHandler() {
        return wsHandler;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.operation.parsing.ParsingState#getHandler(char)
     */
    @Override
    public CharacterHandler getHandler(char ch) {
        if(wsHandler != null && Character.isWhitespace(ch)) {
            return wsHandler;
        }

        if(ignoreWhitespaces && Character.isWhitespace(ch)) {
            return GlobalCharacterHandlers.NOOP_CHARACTER_HANDLER;
        }

        if(leaveOnWhitespace && Character.isWhitespace(ch)) {
            return GlobalCharacterHandlers.LEAVE_STATE_HANDLER;
        }

        CharacterHandler handler = enterStateHandlers.getHandler(ch);
        if(handler != null) {
            return handler;
        }
        handler = handlers.getHandler(ch);
        if(handler != null) {
            return handler;
        }
        return defaultHandler;
    }
}
