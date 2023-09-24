/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import java.util.List;

import org.jboss.as.cli.ArgumentValueConverter;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.handlers.CommandHandlerWithArguments;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.parsing.ExpressionBaseState;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.ParsingState;
import org.jboss.as.cli.parsing.ParsingStateCallbackHandler;
import org.jboss.as.cli.parsing.StateParser;
import org.jboss.as.cli.parsing.WordCharacterHandler;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class ArgumentWithValue extends ArgumentWithoutValue {

    private static final ParsingState DEFAULT_EXPRESSION_STATE;

    static {
        final ExpressionBaseState state = new ExpressionBaseState("EXPR", true, false);
        state.setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_ON);
        DEFAULT_EXPRESSION_STATE = state;
    }

    private final CommandLineCompleter valueCompleter;
    private final ArgumentValueConverter valueConverter;

    /** initial state for value parsing */
    protected final ParsingState initialState;

    public ArgumentWithValue(CommandHandlerWithArguments handler, String fullName) {
        this(handler, null, ArgumentValueConverter.DEFAULT, fullName, null);
    }

    public ArgumentWithValue(CommandHandlerWithArguments handler, CommandLineCompleter valueCompleter, String fullName) {
        this(handler, valueCompleter, ArgumentValueConverter.DEFAULT, fullName, null);
    }

    public ArgumentWithValue(CommandHandlerWithArguments handler, CommandLineCompleter valueCompleter,
            ArgumentValueConverter valueConverter, String fullName) {
        this(handler, valueCompleter, valueConverter, fullName, null);
    }

    public ArgumentWithValue(CommandHandlerWithArguments handler, int index, String fullName) {
        this(handler, null, index, fullName);
    }

    public ArgumentWithValue(CommandHandlerWithArguments handler, CommandLineCompleter valueCompleter, int index, String fullName) {
        super(handler, index, fullName);
        this.valueCompleter = valueCompleter;
        valueConverter = ArgumentValueConverter.DEFAULT;
        this.initialState = initParsingState();
    }

    public ArgumentWithValue(CommandHandlerWithArguments handler, CommandLineCompleter valueCompleter,
            ArgumentValueConverter valueConverter, String fullName, String shortName) {
        super(handler, fullName, shortName);
        this.valueCompleter = valueCompleter;
        this.valueConverter = valueConverter;
        this.initialState = initParsingState();
    }

    protected ParsingState initParsingState() {
        return DEFAULT_EXPRESSION_STATE;
    }

    public CommandLineCompleter getValueCompleter() {
        return valueCompleter;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandArgument#getValue(org.jboss.as.cli.CommandContext)
     */
    @Override
    public String getValue(ParsedCommandLine args, boolean required) throws CommandFormatException {
        return getResolvedValue(args, required);
    }

    /**
     * Calls getOriginalValue(ParsedCommandLine parsedLine, boolean required) and correctly
     * handles escape sequences and resolves system properties.
     *
     * @param parsedLine  parsed command line
     * @param required  whether the argument is required
     * @return  resolved argument value
     * @throws CommandFormatException  in case the required argument is missing
     */
    public String getResolvedValue(ParsedCommandLine parsedLine, boolean required) throws CommandFormatException {
        final String value = getOriginalValue(parsedLine, required);
        return resolveValue(value, initialState);
    }

    public static String resolveValue(final String value) throws CommandFormatException {
        return resolveValue(value, DEFAULT_EXPRESSION_STATE);
    }

    public static String resolveValue(final String value, ParsingState initialState) throws CommandFormatException {

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

    /**
     * Returns value as it appeared on the command line with escape sequences
     * and system properties not resolved. The variables, though, are resolved
     * during the initial parsing of the command line.
     *
     * @param parsedLine  parsed command line
     * @param required  whether the argument is required
     * @return  argument value as it appears on the command line
     * @throws CommandFormatException  in case the required argument is missing
     */
    public String getOriginalValue(ParsedCommandLine parsedLine, boolean required) throws CommandFormatException {
        String value = null;
        if(parsedLine.hasProperties()) {
            if(index >= 0) {
                List<String> others = parsedLine.getOtherProperties();
                if(others.size() > index) {
                    return others.get(index);
                }
            }

            value = parsedLine.getPropertyValue(fullName);
            if(value == null && shortName != null) {
                value = parsedLine.getPropertyValue(shortName);
            }
        }

        if(required && value == null && !isPresent(parsedLine)) {
            StringBuilder buf = new StringBuilder();
            buf.append("Required argument ");
            buf.append('\'').append(fullName).append('\'');
            buf.append(" is missing.");
            throw new CommandFormatException(buf.toString());
        }
        return value;
    }

    public ModelNode toModelNode(CommandContext ctx) throws CommandFormatException {
        final ParsedCommandLine parsedLine = ctx.getParsedCommandLine();
        final String value = getOriginalValue(parsedLine, false);
        if(value == null) {
            return null;
        }
        return valueConverter.fromString(ctx, value);
    }

    /**
     * Argument can only appear if not already present in the parsed command
     * BUT
     * this is not all the time true, for example, an argument cannot appear
     * AFTER some other arguments. This logic is in the parent implementation.
     * That is why, although we would like to redefine the method at this point
     * we can't.
     */
//    @Override
//    public boolean canAppearNext(CommandContext ctx)
//            throws CommandFormatException {
//        return !isPresent(ctx.getParsedCommandLine());
//    }

    @Override
    public boolean isValueRequired() {
        return true;
    }

    @Override
    public boolean isValueComplete(ParsedCommandLine args) throws CommandFormatException {

        if(!isPresent(args)) {
            return false;
        }

        if (index >= 0) {
            final int size = args.getOtherProperties().size();
            if(index >= size) {
                return false;
            }
            if(index < size -1) {
                return true;
            }
            return !args.getOtherProperties().get(index).equals(args.getLastParsedPropertyValue());
        }

        if(fullName.equals(args.getLastParsedPropertyName())) {
            return false;
        }

        if(shortName != null && shortName.equals(args.getLastParsedPropertyName())) {
            return false;
        }
        return true;
    }

    public ArgumentValueConverter getValueConverter() {
        return valueConverter;
    }
}
