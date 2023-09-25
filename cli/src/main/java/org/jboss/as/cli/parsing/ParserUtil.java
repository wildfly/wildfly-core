/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;


import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineFormat;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.parsing.StateParser.SubstitutedLine;
import org.jboss.as.cli.parsing.command.ArgumentListState;
import org.jboss.as.cli.parsing.command.ArgumentState;
import org.jboss.as.cli.parsing.command.ArgumentValueNotFinishedException;
import org.jboss.as.cli.parsing.command.ArgumentValueState;
import org.jboss.as.cli.parsing.command.CommandFormat;
import org.jboss.as.cli.parsing.command.CommandNameState;
import org.jboss.as.cli.parsing.command.CommandState;
import org.jboss.as.cli.parsing.operation.HeaderListState;
import org.jboss.as.cli.parsing.operation.HeaderNameState;
import org.jboss.as.cli.parsing.operation.HeaderState;
import org.jboss.as.cli.parsing.operation.HeaderValueState;
import org.jboss.as.cli.parsing.operation.NodeState;
import org.jboss.as.cli.parsing.operation.OperationFormat;
import org.jboss.as.cli.parsing.operation.OperationRequestState;
import org.jboss.as.cli.parsing.operation.PropertyListState;

/**
 *
 * @author Alexey Loubyansky
 */
public class ParserUtil {

    /**
     * Returns the string which was actually parsed with all the substitutions performed
     */
    public static String parse(String commandLine, final CommandLineParser.CallbackHandler handler) throws CommandFormatException {
        return parse(commandLine, handler, true);
    }

    /**
     * Returns the string which was actually parsed with all the substitutions performed
     */
    public static String parse(String commandLine, final CommandLineParser.CallbackHandler handler, boolean strict) throws CommandFormatException {
        if(commandLine == null) {
            return null;
        }
        final ParsingStateCallbackHandler callbackHandler = getCallbackHandler(handler);
        return StateParser.parse(commandLine, callbackHandler, InitialState.INSTANCE, strict);
    }

    /**
     * Returns the string which was actually parsed with all the substitutions performed
     */
    public static String parse(String commandLine, final CommandLineParser.CallbackHandler handler, boolean strict,
            CommandContext ctx) throws CommandFormatException {
        if(commandLine == null) {
            return null;
        }
        SubstitutedLine sl = parseLine(commandLine, handler, strict, ctx);
        return sl == null ? null : sl.getSubstitued();
    }

    public static SubstitutedLine parseLine(String commandLine, final CommandLineParser.CallbackHandler handler, boolean strict,
            CommandContext ctx) throws CommandFormatException {
        return parseLine(commandLine, handler, strict, ctx, false);
    }

    public static SubstitutedLine parseLine(String commandLine, final CommandLineParser.CallbackHandler handler, boolean strict,
            CommandContext ctx, boolean disableResolutionException) throws CommandFormatException {
        if (commandLine == null) {
            return null;
        }
        final ParsingStateCallbackHandler callbackHandler = getCallbackHandler(handler);
        return StateParser.parseLine(commandLine, callbackHandler, InitialState.INSTANCE, strict, disableResolutionException, ctx);
    }

    /**
     * Returns the string which was actually parsed with all the substitutions performed
     */
    public static String parseOperationRequest(String commandLine, final CommandLineParser.CallbackHandler handler) throws CommandFormatException {
        SubstitutedLine sl = parseOperationRequestLine(commandLine, handler, null);
        return sl == null ? null : sl.getSubstitued();
    }

    /**
     * Returns the string which was actually parsed with all the substitutions
     * performed
     */
    public static String parseOperationRequest(String commandLine, final CommandLineParser.CallbackHandler handler, CommandContext ctx) throws CommandFormatException {
        SubstitutedLine sl = parseOperationRequestLine(commandLine, handler, ctx);
        return sl == null ? null : sl.getSubstitued();
    }

    public static SubstitutedLine parseOperationRequestLine(String commandLine,
            final CommandLineParser.CallbackHandler handler) throws CommandFormatException {
        return parseOperationRequestLine(commandLine, handler, null);
    }

    /**
     * Returns the string which was actually parsed with all the substitutions
     * performed
     */
    public static SubstitutedLine parseOperationRequestLine(String commandLine,
            final CommandLineParser.CallbackHandler handler, CommandContext ctx) throws CommandFormatException {
        if (commandLine == null) {
            return null;
        }
        final ParsingStateCallbackHandler callbackHandler = getCallbackHandler(handler);
        handler.setFormat(OperationFormat.INSTANCE);
        return StateParser.parseLine(commandLine, callbackHandler, OperationRequestState.INSTANCE, ctx);
    }

    /**
     * Returns the string which was actually parsed with all the substitutions performed
     */
    public static String parseHeaders(String commandLine, final CommandLineParser.CallbackHandler handler) throws CommandFormatException {
        if(commandLine == null) {
            return null;
        }
        SubstitutedLine sl = parseHeadersLine(commandLine, handler, null);
        return sl == null ? null : sl.getSubstitued();
    }

    /**
     * Returns the string which was actually parsed with all the substitutions
     * performed
     */
    public static String parseHeaders(String commandLine, final CommandLineParser.CallbackHandler handler, CommandContext ctx) throws CommandFormatException {
        if (commandLine == null) {
            return null;
        }
        SubstitutedLine sl = parseHeadersLine(commandLine, handler, ctx);
        return sl == null ? null : sl.getSubstitued();
    }

    /**
     * Returns the string which was actually parsed with all the substitutions
     * performed
     */
    public static SubstitutedLine parseHeadersLine(String commandLine, final CommandLineParser.CallbackHandler handler, final CommandContext ctx) throws CommandFormatException {
        if (commandLine == null) {
            return null;
        }
        final ParsingStateCallbackHandler callbackHandler = getCallbackHandler(handler);
        return StateParser.parseLine(commandLine, callbackHandler, HeaderListState.INSTANCE, ctx);
    }

    /**
     * Returns the string which was actually parsed with all the substitutions performed
     */
    public static String parseCommandArgs(String commandLine, final CommandLineParser.CallbackHandler handler) throws CommandFormatException {
        if(commandLine == null) {
            return null;
        }
        final ParsingStateCallbackHandler callbackHandler = getCallbackHandler(handler);
        return StateParser.parse(commandLine, callbackHandler, ArgumentListState.INSTANCE);
    }

    /**
     * Returns the string which was actually parsed with all the substitutions performed
     */
    public static String parse(String str, final CommandLineParser.CallbackHandler handler, ParsingState initialState) throws CommandFormatException {
        if(str == null) {
            return null;
        }
        final ParsingStateCallbackHandler callbackHandler = getCallbackHandler(handler);
        return StateParser.parse(str, callbackHandler, initialState);
    }

    protected static ParsingStateCallbackHandler getCallbackHandler(final CommandLineParser.CallbackHandler handler) {

        return new ParsingStateCallbackHandler() {

            private int nameValueSeparator = -1;
            private String name;
            final StringBuilder buffer = new StringBuilder();
            int bufferStartIndex = 0;
            boolean inValue;

            String delegateStateId;
            ParsingStateCallbackHandler delegate;
            CommandLineFormat format;

            @Override
            public void enteredState(ParsingContext ctx) throws CommandFormatException {

                final String id = ctx.getState().getId();
//                System.out.println("entered " + id + " " + ctx.getCharacter());

                if(delegate != null) {
                    delegate.enteredState(ctx);
                    return;
                }

                if(!inValue && ctx.getState().updateValueIndex()) {
                    bufferStartIndex = ctx.getLocation();
                    inValue = ctx.getState().lockValueIndex();
                }

                if (id.equals(PropertyListState.ID)) {
                    handler.propertyListStart(ctx.getLocation());
                } else if ("ADDR_OP_SEP".equals(id)) {
                    handler.addressOperationSeparator(ctx.getLocation());
                } else if ("NAME_VALUE_SEPARATOR".equals(id)) {
                    nameValueSeparator = ctx.getLocation();
                    if (buffer.length() > 0) {
                        name = buffer.toString().trim();
                        buffer.setLength(0);
                    }
                } else if(id.equals(CommandState.ID)) {
                    format = CommandFormat.INSTANCE;
                    handler.setFormat(CommandFormat.INSTANCE);
                } else if(id.equals(OperationRequestState.ID)) {
                    format = OperationFormat.INSTANCE;
                    handler.setFormat(OperationFormat.INSTANCE);
                } else if (HeaderListState.ID.equals(id)) {
                    handler.headerListStart(ctx.getLocation());
                }
            }

            @Override
            public void leavingState(ParsingContext ctx) throws CommandFormatException {

                final String id = ctx.getState().getId();
//                System.out.println("leaving " + id + " " + ctx.getCharacter());

                if(delegateStateId != null && !id.equals(delegateStateId)) {
                    delegate.leavingState(ctx);
                    return;
                }

                if (id.equals(PropertyListState.ID)) {
                    if (!ctx.isEndOfContent()) {
                        handler.propertyListEnd(ctx.getLocation());
                    }
                } else if (ArgumentState.ID.equals(id)) {
                    if (this.name != null) {
                        final String value = getPropertyValue(ctx);
                        if (value != null) {
                            handler.property(this.name, value, bufferStartIndex/*nameValueSeparator*/);
                        } else {
                            handler.propertyName(bufferStartIndex, this.name);
                            if (nameValueSeparator != -1) {
                                handler.propertyNameValueSeparator(nameValueSeparator);
                            }
                        }
                    } else {
                        handler.propertyNoValue(bufferStartIndex, buffer.toString().trim());
                    }
                    if(!ctx.isEndOfContent() || format != null &&
                            // if the char is recognized as the separator but there was an error,
                            // at least atm, this means the character belongs to an unfinished/incomplete
                            // property value (e.g. a closing } or ], or " is missing) and it's not
                            // really a property separator.
                            format.isPropertySeparator(ctx.getCharacter()) && ctx.getError() == null) {
                        handler.propertySeparator(ctx.getLocation());
                    }

                    buffer.setLength(0);
                    name = null;
                    nameValueSeparator = -1;
                } else if (ArgumentValueState.ID.equals(id)) {
                    if (name == null) {
                        handler.property(null, buffer.toString(), bufferStartIndex);
                        buffer.setLength(0);
                        if(!ctx.isEndOfContent()) {
                            handler.propertySeparator(ctx.getLocation());
                        }
                    }
                } else if (CommandNameState.ID.equals(id)) {
                    final String opName = buffer.toString().trim();
                    if(!opName.isEmpty()) {
                        handler.operationName(bufferStartIndex, opName);
                    }
                    buffer.setLength(0);
                } else if (NodeState.ID.equals(id)) {
                    char ch = ctx.getCharacter();
                    if (buffer.length() == 0) {
                        if (ch == '/') {
                            handler.rootNode(bufferStartIndex);
                            handler.nodeSeparator(ctx.getLocation());
                        }
                    } else {
                        final String value = buffer.toString().trim();
                        // If ctx.isEndOfContent() is true and the last character is '=' or ':', it must be escaped and
                        // it won't be considered a type/name separator (if it wasn't escaped the NodeState would've
                        // been left already).
                        if (ch == '=' && !ctx.isEndOfContent()) {
                            handler.nodeType(bufferStartIndex, value);
                            handler.nodeTypeNameSeparator(ctx.getLocation());
                        } else if (ch == ':' && !ctx.isEndOfContent()) {
                            handler.nodeName(bufferStartIndex, value);
                        } else {
                            if (".".equals(value)) {
                                // stay at the current address
                            } else if ("..".equals(value)) {
                                handler.parentNode(ctx.getLocation() - 2);
                            } else if (".type".equals(value)) {
                                handler.nodeType(ctx.getLocation() - 5);
                            } else {
                                if (ch == '/') {
                                    if ("".equals(value)) {
                                        handler.rootNode(ctx.getLocation());
                                    } else {
                                        handler.nodeName(bufferStartIndex, value);
                                    }
                                } else {
                                    handler.nodeTypeOrName(bufferStartIndex, value);
                                }
                            }

                            if (ch == '/' && value.charAt(value.length() - 1) != '/') {
                                handler.nodeSeparator(ctx.getLocation());
                            }
                        }
                    }
                    buffer.setLength(0);
                } else if (HeaderListState.ID.equals(id)) {
                    if (!ctx.isEndOfContent()) {
                        handler.headerListEnd(ctx.getLocation());
                    }
                } else if (HeaderNameState.ID.equals(id)) {
                    final String headerName = buffer.toString().trim();
                    if(!headerName.isEmpty()) {
                        this.name = headerName;
                        delegate = handler.headerName(bufferStartIndex, headerName);
                        if(delegate != null) {
                            delegateStateId = HeaderState.ID;
                        }
                    }
                    buffer.setLength(0);
                } else if (HeaderValueState.ID.equals(id)) {
                    handler.header(name, buffer.toString(), bufferStartIndex);
                    buffer.setLength(0);
                    nameValueSeparator = -1;
                } else if (HeaderState.ID.equals(id)) {
                    if(nameValueSeparator > 0) {
                        handler.headerNameValueSeparator(nameValueSeparator);
                        nameValueSeparator = -1;
                    }
                    this.name = null;
                    delegate = null;
                    delegateStateId = null;
                    if(!ctx.isEndOfContent() && ctx.getCharacter() == ';') {
                        handler.headerSeparator(ctx.getLocation());
                    }
                } else if (OutputTargetState.ID.equals(id)) {
                    handler.outputTarget(bufferStartIndex, buffer.toString().trim());
                    buffer.setLength(0);
                } else if (OperatorState.ID.equals(id)) {
                    handler.operator(bufferStartIndex);
                    buffer.setLength(0);
                }

                if(inValue && ctx.getState().lockValueIndex()) {
                    inValue = false;
                }
            }

            private String getPropertyValue(ParsingContext ctx) {
                final String value = buffer.toString();
                if (value.trim().length() == 0) {
                    return null;
                } else if(ctx.isEndOfContent() && ctx.getError() != null &&
                        ctx.getError() instanceof ArgumentValueNotFinishedException) {
                    // if there was an error and it's the end of input the argument value is incomplete (missing },], or ")
                    // and the trailing whitespaces should be treated as part of the value.
                    return value.replaceAll("^\\s+","");
                } else {
                    return value.trim();
                }
            }

            @Override
            public void character(ParsingContext ctx) throws CommandFormatException {
                if(delegate != null) {
                    delegate.character(ctx);
                    return;
                }
//                System.out.println(ctx.getState().getId() + " '" + ctx.getCharacter() + "'");
                buffer.append(ctx.getCharacter());
            }
        };
    }
}
