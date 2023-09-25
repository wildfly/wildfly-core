/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.parsing;

import org.jboss.as.cli.CommandFormatException;

/**
 * @author Alexey Loubyansky
 *
 */
public class ExpressionBaseState extends DefaultParsingState {

    private final boolean resolveSystemProperties;
    private final boolean exceptionIfNotResolved;

    private CharacterHandler resolvingEntranceHandler = new CharacterHandler() {
        @Override
        public void handle(ParsingContext ctx) throws CommandFormatException {
            ctx.resolveExpression(resolveSystemProperties, exceptionIfNotResolved);
            ExpressionBaseState.super.getEnterHandler().handle(ctx);
        }};

    public ExpressionBaseState(String id) {
        this(id, true);
    }

    public ExpressionBaseState(String id, boolean enterLeaveContent, CharacterHandlerMap enterStateHandlers) {
        this(id, enterLeaveContent, enterStateHandlers, true);
    }

    public ExpressionBaseState(String id, boolean enterLeaveContent, CharacterHandlerMap enterStateHandlers, boolean resolveSystemProperties) {
        super(id, enterLeaveContent, enterStateHandlers);
        this.resolveSystemProperties = resolveSystemProperties;
        this.exceptionIfNotResolved = true;
        putExpressionHandler();
    }

    public ExpressionBaseState(String id, boolean resolveSystemProperties) {
        this(id, resolveSystemProperties, true);
    }

    public ExpressionBaseState(String id, boolean resolveSystemProperties, boolean exceptionIfNotResolved) {
        super(id);
        this.resolveSystemProperties = resolveSystemProperties;
        this.exceptionIfNotResolved = exceptionIfNotResolved;
        putExpressionHandler();
    }

    public ExpressionBaseState(String id, boolean resolveSystemProperties, boolean exceptionIfNotResolved, boolean enterLeaveContent) {
        super(id, enterLeaveContent);
        this.resolveSystemProperties = resolveSystemProperties;
        this.exceptionIfNotResolved = exceptionIfNotResolved;
        putExpressionHandler();
    }

    protected void putExpressionHandler() {
        this.putHandler('$', new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx)
                    throws CommandFormatException {
                final int originalLength = ctx.getInput().length();
                ctx.resolveExpression(resolveSystemProperties, exceptionIfNotResolved);
                final char resolvedCh = ctx.getCharacter();
                if(resolvedCh == '$' && originalLength == ctx.getInput().length()) {
                    getDefaultHandler().handle(ctx);
                } else {
                    getHandler(resolvedCh).handle(ctx);
                }
            }});
    }

    @Override
    public CharacterHandler getEnterHandler() {
        return resolvingEntranceHandler;
    }
}
