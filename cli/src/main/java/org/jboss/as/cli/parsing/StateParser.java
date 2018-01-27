/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.parsing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.util.CLIExpressionResolver;


/**
 *
 * @author Alexey Loubyansky
 */
public class StateParser {

    private final DefaultParsingState initialState = new DefaultParsingState("INITIAL");

    public void addState(char ch, ParsingState state) {
        initialState.enterState(ch, state);
    }

    /**
     * Returns the string which was actually parsed with all the substitutions
     * performed. NB: No CommandContext being provided, variables can't be
     * resolved. variables should be already resolved when calling this parse
     * method.
     */
    public String parse(String str, ParsingStateCallbackHandler callbackHandler) throws CommandFormatException {
        return parse(str, callbackHandler, initialState);
    }

    /**
     * Returns the string which was actually parsed with all the substitutions
     * performed. NB: No CommandContext being provided, variables can't be
     * resolved. variables should be already resolved when calling this parse
     * method.
     */
    public static String parse(String str, ParsingStateCallbackHandler callbackHandler, ParsingState initialState) throws CommandFormatException {
        return parseLine(str, callbackHandler, initialState).getSubstitued();
    }

    /**
     * Returns the string which was actually parsed with all the substitutions
     * performed. NB: No CommandContext being provided, variables can't be
     * resolved. variables should be already resolved when calling this parse
     * method.
     */
    public static SubstitutedLine parseLine(String str, ParsingStateCallbackHandler callbackHandler, ParsingState initialState) throws CommandFormatException {
        return parseLine(str, callbackHandler, initialState, true);
    }

    /**
     * Returns the string which was actually parsed with all the substitutions
     * performed. NB: No CommandContext being provided, variables can't be
     * resolved. variables should be already resolved when calling this parse
     * method.
     */
    public static String parse(String str, ParsingStateCallbackHandler callbackHandler, ParsingState initialState, boolean strict) throws CommandFormatException {
        return parseLine(str, callbackHandler, initialState, strict).getSubstitued();
    }

    /**
     * Returns the string which was actually parsed with all the substitutions
     * performed. NB: No CommandCOntext being provided, variables can't be
     * resolved. variables should be already resolved when calling this parse
     * method.
     */
    public static SubstitutedLine parseLine(String str, ParsingStateCallbackHandler callbackHandler, ParsingState initialState, boolean strict) throws CommandFormatException {
        return parseLine(str, callbackHandler, initialState, strict, null);
    }

    /**
     * Returns the string which was actually parsed with all the substitutions
     * performed
     */
    public static SubstitutedLine parseLine(String str, ParsingStateCallbackHandler callbackHandler, ParsingState initialState, CommandContext ctx) throws CommandFormatException {
        return parseLine(str, callbackHandler, initialState, true, ctx);
    }


    /**
     * Returns the string which was actually parsed with all the substitutions performed
     */
    public static String parse(String str, ParsingStateCallbackHandler callbackHandler, ParsingState initialState,
            boolean strict, CommandContext ctx) throws CommandFormatException {
        return parseLine(str, callbackHandler, initialState, strict, ctx).getSubstitued();
    }

    public static SubstitutedLine parseLine(String str, ParsingStateCallbackHandler callbackHandler, ParsingState initialState,
            boolean strict, CommandContext ctx) throws CommandFormatException {
        return parseLine(str, callbackHandler, initialState, strict, false, ctx);
    }

    public static SubstitutedLine parseLine(String str, ParsingStateCallbackHandler callbackHandler, ParsingState initialState,
            boolean strict, boolean disableResolutionException, CommandContext ctx) throws CommandFormatException {

        try {
            return doParse(str, callbackHandler, initialState, strict, disableResolutionException, ctx);
        } catch (CommandFormatException e) {
            throw e;
        } catch (Throwable t) {
            throw new CommandFormatException("Failed to parse '" + str + "'", t);
        }
    }

    /**
     * Returns the string which was actually parsed with all the substitutions performed
     */
    protected static SubstitutedLine doParse(String str, ParsingStateCallbackHandler callbackHandler, ParsingState initialState,
            boolean strict, boolean disableResolutionException, CommandContext cmdCtx) throws CommandFormatException {

        if (str == null || str.isEmpty()) {
            return new SubstitutedLine(str);
        }

        ParsingContextImpl ctx = new ParsingContextImpl();
        ctx.initialState = initialState;
        ctx.callbackHandler = callbackHandler;
        ctx.input = str;
        ctx.strict = strict;
        ctx.cmdCtx = cmdCtx;
        ctx.disableResolutionException = disableResolutionException;

        ctx.substitued.substitued = ctx.parse();
        return ctx.substitued;
    }

    public static class SubstitutedLine {

        private final List<Substitution> substitutions = new ArrayList<>();
        private String substitued;
        private int currentOriginalIndex;

        SubstitutedLine() {
        }

        SubstitutedLine(String str) {
            substitued = str;
        }

        public String getSubstitued() {
            return substitued;
        }

        public List<Substitution> getSubstitions() {
            return Collections.unmodifiableList(substitutions);
        }

        public int getOriginalOffset(int substituedOffset) {
            if (substitutions.isEmpty()) {
                return substituedOffset;
            }
            int delta = 0;
            for (Substitution sub : getSubstitions()) {
                if (sub.substitutionIndex > substituedOffset) {
                    break;
                } else {
                    delta += sub.original.length() - sub.substitution.length();
                }
            }
            return substituedOffset + delta;
        }

        public int getSubstitutedOffset(int originalOffset) {
            if (substitutions.isEmpty()) {
                return originalOffset;
            }
            int delta = 0;
            for (Substitution sub : getSubstitions()) {
                if (sub.substitutionIndex > originalOffset) {
                    break;
                } else {
                    delta += sub.substitution.length() - sub.original.length();
                }
            }
            return originalOffset + delta;
        }

        private void add(String original, String substitution, int location) {
            //compute original index;
            int orig = location - currentOriginalIndex;
            currentOriginalIndex += substitution.length() - original.length();
            substitutions.add(new Substitution(original, substitution, location, orig));
        }

    }

    public static class Substitution {

        private final String original;
        private final String substitution;
        private final int substitutionIndex;
        private final int originalIndex;

        private Substitution(String original, String substitution, int substitutionIndex, int originalIndex) {
            this.original = original;
            this.substitution = substitution;
            this.substitutionIndex = substitutionIndex;
            this.originalIndex = originalIndex;
        }

    }

    static class ParsingContextImpl implements ParsingContext {

        private final Deque<ParsingState> stack = new ArrayDeque<ParsingState>();

        String input;
        String originalInput;
        int location;
        char ch;
        ParsingStateCallbackHandler callbackHandler;
        ParsingState initialState;
        boolean strict;
        boolean disableResolutionException;
        CommandFormatException error;
        CommandContext cmdCtx;
        private final SubstitutedLine substitued = new SubstitutedLine();
        private final Deque<Character> lookFor = new ArrayDeque<Character>();
        /** to not meet the same character at the same position multiple times */
        int lastMetLookForIndex = -1;
        private char deactivated;

        String parse() throws CommandFormatException {

            ch = input.charAt(0);
            originalInput = input;
            location = 0;

            initialState.getEnterHandler().handle(this);

            while (location < input.length()) {
                ch = input.charAt(location);
                final CharacterHandler handler = getState().getHandler(ch);
                handler.handle(this);
                ++location;
            }

            ParsingState state = getState();
            while(state != initialState) {
                state.getEndContentHandler().handle(this);
                leaveState();
                state = getState();
            }
            initialState.getEndContentHandler().handle(this);
            initialState.getLeaveHandler().handle(this);
            return input;
        }

        @Override
        public void resolveExpression(boolean systemProperty, boolean exceptionIfNotResolved)
            throws UnresolvedExpressionException {
            final int inputLength = input.length();
            if(inputLength - location < 2) {
                return;
            }
            final char firstChar = input.charAt(location);
            if(firstChar == '$') {
                if (input.charAt(location + 1) == '{') {
                    if (systemProperty) {
                        input = CLIExpressionResolver.resolveProperty(input, location, exceptionIfNotResolved);
                        ch = input.charAt(location);
                    }
                } else {
                    substituteVariable(exceptionIfNotResolved);
                }
            } else if(firstChar == '`') {
                substituteCommand(exceptionIfNotResolved);
            }
        }

        private void substituteCommand(boolean exceptionIfNotResolved) throws CommandSubstitutionException {
            if(location + 1 == input.length()) {
                throw new CommandSubstitutionException("", "Command is missing after `");
            }
            final int endQuote = firstNotEscaped('`', location + 1);
            if(endQuote - location <= 1) {
                throw new CommandSubstitutionException(input.substring(location + 1),
                        "Closing ` is missing for " +
                        input.substring(location, Math.min(location + 5, input.length())) + "...");
            }

            final String cmd = input.substring(location + 1, endQuote);
            final String resolved = Util.getResult(cmdCtx, cmd);

            final StringBuilder buf = new StringBuilder(input.length() - cmd.length() - 2 + resolved.length());
            buf.append(input.substring(0, location)).append(resolved);
            if (endQuote < input.length() - 1) {
                buf.append(input.substring(endQuote + 1));
            }
            input = buf.toString();
            ch = input.charAt(location);
        }

        private int firstNotEscaped(char ch, int start) {
            final int index = input.indexOf(ch, start);
            if(index < 0) {
                return index;
            }

            // make sure ch is not escape
            if(input.charAt(index - 1) == '\\') {
                int i = index - 2;
                boolean escaped = true;
                while(i - start >= 0 && input.charAt(i) == '\\') {
                    --i;
                    escaped = !escaped;
                }
                if(escaped) {
                    if(index + 1 < input.length() - 1) {
                        return firstNotEscaped(ch, index + 1);
                    }
                    return -1;
                }
            }
            return index;
        }

        private void substituteVariable(boolean exceptionIfNotResolved) throws UnresolvedVariableException {
            int endIndex = location + 1;
            char c = input.charAt(endIndex);
            if(endIndex >= input.length() || !(Character.isJavaIdentifierStart(c) && c != '$')) {
                // simply '$'
                return;
            }
            while(++endIndex < input.length()) {
                c = input.charAt(endIndex);
                if(!(Character.isJavaIdentifierPart(c) && c != '$')) {
                    break;
                }
            }

            final String name = input.substring(location+1, endIndex);
            final String value = cmdCtx == null ? null : cmdCtx.getVariable(name);
            if(value == null) {
                if (exceptionIfNotResolved && !disableResolutionException) {
                    throw new UnresolvedVariableException(name, "Unrecognized variable " + name);
                }
            } else {
                substitued.add("$" + name, value, location);
                StringBuilder buf = new StringBuilder(input.length() - name.length() + value.length());
                buf.append(input.substring(0, location)).append(value);
                if (endIndex < input.length()) {
                    buf.append(input.substring(endIndex));
                }
                input = buf.toString();
                ch = input.charAt(location);
            }
        }

        @Override
        public boolean replaceSpecialChars() {
            if(location == 0) {
                return false;
            }
            if(input.charAt(location - 1) != '\\') {
                return false;
            }
            switch(ch) {
                case 'n':
                    ch = '\n';
                    break;
                case 't':
                    ch = '\t';
                    break;
                case 'b':
                    ch = '\b';
                    break;
                case 'r':
                    ch = '\r';
                    break;
                case 'f':
                    ch = '\f';
                    break;
                default:
                    return false;
            }
            return true;
        }

        @Override
        public boolean isStrict() {
            return strict;
        }

        @Override
        public ParsingState getState() {
            return stack.isEmpty() ? initialState : stack.peek();
        }

        @Override
        public void enterState(ParsingState state) throws CommandFormatException {
            stack.push(state);
            callbackHandler.enteredState(this);
            state.getEnterHandler().handle(this);
        }

        @Override
        public ParsingState leaveState() throws CommandFormatException {
            stack.peek().getLeaveHandler().handle(this);
            callbackHandler.leavingState(this);
            ParsingState pop = stack.pop();
            if(!stack.isEmpty()) {
                stack.peek().getReturnHandler().handle(this);
            } else {
                initialState.getReturnHandler().handle(this);
            }
            return pop;
        }

        @Override
        public ParsingStateCallbackHandler getCallbackHandler() {
            return callbackHandler;
        }

        @Override
        public char getCharacter() {
            return ch;
        }

        @Override
        public int getLocation() {
            return location;
        }

        @Override
        public void reenterState() throws CommandFormatException {
            callbackHandler.leavingState(this);
            ParsingState state = stack.peek();
            state.getLeaveHandler().handle(this);
            callbackHandler.enteredState(this);
            state.getEnterHandler().handle(this);
        }

        @Override
        public boolean isEndOfContent() {
            return location >= input.length();
        }

        @Override
        public String getInput() {
            return input;
        }

        @Override
        public void advanceLocation(int offset) throws IndexOutOfBoundsException {
            if(isEndOfContent()) {
                throw new IndexOutOfBoundsException("Location=" + location + ", offset=" + offset + ", length=" + input.length());
            }

            location += offset;
            if(location < input.length()) {
                ch = input.charAt(location);
            }
        }

        @Override
        public CommandFormatException getError() {
            return error;
        }

        @Override
        public void setError(CommandFormatException e) {
            if(error == null) {
                error = e;
            }
        }

        @Override
        public void lookFor(char ch) {
            lookFor.push(ch);
        }

        @Override
        public boolean meetIfLookedFor(char ch) {
            if(lastMetLookForIndex == location || lookFor.isEmpty() || lookFor.peek() != ch) {
                return false;
            }
            lookFor.pop();
            lastMetLookForIndex = location;
            return true;
        }

        @Override
        public boolean isLookingFor(char c) {
            return !lookFor.isEmpty() && lookFor.peek() == c;
        }

        @Override
        public void deactivateControl(char c) {
            if(deactivated != '\u0000') {
                // just not to use java.util.Set when only '=' is expected
                // to be deactivated at the moment...
                throw new IllegalStateException(
                        "Current implementation supports only one deactivated character at a time.");
            }
            deactivated = c;
        }

        @Override
        public void activateControl(char c) {
            if(deactivated == c) {
                deactivated = '\u0000';
            }
        }

        @Override
        public boolean isDeactivated(char c) {
            return deactivated == c;
        }
    }
}
