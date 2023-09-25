/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.command.ArgumentValueNotFinishedException;

/**
 *
 * @author Alexey Loubyansky
 */
public class DefaultStateWithEndCharacter extends ExpressionBaseState {

    public static class Builder {
        private String id;
        private char leaveStateChar;
        private boolean endRequired;
        private boolean enterLeaveContent;
        private CharacterHandlerMap enterStateHandlers;
        private boolean resolveSystemProperties = true;

        private Builder(String id) {
            this.id = id;
        }

        public Builder setLeaveStateChar(char leaveStateChar) {
            this.leaveStateChar = leaveStateChar;
            return this;
        }

        public Builder setEndRequired(boolean endRequired) {
            this.endRequired = endRequired;
            return this;
        }

        public Builder setEnterLeaveContent(boolean enterLeaveContent) {
            this.enterLeaveContent = enterLeaveContent;
            return this;
        }

        public Builder setEnterStateHandlers(CharacterHandlerMap enterStateHandlers) {
            this.enterStateHandlers = enterStateHandlers;
            return this;
        }

        public Builder setResolveSystemProperties(boolean resolveSystemProperties) {
            this.resolveSystemProperties = resolveSystemProperties;
            return this;
        }

        public DefaultStateWithEndCharacter build() {
            return new DefaultStateWithEndCharacter(id, leaveStateChar, endRequired, enterLeaveContent, enterStateHandlers, resolveSystemProperties);
        }
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    private final char leaveStateChar;

    DefaultStateWithEndCharacter(String id, char leaveStateChar) {
        this(id, leaveStateChar, true);
    }

    DefaultStateWithEndCharacter(String id, char leaveStateChar, boolean endRequired) {
        this(id, leaveStateChar, endRequired, false);
    }

    public DefaultStateWithEndCharacter(String id, char leaveStateChar, boolean endRequired, boolean enterLeaveContent) {
        this(id, leaveStateChar, endRequired, enterLeaveContent, new DefaultCharacterHandlerMap());
    }

    public DefaultStateWithEndCharacter(String id, final char leaveStateChar, boolean endRequired, boolean enterLeaveContent, CharacterHandlerMap enterStateHandlers) {
        this(id, leaveStateChar, endRequired, enterLeaveContent, enterStateHandlers, true);
    }

    private DefaultStateWithEndCharacter(String id, final char leaveStateChar, boolean endRequired, boolean enterLeaveContent, CharacterHandlerMap enterStateHandlers,
            boolean resolveSystemProperties) {
        super(id, enterLeaveContent, enterStateHandlers, resolveSystemProperties);
        this.leaveStateChar = leaveStateChar;
        if(enterLeaveContent) {
            setLeaveHandler(new CharacterHandler() {
                @Override
                public void handle(ParsingContext ctx) throws CommandFormatException {
                    // End of content, do not mix character from
                    // previous state with this state.
                    // eg: a={x={y=c}
                    // x is complete, a is not, do not 're-use; x closing '}' for a.
                    if (ctx.getCharacter() == leaveStateChar && !ctx.isEndOfContent()) {
                        GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER.handle(ctx);
                    }
                }});
        }
        if(endRequired) {
           this.setEndContentHandler(new ErrorCharacterHandler(("Closing '" + leaveStateChar + "' is missing.")));
        } else {
            this.setEndContentHandler(new CharacterHandler() {
                @Override
                public void handle(ParsingContext ctx) throws CommandFormatException {
                    // only set the error, but don't throw it even if the strict parsing is enabled.
                    // the command is still valid, but the error is needed to correctly treat a trailing space in
                    // argument value (WFCORE-1572)
                    ctx.setError(new ArgumentValueNotFinishedException("Closing '" + leaveStateChar + "' is missing"));
                }
            });
        }
        this.setDefaultHandler(GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER);
    }

    @Override
    public CharacterHandler getHandler(char ch) {
        return leaveStateChar == ch ? GlobalCharacterHandlers.LEAVE_STATE_HANDLER : super.getHandler(ch);
    }
}
