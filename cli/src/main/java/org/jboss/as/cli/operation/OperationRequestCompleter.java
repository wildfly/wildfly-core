/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.operation;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineFormat;
import org.jboss.as.cli.EscapeSelector;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.SegmentParsingInitialState;
import org.jboss.as.cli.parsing.StateParser;
import org.jboss.logging.Logger;


/**
 *
 * @author Alexey Loubyansky
 */
public class OperationRequestCompleter implements CommandLineCompleter {

    public static final OperationRequestCompleter INSTANCE = new OperationRequestCompleter();

    public static final CommandLineCompleter ARG_VALUE_COMPLETER = new CommandLineCompleter(){
        final DefaultCallbackHandler parsedOp = new DefaultCallbackHandler(false);
        @Override
        public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
            try {
                parsedOp.parseOperation(ctx.getCurrentNodePath(), buffer);
            } catch (CommandFormatException e) {
                return -1;
            }
            return INSTANCE.complete(ctx, parsedOp, buffer, cursor, candidates);
        }};

    public static final EscapeSelector ESCAPE_SELECTOR = new EscapeSelector() {
        @Override
        public boolean isEscape(char ch) {
            return ch == ':' || ch == '/' || ch == '=' || ch == ' ' || ch == '"' || ch == '\\' || ch == '\'';
        }
    };

    /**
     * Escape selector for quoted strings - only " and \ chars should be escaped
     */
    public static final EscapeSelector ESCAPE_SELECTOR_INSIDE_QUOTES = new EscapeSelector() {
        @Override
        public boolean isEscape(char ch) {
            return ch == '"' || ch == '\\';
        }
    };

    private static final Logger LOGGER = Logger.getLogger(OperationRequestCompleter.class);

    @Override
    public int complete(CommandContext ctx, final String buffer, int cursor, List<String> candidates) {
        return complete(ctx, ctx.getParsedCommandLine(), buffer, cursor, candidates);
    }

    public int complete(CommandContext ctx, OperationCandidatesProvider candidatesProvider,
            final String buffer, int cursor, List<String> candidates) {
        return complete(ctx, ctx.getParsedCommandLine(), candidatesProvider, buffer, cursor, candidates);
    }

    public int complete(CommandContext ctx, ParsedCommandLine parsedCmd, final String buffer, int cursor, List<String> candidates) {
        return complete(ctx, parsedCmd, ctx.getOperationCandidatesProvider(), buffer, cursor, candidates);
    }

    protected int complete(CommandContext ctx, ParsedCommandLine parsedCmd, OperationCandidatesProvider candidatesProvider, final String buffer, int cursor, List<String> candidates) {

        if(parsedCmd.isRequestComplete()) {
            return -1;
        }

        if(parsedCmd.endsOnHeaderListStart() || parsedCmd.hasHeaders()) {
            final Map<String, OperationRequestHeader> headers = candidatesProvider.getHeaders(ctx);
            if(headers.isEmpty()) {
                return -1;
            }
            int result = buffer.length();
            if(parsedCmd.getLastHeaderName() != null) {
                if(buffer.endsWith(parsedCmd.getLastHeaderName())) {
                    result = parsedCmd.getLastChunkIndex();
                    for (String name : headers.keySet()) {
                        if (name.equals(parsedCmd.getLastHeaderName())) {
                            result = completeHeader(headers, ctx, parsedCmd, buffer, cursor, candidates);
                            break;
                        }
                        if(!parsedCmd.hasHeader(name) && name.startsWith(parsedCmd.getLastHeaderName())) {
                            candidates.add(name);
                        }
                    }
                } else {
                    result = completeHeader(headers, ctx, parsedCmd, buffer, cursor, candidates);
                }
            } else {
                if(!parsedCmd.hasHeaders()) {
                    candidates.addAll(headers.keySet());
                } else if(parsedCmd.endsOnHeaderSeparator()) {
                    candidates.addAll(headers.keySet());
                    for(ParsedOperationRequestHeader parsed : parsedCmd.getHeaders()) {
                        candidates.remove(parsed.getName());
                    }
                } else {
                    final ParsedOperationRequestHeader lastParsedHeader = parsedCmd.getLastHeader();
                    final OperationRequestHeader lastHeader = headers.get(lastParsedHeader.getName());
                    if(lastHeader == null) {
                        return -1;
                    }
                    final CommandLineCompleter headerCompleter = lastHeader.getCompleter();
                    if(headerCompleter == null) {
                        return -1;
                    }
                    result = headerCompleter.complete(ctx, buffer, cursor, candidates);
                }
            }
            Collections.sort(candidates);
            return result;
        }

        if(parsedCmd.endsOnPropertyListEnd()) {
            return buffer.length();
        }

        if (parsedCmd.hasProperties() || parsedCmd.endsOnPropertyListStart()
                || parsedCmd.endsOnNotOperator()) {
            if(!parsedCmd.hasOperationName()) {
                return -1;
            }
            final Collection<CommandArgument> allArgs = candidatesProvider.getProperties(ctx, parsedCmd.getOperationName(), parsedCmd.getAddress());
            if (allArgs.isEmpty()) {
                final CommandLineFormat format = parsedCmd.getFormat();
                // If no arguments are provided, the only valid case is that the operation
                // has no arguments. Invalid cases are (wrong operation, exception, ..
                // In the valid case the operation should be closed.
                // If some properties are already typed, something is wrong, do not complete
                if (!parsedCmd.hasProperties()
                        && format != null
                        && format.getPropertyListEnd() != null
                        && format.getPropertyListEnd().length() > 0) {
                    candidates.add(format.getPropertyListEnd());
                }
                return buffer.length();
            }

            try {
                // Retrieve properties with implicit values only.
                if (parsedCmd.endsOnNotOperator()) {
                    for (CommandArgument arg : allArgs) {
                        if (arg.canAppearNext(ctx)) {
                            if (!arg.isValueRequired()) {
                                candidates.add(arg.getFullName());
                            }
                        }
                    }

                    Collections.sort(candidates);
                    return buffer.length();
                }

                if (!parsedCmd.hasProperties()) {
                    boolean needNeg = false;
                    for (CommandArgument arg : allArgs) {
                        if (arg.canAppearNext(ctx)) {
                            if (arg.getIndex() >= 0) {
                                final CommandLineCompleter valCompl = arg.getValueCompleter();
                                if (valCompl != null) {
                                    valCompl.complete(ctx, "", 0, candidates);
                                }
                            } else {
                                String argName = arg.getFullName();
                                candidates.add(argName);
                                if (!arg.isValueRequired()) {
                                    needNeg = true;
                                }
                            }
                        }
                    }
                    if (needNeg) {
                        candidates.add(Util.NOT_OPERATOR);
                    }
                    Collections.sort(candidates);
                    return buffer.length();
                }
            } catch (CommandFormatException e) {
                return -1;
            }

            int result = buffer.length();

            String chunk = null;
            CommandLineCompleter valueCompleter = null;
            if (!parsedCmd.endsOnPropertySeparator()) {
                final String argName = parsedCmd.getLastParsedPropertyName();
                final String argValue = parsedCmd.getLastParsedPropertyValue();
                if (argValue != null || parsedCmd.endsOnPropertyValueSeparator()) {
                    result = parsedCmd.getLastChunkIndex();
                    if (parsedCmd.endsOnPropertyValueSeparator()) {
                        ++result;// it enters on '='
                    }
                    chunk = argValue;
                    if (argName != null) {
                        valueCompleter = getValueCompleter(ctx, allArgs, argName);
                    } else {
                        valueCompleter = getValueCompleter(ctx, allArgs, parsedCmd.getOtherProperties().size() - 1);
                    }
                    if (valueCompleter == null) {
                        if (parsedCmd.endsOnSeparator()) {
                            return -1;
                        }
                        for (CommandArgument arg : allArgs) {
                            try {
                                if (arg.canAppearNext(ctx) && !arg.getFullName().equals(argName)) {
                                    return -1;
                                }
                            } catch (CommandFormatException e) {
                                break;
                            }
                        }
                        final CommandLineFormat format = parsedCmd.getFormat();
                        if (format != null && format.getPropertyListEnd() != null && format.getPropertyListEnd().length() > 0) {
                            candidates.add(format.getPropertyListEnd());
                        }
                        return buffer.length();
                    }
                } else {
                    chunk = argName;
                    result = parsedCmd.getLastChunkIndex();
                }
            } else {
                chunk = null;
            }

            if (valueCompleter != null) {
                if (chunk == null) {
                    // The user typed xxx=
                    // Complete with false if boolean
                    String parsedName = parsedCmd.getLastParsedPropertyName();
                    for (CommandArgument arg : allArgs) {
                        String argFullName = arg.getFullName();
                        if (argFullName.equals(parsedName)) {
                            if (!arg.isValueRequired()) {
                                candidates.add(Util.FALSE);
                                return result;
                            }
                        }
                    }
                }
                int valueResult = valueCompleter.complete(ctx,
                        chunk == null ? "" : chunk,
                        chunk == null ? 0 : chunk.length(), candidates);
                if (valueResult < 0) {
                    return valueResult;
                } else {
                    if(suggestionEqualsUserEntry(candidates, chunk, valueResult)) {
                        final CommandLineFormat format = parsedCmd.getFormat();
                        if(format != null) {
                            for (CommandArgument arg : allArgs) {
                                try {
                                    if(arg.canAppearNext(ctx)) {
                                        candidates.set(0, "" + format.getPropertySeparator());
                                        return buffer.length();
                                    }
                                } catch (CommandFormatException e) {
                                    e.printStackTrace();
                                    return result + valueResult;
                                }
                            }
                            candidates.set(0, format.getPropertyListEnd());
                            return buffer.length();
                        }
                    }
                    return result + valueResult;
                }
            }

            CommandArgument lastArg = null;
            // All property present means proposing end of list instead of property
            // separator when the last property is a boolean one.
            boolean allPropertiesPresent = true;
            // Lookup for the existence of a fully named property.
            // Doing so we will not mix properties that are prefix of other ones
            // e.g.: recursive and recursive-depth
            for (CommandArgument arg : allArgs) {
                try {
                    if (arg.canAppearNext(ctx)) {
                        allPropertiesPresent = false;
                    } else {
                        String argFullName = arg.getFullName();
                        if (argFullName.equals(chunk)) {
                            lastArg = arg;
                        }
                    }
                } catch (CommandFormatException e) {
                    e.printStackTrace();
                    return -1;
                }
            }
            boolean needNeg = false;
            for (CommandArgument arg : allArgs) {
                try {
                    if (arg.canAppearNext(ctx)) {
                        if (arg.getIndex() >= 0) {
                            CommandLineCompleter valCompl = arg.getValueCompleter();
                            if (valCompl != null) {
                                final String value = chunk == null ? "" : chunk;
                                valCompl.complete(ctx, value, value.length(), candidates);
                            }
                        } else {
                            String argFullName = arg.getFullName();
                            if (chunk == null
                                    || argFullName.startsWith(chunk)) {
                                /* The following complexity is due to cases like:
                                 recursive and recursive-depth. Both start with the same name
                                 but are of different types. Completion can't propose
                                 recursive-depth of !recursive has been typed.
                                 If the last property is not negated,
                                 we can add all properties with the same name.
                                 */
                                if (!parsedCmd.isLastPropertyNegated()) {
                                    candidates.add(argFullName);
                                } else // We can only add candidates that are of type boolean
                                 if (!arg.isValueRequired()) {
                                        candidates.add(argFullName);
                                    }
                            }
                            // Only add the not operator if the property is of type boolean
                            // and this property is not already negated.
                            if (!arg.isValueRequired()
                                    && !parsedCmd.isLastPropertyNegated()) {
                                needNeg = true;
                            }
                        }
                    }
                } catch (CommandFormatException e) {
                    e.printStackTrace();
                    return -1;
                }
            }

            // Propose not operator only after a property separator
            if (needNeg && parsedCmd.endsOnPropertySeparator()) {
                candidates.add(Util.NOT_OPERATOR);
            }

            if (lastArg != null) {
                if (lastArg.isValueRequired()) {
                    candidates.add(lastArg.getFullName() + "=");
                } else if (lastArg instanceof ArgumentWithoutValue) {
                    ArgumentWithoutValue argWithoutValue = (ArgumentWithoutValue) lastArg;
                    // If the last argument is exclusive, no need to add any separator
                    if (!argWithoutValue.isExclusive()) {
                        // Command argument without value have no completion.
                        // If more arguments can come, add an argument separator
                        // to make completion propose next argument
                        if (!allPropertiesPresent) {
                            CommandLineFormat format = parsedCmd.getFormat();
                            if (format != null && format.getPropertySeparator() != null) {
                                candidates.add(lastArg.getFullName()
                                        + format.getPropertySeparator());
                            }
                        }
                    }
                } else {
                    // We are completing implicit values for operation.
                    CommandLineFormat format = parsedCmd.getFormat();
                    // This is a way to optimise false value.
                    // Setting to true is useless, the property name is
                    // enough.
                    if (!parsedCmd.isLastPropertyNegated()) {
                        candidates.add("=" + Util.FALSE);
                    }
                    if (format != null && format.getPropertyListEnd() != null && format.getPropertyListEnd().length() > 0) {
                        candidates.add(format.getPropertyListEnd());
                        if (!allPropertiesPresent) {
                            candidates.add(format.getPropertySeparator());
                        }
                    }
                }
            }

            if (candidates.isEmpty()) {
                if (chunk == null && !parsedCmd.endsOnSeparator()) {
                    final CommandLineFormat format = parsedCmd.getFormat();
                    if (format != null && format.getPropertyListEnd() != null && format.getPropertyListEnd().length() > 0) {
                        candidates.add(format.getPropertyListEnd());
                    }
                }
            } else {
                Collections.sort(candidates);
            }
            return result;
        }

        if(parsedCmd.hasOperationName() || parsedCmd.endsOnAddressOperationNameSeparator()) {

            if(parsedCmd.getAddress().endsOnType()) {
                return -1;
            }
            final Collection<String> names = candidatesProvider.getOperationNames(ctx, parsedCmd.getAddress());
            if(names.isEmpty()) {
                return -1;
            }

            final String chunk = parsedCmd.getOperationName();
            if(chunk == null) {
                candidates.addAll(names);
            } else {
                for (String name : names) {
                    if (name.startsWith(chunk)) {
                        candidates.add(name);
                    }
                }
            }

            Collections.sort(candidates);
            if(parsedCmd.endsOnSeparator()) {
                return buffer.length();//parsedCmd.getLastSeparatorIndex() + 1;
            } else {
                if(chunk != null && candidates.size() == 1 && chunk.equals(candidates.get(0))
                        && parsedCmd.getFormat().getPropertyListStart().length() > 0) {
                    candidates.set(0, chunk + parsedCmd.getFormat().getPropertyListStart());
                }

                return parsedCmd.getLastChunkIndex();
            }
        }

        final OperationRequestAddress address = parsedCmd.getAddress();

        if(buffer.endsWith("..")) {
            return -1;
        }

        final String chunk;
        if (address.isEmpty() || parsedCmd.endsOnNodeSeparator()
                || parsedCmd.endsOnNodeTypeNameSeparator()
                || address.equals(ctx.getCurrentNodePath())) {
            chunk = null;
        } else if (address.endsOnType()) {
            chunk = address.getNodeType();
            address.toParentNode();
        } else { // address ends on node name
            chunk = address.toNodeType();
        }

        final Collection<String> names;
        if(address.endsOnType()) {
            names = candidatesProvider.getNodeNames(ctx, address);
        } else {
            names = candidatesProvider.getNodeTypes(ctx, address);
        }

        if(names.isEmpty()) {
            return -1;
        }

        if(chunk == null) {
            candidates.addAll(names);
        } else {
            for (String name : names) {
                if (name.startsWith(chunk)) {
                    candidates.add(name);
                }
            }
        }


        // get all characters after the last separator - including spaces and quotes - and parse it
        String lastSegment = "";
        if (parsedCmd.getSubstitutedLine().length() >= parsedCmd.getLastSeparatorIndex()) {
            lastSegment = parsedCmd.getSubstitutedLine().substring(parsedCmd.getLastSeparatorIndex() + 1);
        }
        SegmentParsingInitialState.SegmentParsingCallbackHandler parsedSegment = parseLastSegment(lastSegment);

        // offset to where the completion should inline its content
        int offset;

        if(candidates.size() == 1) {
            final String candidate = candidates.get(0);
            if (address.endsOnType()) { // completing node name
                if (chunk != null && chunk.equals(candidate)) {
                    // inline a '"' to terminate the quoted name.
                    if (parsedSegment.isOpenQuotes()) {
                        candidates.set(0, "\"");
                    } else {
                        // propose the common separators.
                        candidates.set(0, parsedCmd.getFormat().getAddressOperationSeparator());
                        candidates.add(parsedCmd.getFormat().getNodeSeparator());
                    }
                    return buffer.length();
                }
                // We are inlining the candidate, ends it with quotes if it is starting with quotes
                if (parsedSegment.isOpenQuotes()) {
                    String escapedCandidate = Util.escapeString(candidate, ESCAPE_SELECTOR_INSIDE_QUOTES);
                    offset = parsedSegment.getOffset() - 1; // decrementing one char because the leading quote is included in the candidate
                    candidates.set(0, "\"" + escapedCandidate + "\"");
                } else {
                    String escapedCandidate = Util.escapeString(candidate, ESCAPE_SELECTOR);
                    offset = parsedSegment.getOffset();
                    candidates.set(0, escapedCandidate);
                }
            } else { // completing node type
                if (chunk != null && chunk.equals(candidate)) {
                    // inline a '"' to terminate the quoted type.
                    if (parsedSegment.isOpenQuotes()) {
                        candidates.set(0, "\"");
                    } else {
                        // propose the type=name separator.
                        candidates.set(0, "=");
                    }
                    return buffer.length();
                }
                // We are inlining the candidate, ends it with quotes +'=' if it is starting with quotes
                if (parsedSegment.isOpenQuotes()) {
                    String escapedCandidate = Util.escapeString(candidate, ESCAPE_SELECTOR_INSIDE_QUOTES);
                    offset = parsedSegment.getOffset() - 1; // decrementing one char because the leading quote is included in the candidate
                    candidates.set(0, "\"" + escapedCandidate + "\"=");
                } else {
                    String escapedCandidate = Util.escapeString(candidate, ESCAPE_SELECTOR);
                    offset = parsedSegment.getOffset();
                    candidates.set(0, escapedCandidate + "=");
                }
            }
        } else { // multiple candidates
            if (parsedSegment.isOpenQuotes()) {
                Util.sortAndEscape(candidates, ESCAPE_SELECTOR_INSIDE_QUOTES);
            } else {
                Util.sortAndEscape(candidates, ESCAPE_SELECTOR);
            }
            offset = parsedSegment.getOffset();
        }

        return parsedCmd.getLastSeparatorIndex() + 1 + offset;
    }

    private SegmentParsingInitialState.SegmentParsingCallbackHandler parseLastSegment(String chunk) {
        SegmentParsingInitialState.SegmentParsingCallbackHandler handler = new SegmentParsingInitialState.SegmentParsingCallbackHandler();
        try {
            StateParser.parse(chunk, handler, SegmentParsingInitialState.INSTANCE, false);
        } catch (CommandFormatException e) {
            // this should not happen during non-strict parsing
            LOGGER.debug("Error when parsing last chunk of operation request", e);
        }
        return handler;
    }

    private boolean suggestionEqualsUserEntry(List<String> candidates, String chunk, int suggestionOffset) {
        if (chunk == null || candidates.size() != 1) {
            return false;
        }

        if (suggestionOffset > 0) {
            // user entry before suggestionOffset is always the same - compare only part after offset
            return chunk.substring(suggestionOffset).equals(candidates.get(0));
        } else {
            return chunk.equals(candidates.get(0));
        }
    }

    protected CommandLineCompleter getValueCompleter(CommandContext ctx, Iterable<CommandArgument> allArgs, final String argName) {
        CommandLineCompleter result = null;
        for (CommandArgument arg : allArgs) {
            if (arg.getFullName().equals(argName)) {
                return arg.getValueCompleter();
            } else if(arg.getIndex() == Integer.MAX_VALUE) {
                result = arg.getValueCompleter();
            }
        }
        return result;
    }

    protected CommandLineCompleter getValueCompleter(CommandContext ctx, Iterable<CommandArgument> allArgs, int index) {
        CommandLineCompleter maxIndex = null;
        for (CommandArgument arg : allArgs) {
            if (arg.getIndex() == index) {
                return arg.getValueCompleter();
            } else if(arg.getIndex() == Integer.MAX_VALUE) {
                maxIndex = arg.getValueCompleter();
            }
        }
        return maxIndex;
    }

    private int completeHeader(Map<String, OperationRequestHeader> headers,
            CommandContext ctx, ParsedCommandLine parsedCmd, String buffer,
            int cursor, List<String> candidates) {
        final OperationRequestHeader header = headers.get(parsedCmd.getLastHeaderName());
        if (header == null) {
            return -1;
        }
        final CommandLineCompleter headerCompleter = header.getCompleter();
        if (headerCompleter == null) {
            return -1;
        }

        int valueResult = headerCompleter.complete(ctx,
                buffer.substring(parsedCmd.getLastChunkIndex()), cursor, candidates);
        if (valueResult < 0) {
            return -1;
        }
        return parsedCmd.getLastChunkIndex() + valueResult;
    }
}
