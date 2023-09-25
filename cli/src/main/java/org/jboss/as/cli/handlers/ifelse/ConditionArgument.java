/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.handlers.ifelse;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.handlers.CommandHandlerWithArguments;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.DefaultStateWithEndCharacter;
import org.jboss.as.cli.parsing.EscapeCharacterState;
import org.jboss.as.cli.parsing.ExpressionBaseState;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.ParsingState;
import org.jboss.as.cli.parsing.ParsingStateCallbackHandler;
import org.jboss.as.cli.parsing.QuotesState;
import org.jboss.as.cli.parsing.StateParser;


/**
 *
 * @author Alexey Loubyansky
 */
public class ConditionArgument extends ArgumentWithValue {

    private static final ParsingState AND = new OperationParsingState(AndOperation.SYMBOL) {
        @Override
        BaseOperation createOperation() {
            return new AndOperation();
        }
    };
    private static final ParsingState OR = new OperationParsingState(OrOperation.SYMBOL) {
        @Override
        BaseOperation createOperation() {
            return new OrOperation();
        }
    };
    private static final ParsingState GT = new OperationParsingState(GreaterThanOperation.SYMBOL) {
        @Override
        BaseOperation createOperation() {
            return new GreaterThanOperation();
        }
    };
    private static final ParsingState LT = new OperationParsingState(LesserThanOperation.SYMBOL) {
        @Override
        BaseOperation createOperation() {
            return new LesserThanOperation();
        }
    };
    private static final ParsingState NEQ = new OperationParsingState(NotEqualsOperation.SYMBOL) {
        @Override
        BaseOperation createOperation() {
            return new NotEqualsOperation();
        }
    };
    private static final ParsingState EQ = new OperationParsingState(EqualsOperation.SYMBOL) {
        @Override
        BaseOperation createOperation() {
            return new EqualsOperation();
        }
    };
    private static final ParsingState NGT = new OperationParsingState(NotGreaterThanOperation.SYMBOL) {
        @Override
        BaseOperation createOperation() {
            return new NotGreaterThanOperation();
        }
    };
    private static final ParsingState NLT = new OperationParsingState(NotLesserThanOperation.SYMBOL) {
        @Override
        BaseOperation createOperation() {
            return new NotLesserThanOperation();
        }
    };
    private static final ParsingState MCH = new OperationParsingState(MatchOperation.SYMBOL) {
        @Override
        BaseOperation createOperation() {
            return new MatchOperation();
        }
    };

    private DefaultParsingState parenthesisState;
    private ExpressionBaseState exprState;

    public ConditionArgument(CommandHandlerWithArguments handler) {
        super(handler, 0, "--condition");
    }

    @Override
    protected ParsingState initParsingState() {

        final CharacterHandler defaultHandler = new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                final char c = ctx.getCharacter();
                if(c == '&' && isFollowingChar(ctx, '&')) {
                    signalState(ctx, AND, true);
                } else if(c == '|' && isFollowingChar(ctx, '|')) {
                    signalState(ctx, OR, true);
                } else if(c == '=' && isFollowingChar(ctx, '=')) {
                    signalState(ctx, EQ, true);
                } else if(c == '~' && isFollowingChar(ctx, '=')) {
                    signalState(ctx, MCH, true);
                } else if(c == '!' && isFollowingChar(ctx, '=')) {
                    signalState(ctx, NEQ, true);
                } else if(c == '>' && isFollowingChar(ctx, '=')) {
                    signalState(ctx, NLT, true);
                } else if(c == '<' && isFollowingChar(ctx, '=')) {
                    signalState(ctx, NGT, true);
                } else if(c == '>' && !isFollowingChar(ctx, '=')) {
                    signalState(ctx, GT, false);
                } else if(c == '<' && !isFollowingChar(ctx, '=')) {
                    signalState(ctx, LT, false);
                } else {
                    ctx.getCallbackHandler().character(ctx);
                }
            }

            protected void signalState(final ParsingContext ctx, final ParsingState state, boolean advance) throws CommandFormatException {
                ctx.enterState(state);
                if(advance) {
                    ctx.advanceLocation(1);
                }
                ctx.leaveState();
            }

            protected boolean isFollowingChar(ParsingContext ctx, char c) {
                if (ctx.getLocation() + 1 < ctx.getInput().length()) {
                    return ctx.getInput().charAt(ctx.getLocation() + 1) == c;
                }
                return false;
            }};

        exprState = new ExpressionBaseState("EXPR", true, false);
        exprState.setEnterHandler(GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER);
        exprState.setDefaultHandler(defaultHandler);
        exprState.enterState('"', QuotesState.QUOTES_INCLUDED);
        exprState.enterState('\\', EscapeCharacterState.INSTANCE);
        exprState.putHandler(')', new CharacterHandler() {
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                ctx.leaveState();
                if(ctx.getState() == parenthesisState) {
                    ctx.leaveState();
                }
            }});

        parenthesisState = new DefaultStateWithEndCharacter("PARENTHESIS", ')', true, true);
        parenthesisState.enterState('\\', EscapeCharacterState.INSTANCE);
        parenthesisState.enterState('"', QuotesState.QUOTES_INCLUDED);
        parenthesisState.enterState('(', parenthesisState);
        exprState.enterState('(', parenthesisState);
        parenthesisState.setDefaultHandler(new CharacterHandler() {
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                ctx.enterState(exprState);
            }});
        //parenthesisState.setReturnHandler(GlobalCharacterHandlers.LEAVE_STATE_HANDLER);

        DefaultParsingState initial = new DefaultParsingState("EXPR_INITIAL");
        initial.enterState('(', parenthesisState);
        initial.setDefaultHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                ctx.enterState(exprState);
            }});
        return initial;
    }

    @Override
    public String getResolvedValue(ParsedCommandLine parsedLine, boolean required) throws CommandFormatException {
        final String value = getOriginalValue(parsedLine, required);

        if(value == null) {
            return null;
        }

        final StringBuilder buf = new StringBuilder();
        StateParser.parse(value, new ParsingStateCallbackHandler(){
            @Override
            public void enteredState(ParsingContext ctx) throws CommandFormatException {
            }

            @Override
            public void leavingState(ParsingContext ctx) throws CommandFormatException {
            }

            @Override
            public void character(ParsingContext ctx) throws CommandFormatException {
                buf.append(ctx.getCharacter());
            }}, initialState);
        return buf.toString();
    }

    public Operation resolveOperation(ParsedCommandLine parsedLine) throws CommandFormatException {
        final String value = getOriginalValue(parsedLine, true);
        if(value == null) {
            return null;
        }

        //System.out.println("PARSING '" + value + "'");

        final ConditionOperationCallback callback = new ConditionOperationCallback();
        StateParser.parse(value, callback, initialState);
        return callback.expr.getExpression();
    }


    private final class ConditionOperationCallback implements ParsingStateCallbackHandler {
        final StringBuilder buf = new StringBuilder();
        ExpressionParsingState expr = new ExpressionParsingState();

        @Override
        public void enteredState(ParsingContext ctx) throws CommandFormatException {
            //System.out.println("entered " + ctx.getState().getId());

            if(ctx.getState() == parenthesisState) {
                expr = new ExpressionParsingState(expr);
            }
        }

        @Override
        public void leavingState(ParsingContext ctx) throws CommandFormatException {
            final ParsingState state = ctx.getState();
            //System.out.println("left " + state.getId());

            try {
                if (state instanceof OperationParsingState) {
                    BaseOperation nextOperation = ((OperationParsingState) state).createOperation();
                    final Operand nextOperand;
                    if(expr.nestedExpression != null) {
                        nextOperand = expr.nestedExpression;
                        expr.nestedExpression = null;
                    } else if(nextOperation instanceof ComparisonOperation) {
                        nextOperand = new ModelNodePathOperand(buf.toString());
                    } else {
                        nextOperand = new StringValueOperand(buf.toString());
                    }
                    expr.place(nextOperand, nextOperation);
                    buf.setLength(0);
                } else if (state == exprState) {
                    if(buf.length() > 0) {
                        if(expr.lastParsed == null) {
                            throw new CommandFormatException("The operation is missing in front of '" + buf.toString() + "'");
                        }
                        final Operand operand = new StringValueOperand(buf.toString());
                        expr.lastParsed.addOperand(operand);
                        buf.setLength(0);
                    } else if(expr.nestedExpression != null) {
                        if(expr.lastParsed == null) {
                            throw new CommandFormatException("The operation is missing in front of '" + expr.nestedExpression + "'");
                        }
                        expr.lastParsed.addOperand(expr.nestedExpression);
                        expr.nestedExpression = null;
                    }
                } else if(state == parenthesisState) {
                    if(expr.parent != null) {
                        expr.parent.nestedExpression = expr.root;
                        expr = expr.parent;
                    }
                }
            } catch (CommandLineException e) {
                throw new CommandFormatException("Failed to parse if condition", e);
            }
        }

        @Override
        public void character(ParsingContext ctx) throws CommandFormatException {
            //System.out.println(ctx.getState().getId() + ": " + ctx.getCharacter());
            if (parenthesisState != ctx.getState() && (!Character.isWhitespace(ctx.getCharacter())
                    || ctx.getState().getId().equals(QuotesState.ID) || ctx.getState().getId().equals(EscapeCharacterState.ID))) {
                buf.append(ctx.getCharacter());
            }
        }
    }

    private static class ExpressionParsingState {
        final ExpressionParsingState parent;
        BaseOperation root;
        BaseOperation lastParsed;

        BaseOperation nestedExpression;

        ExpressionParsingState() {
            this(null);
        }

        ExpressionParsingState(ExpressionParsingState expr) {
            this.parent = expr;
        }

        BaseOperation getExpression() {
            return root == null ? nestedExpression : root;
        }

        protected void place(final Operand nextOperand, final BaseOperation nextOperation) throws CommandLineException {
            BaseOperation parent = lastParsed;
            nextOperation.setParent(parent);
            lastParsed = nextOperation;

            //System.out.println("orderOperations " + root + " " + lastParsed + " " + nextOperand);

            if(parent == null) {
                root = lastParsed;
            }

            //System.out.println("   parent " + parent);
            if (parent == null) {
                lastParsed.addOperand(nextOperand);
            } else if (parent.getPriority() >= lastParsed.getPriority()) {
                if (parent.allowsMoreArguments()) {
                    parent.addOperand(nextOperand);
                    BaseOperation targetParent = parent;
                    while (targetParent != null) {
                        if (targetParent.getPriority() <= lastParsed.getPriority()) {
                            parent = targetParent;
                            break;
                        }
                        targetParent = targetParent.getParent();
                    }
                    if (targetParent == null) {
                        lastParsed.addOperand(root);
                        root = lastParsed;
                    } else if (parent.getName().equals(lastParsed.getName())) {
                        lastParsed = parent;
                    } else {
                        lastParsed.addOperand(parent.getLastOperand());
                        parent.replaceLastOperand(lastParsed);
                    }
                } else {
                    lastParsed.addOperand(nextOperand);
                    BaseOperation targetParent = parent.getParent();
                    while (targetParent != null) {
                        if (targetParent.allowsMoreArguments()) {
                            parent = targetParent;
                            break;
                        }
                        targetParent = targetParent.getParent();
                    }
                    if (!parent.allowsMoreArguments()) {
                        throw new IllegalStateException();
                    }
                    parent.addOperand(lastParsed);
                }
            } else {
                lastParsed.addOperand(nextOperand);
                BaseOperation targetParent = parent;
                while (targetParent != null) {
                    if (targetParent.allowsMoreArguments()) {
                        parent = targetParent;
                        break;
                    }
                    targetParent = targetParent.getParent();
                }
                if (!parent.allowsMoreArguments()) {
                    throw new IllegalStateException();
                }
                parent.addOperand(lastParsed);
            }

            //System.out.println("ordered " + root);
        }
    }

    private abstract static class OperationParsingState extends DefaultParsingState {

        public OperationParsingState(String id) {
            super(id);
        }

        abstract BaseOperation createOperation();
    }
}
