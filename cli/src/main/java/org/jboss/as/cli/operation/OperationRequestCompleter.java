/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import org.jboss.as.cli.parsing.StateParser.SubstitutedLine;
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
                parsedOp.parseOperation(ctx.getCurrentNodePath(), buffer, ctx);
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

    private int completeHeaders(CommandContext ctx, ParsedCommandLine parsedCmd,
            OperationCandidatesProvider candidatesProvider,
            String buffer, int cursor, List<String> candidates) {
        final Map<String, OperationRequestHeader> headers = candidatesProvider.getHeaders(ctx);
        if (headers.isEmpty()) {
            return -1;
        }
        int result = buffer.length();
        if (parsedCmd.getLastHeaderName() != null) {
            if (buffer.endsWith(parsedCmd.getLastHeaderName())) {
                result = parsedCmd.getLastChunkIndex();
                for (String name : headers.keySet()) {
                    if (name.equals(parsedCmd.getLastHeaderName())) {
                        result = completeHeader(headers, ctx, parsedCmd, buffer, cursor, candidates);
                        break;
                    }
                    if (!parsedCmd.hasHeader(name) && name.startsWith(parsedCmd.getLastHeaderName())) {
                        candidates.add(name);
                    }
                }
            } else {
                result = completeHeader(headers, ctx, parsedCmd, buffer, cursor, candidates);
            }
        } else if (!parsedCmd.hasHeaders()) {
            candidates.addAll(headers.keySet());
        } else if (parsedCmd.endsOnHeaderSeparator()) {
            candidates.addAll(headers.keySet());
            for (ParsedOperationRequestHeader parsed : parsedCmd.getHeaders()) {
                candidates.remove(parsed.getName());
            }
        } else {
            final ParsedOperationRequestHeader lastParsedHeader = parsedCmd.getLastHeader();
            final OperationRequestHeader lastHeader = headers.get(lastParsedHeader.getName());
            if (lastHeader == null) {
                return -1;
            }
            final CommandLineCompleter headerCompleter = lastHeader.getCompleter();
            if (headerCompleter == null) {
                return -1;
            }
            result = headerCompleter.complete(ctx, buffer, cursor, candidates);
        }
        Collections.sort(candidates);
        return result;
    }

    private int completeOperationName(CommandContext ctx, ParsedCommandLine parsedCmd,
            OperationCandidatesProvider candidatesProvider,
            String buffer, List<String> candidates) {
        if (parsedCmd.getAddress().endsOnType()) {
            return -1;
        }
        final Collection<String> names = candidatesProvider.getOperationNames(ctx, parsedCmd.getAddress());
        if (names.isEmpty()) {
            return -1;
        }

        final String chunk = parsedCmd.getOperationName();
        if (chunk == null) {
            candidates.addAll(names);
        } else {
            for (String name : names) {
                if (name.startsWith(chunk)) {
                    candidates.add(name);
                }
            }
        }

        Collections.sort(candidates);
        if (parsedCmd.endsOnSeparator()) {
            return buffer.length();//parsedCmd.getLastSeparatorIndex() + 1;
        } else {
            if (chunk != null && candidates.size() == 1 && chunk.equals(candidates.get(0))
                    && parsedCmd.getFormat().getPropertyListStart().length() > 0) {
                candidates.set(0, chunk + parsedCmd.getFormat().getPropertyListStart());
            }

            return parsedCmd.getLastChunkIndex();
        }
    }

    private int completeAddress(CommandContext ctx, ParsedCommandLine parsedCmd,
            OperationCandidatesProvider candidatesProvider,
            String buffer, List<String> candidates) {
        final OperationRequestAddress address = parsedCmd.getAddress();

        if (buffer.endsWith("..")) {
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
        if (address.endsOnType()) {
            names = candidatesProvider.getNodeNames(ctx, address);
        } else {
            names = candidatesProvider.getNodeTypes(ctx, address);
        }

        if (names.isEmpty()) {
            return -1;
        }

        if (chunk == null) {
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

        if (candidates.size() == 1) {
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

    private int completeImplicitValueProperties(CommandContext ctx, String buffer,
            Collection<CommandArgument> allArgs, List<String> candidates) {
        try {
            for (CommandArgument arg : allArgs) {
                if (arg.canAppearNext(ctx)) {
                    if (!arg.isValueRequired()) {
                        candidates.add(arg.getDecoratedName());
                    }
                }
            }
            Collections.sort(candidates);
            return buffer.length();
        } catch (CommandFormatException e) {
            return -1;
        }
    }

    private int completeNoPropertiesProvided(CommandContext ctx, String buffer,
            Collection<CommandArgument> allArgs, List<String> candidates) {
        try {
            boolean needNeg = false;
            for (CommandArgument arg : allArgs) {
                if (arg.canAppearNext(ctx)) {
                    // Means an argument without a name, only applies to command argument.
                    // In this case call the value completer.
                    if (arg.getIndex() >= 0) {
                        final CommandLineCompleter valCompl = arg.getValueCompleter();
                        if (valCompl != null) {
                            valCompl.complete(ctx, "", 0, candidates);
                            // Values have been added as candidate.
                            // If there are some options to propose, they will be mixed
                            // with the values. That only applies to commands.
                        }
                    } else {
                        candidates.add(arg.getDecoratedName());
                        // Propose the '!' operator in case at least one of the arguments
                        // doesn't need a value (is a boolean).
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
        } catch (CommandFormatException e) {
            return -1;
        }
    }

    private int completeNoProperties(ParsedCommandLine parsedCmd, String buffer,
            List<String> candidates) {
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

    private int completeNoValueCompleter(CommandContext ctx, ParsedCommandLine parsedCmd,
            Collection<CommandArgument> allArgs, String argName,
            String buffer, List<String> candidates) {
        // Great chance tha the last character is '='
        // Only the user can provide a value. No completion.
        if (parsedCmd.endsOnSeparator()) {
            return -1;
        }

        // We are checking if some properties could be proposed.
        // If that is the case, we can't complete the value.
        for (CommandArgument arg : allArgs) {
            try {
                // Some more can come next, no completion.
                if (arg.canAppearNext(ctx) && !arg.getFullName().equals(argName)) {
                    return -1;
                }
            } catch (CommandFormatException e) {
                break;
            }
        }
        // No properties to propose, user wants to complete, propose the end of properties list ')' for operation, nothing for commands.
        final CommandLineFormat format = parsedCmd.getFormat();
        if (format != null && format.getPropertyListEnd() != null && format.getPropertyListEnd().length() > 0) {
            candidates.add(format.getPropertyListEnd());
        }
        return buffer.length();
    }

    private int completeWithValueCompleter(CommandContext ctx, ParsedCommandLine parsedCmd,
            Collection<CommandArgument> allArgs, String argName,
            String buffer, List<String> candidates, String chunk, int result,
            CommandLineCompleter valueCompleter) {
        if (chunk == null) {// Special case of an implicit value with a value separtor.
            // In this case propose false.
            // The user typed xxx=
            // Complete with false if boolean
            for (CommandArgument arg : allArgs) {
                String argFullName = arg.getFullName();
                if (argFullName.equals(argName)) {
                    if (!arg.isValueRequired()) {
                        candidates.add(Util.FALSE);
                        return buffer.length();
                    }
                }
            }
        }
        // Call the value completer.
        // Completion is done with substituted line, so returned offset
        // is possibly wrong.
        // Some completers are reseting the CommandContext referenced parsed
        // command. We need to keep a local reference to substitution
        // to compute substitution index after completion occured.
        SubstitutedLine substitutions = parsedCmd.getSubstitutions();

        final String normalizedChunk = chunk == null ? "" : chunk;
        int valueResult = valueCompleter.complete(ctx, normalizedChunk, normalizedChunk.length(), candidates);
        // No proposition.
        if (valueResult < 0) {
            return valueResult;
        } else {
            // Implies a single candidate to inline, the value is complete.
            // propose the property separator if more properties to come
            // or the propertyListEnd if no more properties.
            if (suggestionEqualsUserEntry(candidates, chunk, valueResult)|| areIncludedCandidatesForSpecificValueTypes(candidates)) {
                final CommandLineFormat format = parsedCmd.getFormat();
                if (format != null) {
                    for (CommandArgument arg : allArgs) {
                        try {
                            if (arg.canAppearNext(ctx)) {
                                candidates.add("" + format.getPropertySeparator());
                                return buffer.length();
                            }
                        } catch (CommandFormatException e) {
                            return -1;
                        }
                    }
                    // inline the end of properties.
                    // at the end of the input.
                    if((buffer.charAt(buffer.length() - 1))!='='){
                        candidates.add(format.getPropertyListEnd());
                    }
                    return buffer.length();
                }
            }
            //WFCORE-3190 ignore trailing spaces after the cursor position
            int trailOffset = substitutions.getSubstitued().substring(result).length() - normalizedChunk.length();

            return result + valueResult + trailOffset;
        }
    }

    private int completeArgumentValueAndPropertyNames(CommandContext ctx, ParsedCommandLine parsedCmd,
            Collection<CommandArgument> allArgs, List<String> candidates, String chunk, int result) {
        // lastArg will be not null if its name is equals to the last typed one.
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
                } else if (arg.getFullName().equals(chunk)) {
                    lastArg = arg;
                }
            } catch (CommandFormatException e) {
                return -1;
            }
        }
        boolean needNeg = false;

        // We must first check that we have no matches for option names
        // prior to call the argument (option with no value) value completers
        // Otherwise we can enter the case where value completer would complete
        // an option name. That is possible because value completer parsers are not strict.
        // for example: ls --hel<TAB> ==> --hel will be completed by the node-path completer
        // and this can give strange completion result.
        boolean optionMatch = false;
        for (CommandArgument arg : allArgs) {
            try {
                if (arg.canAppearNext(ctx)) {
                    if (arg.getIndex() < 0) {
                        String argFullName = arg.getFullName();
                        if (chunk != null
                                && argFullName.startsWith(chunk)) {
                            if (!parsedCmd.isLastPropertyNegated()) {
                                optionMatch = true;
                                break;
                            } else // We can only add candidates that are of type boolean
                             if (!arg.isValueRequired()) {
                                    optionMatch = true;
                                    break;
                                }
                        }
                    }
                }
            } catch (CommandFormatException e) {
                return -1;
            }
        }

        for (CommandArgument arg : allArgs) {
            try {
                if (arg.canAppearNext(ctx)) {
                    if (arg.getIndex() >= 0) {
                        if (optionMatch) {
                            continue;
                        }
                        CommandLineCompleter valCompl = arg.getValueCompleter();
                        if (valCompl != null) {
                            final String value = chunk == null ? "" : chunk;
                            valCompl.complete(ctx, value, value.length(), candidates);
                            // Values have been added as candidate.
                            // If there are some options to propose, they will be mixed
                            // with the values. That only applies to commands.
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
                                candidates.add(arg.getDecoratedName());
                            } else // We can only add candidates that are of type boolean
                            {
                                if (!arg.isValueRequired()) {
                                    candidates.add(arg.getDecoratedName());
                                }
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
                    result = recalculateResult(parsedCmd, candidates, chunk, result);
                }
                if (format != null && format.getPropertyListEnd() != null && format.getPropertyListEnd().length() > 0) {
                    candidates.add(format.getPropertyListEnd());
                    result = recalculateResult(parsedCmd, candidates, chunk, result);
                    if (!allPropertiesPresent) {
                        candidates.add(format.getPropertySeparator());
                        result = recalculateResult(parsedCmd, candidates, chunk, result);
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            if (chunk == null && !parsedCmd.endsOnSeparator()) {
                final CommandLineFormat format = parsedCmd.getFormat();
                if (format != null && format.getPropertyListEnd() != null && format.getPropertyListEnd().length() > 0) {
                    candidates.add(format.getPropertyListEnd());
                    result = recalculateResult(parsedCmd, candidates, chunk, result);
                }
            }
        } else {
            Collections.sort(candidates);
        }
        return result;
    }

    private int recalculateResult(ParsedCommandLine parsedCmd, List<String> candidates, String chunk, int result) {
        if (candidates.size() == 1 && chunk != null) {
            // Move the offset to the end of the line, since the name of the last property is completely specified
            // and there are no other candidates
            result = parsedCmd.getLastChunkIndex() + chunk.length();
        }
        return result;
    }

    private int completeProperties(CommandContext ctx, ParsedCommandLine parsedCmd,
            OperationCandidatesProvider candidatesProvider,
            String buffer, List<String> candidates) {
        // Invalid case of no operation name provided.
        if (!parsedCmd.hasOperationName()) {
            return -1;
        }
        // Retrieve all operation arguments from remote server.
        final Collection<CommandArgument> allArgs = candidatesProvider.getProperties(ctx, parsedCmd.getOperationName(), parsedCmd.getAddress());

        // No argument/option for this operation/command.
        if (allArgs.isEmpty()) {
            return completeNoProperties(parsedCmd, buffer, candidates);
        }

        // Retrieve properties with implicit values only.
        // The lastcharacter typed is '!', we need a property name.
        if (parsedCmd.endsOnNotOperator()) {
            return completeImplicitValueProperties(ctx, buffer, allArgs, candidates);
        }

        // No properties have been already set.
        if (!parsedCmd.hasProperties()) {
            return completeNoPropertiesProvided(ctx, buffer, allArgs, candidates);
        }

        // We should complete at the end of the input.
        int result = buffer.length();

        // chunk is the last piece of text the user typed.
        String chunk = null;

        // We are expecting to complete a property/option name,
        // a property/option value or an argument value.
        if (!parsedCmd.endsOnPropertySeparator()) {
            final String argName = parsedCmd.getLastParsedPropertyName();
            final String argValue = parsedCmd.getLastParsedPropertyValue();

            // Complete a value.
            if (argValue != null || parsedCmd.endsOnPropertyValueSeparator()) {
                result = parsedCmd.getLastChunkIndex();
                if (parsedCmd.endsOnPropertyValueSeparator()) {
                    ++result;// it enters on '='
                }
                chunk = argValue;
                CommandLineCompleter valueCompleter = null;
                if (argName != null) { // Retrieve the completer based on name
                    valueCompleter = getValueCompleter(ctx, allArgs, argName);
                } else { // retrieve the completer based on argument index.
                    valueCompleter = getValueCompleter(ctx, allArgs, parsedCmd.getOtherProperties().size() - 1);
                }

                // No value completer for this property.
                if (valueCompleter == null) {
                    return completeNoValueCompleter(ctx, parsedCmd, allArgs, argName, buffer, candidates);
                } else { // Complete with a value Completer.
                    return completeWithValueCompleter(ctx, parsedCmd, allArgs, argName, buffer, candidates,
                            chunk, result, valueCompleter);
                }
            } else { // Will complete possibly a name.
                chunk = argName;
                // Name completion is inlined at the begining of the name, not at
                // the end of the user input.
                result = parsedCmd.getLastChunkIndex();
            }
        }

        // At this point complete a name, propose remaining properties or complete an
        // unammed argument value (apply to commands only).
        return completeArgumentValueAndPropertyNames(ctx, parsedCmd, allArgs,
                candidates, chunk, result);
    }

    // Complete both operations and commands.
    protected int complete(CommandContext ctx, ParsedCommandLine parsedCmd, OperationCandidatesProvider candidatesProvider, final String buffer, int cursor, List<String> candidates) {

        if(parsedCmd.isRequestComplete()) {
            return -1;
        }

        // Headers completion
        if(parsedCmd.endsOnHeaderListStart() || parsedCmd.hasHeaders()) {
            return completeHeaders(ctx, parsedCmd, candidatesProvider, buffer, cursor, candidates);
        }

        // Completed.
        if(parsedCmd.endsOnPropertyListEnd()) {
            return buffer.length();
        }

        // Complete properties
        if (parsedCmd.hasProperties() || parsedCmd.endsOnPropertyListStart()
                || parsedCmd.endsOnNotOperator()) {
            return completeProperties(ctx, parsedCmd, candidatesProvider, buffer, candidates);
        }

        // Complete Operation name
        if (parsedCmd.hasOperationName() || parsedCmd.endsOnAddressOperationNameSeparator()) {
            return completeOperationName(ctx, parsedCmd, candidatesProvider, buffer, candidates);
        }

        // Finally Complete address
        return completeAddress(ctx, parsedCmd, candidatesProvider, buffer, candidates);
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

        if (suggestionOffset > 0 && candidates.get(0)!="") {
            // user entry before suggestionOffset is always the same - compare only part after offset
            return chunk.substring(suggestionOffset).equals(candidates.get(0));
        } else {
            if(chunk.equals(candidates.get(0))){
                candidates.clear();
                return true;
            }
            return false;
        }
    }

    boolean areIncludedCandidatesForSpecificValueTypes(List<String> candidates){
        if(candidates.contains("[") || candidates.contains(".")){
            return true;
        }else if(candidates.contains("")){
            candidates.remove("");
            return true;
        }
        return false;
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
