/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

import org.jboss.as.cli.CommandFormatException;

/**
 *
 * @author Alexey Loubyansky
 */
public interface ParsingContext {

    /**
     * The complete string being parsed.
     *
     * @return  the complete string being parsed
     */
    String getInput();

    /**
     * The current state.
     *
     * @return current state
     */
    ParsingState getState();

    /**
     * Enters the state passed in as the argument which then becomes the current state.
     *
     * @param state  the state to enter
     * @throws CommandFormatException  in case something went wrong
     */
    void enterState(ParsingState state) throws CommandFormatException;

    /**
     * Leaves the current state and and returns it.
     *
     * @return  the state that's been left
     * @throws CommandFormatException  in case something went wrong
     */
    ParsingState leaveState() throws CommandFormatException;

    /**
     * Leaves the current state and then enters it again.
     *
     * @throws CommandFormatException  in case something went wrong
     */
    void reenterState() throws CommandFormatException;

    /**
     * The callback handler used for current parsing.
     *
     * @return  the callback handler used for the current parsing
     */
    ParsingStateCallbackHandler getCallbackHandler();

    /**
     * The character at the current location in the input string.
     *
     * @return  the character at the current location in the input string
     */
    char getCharacter();

    /**
     * The current location in the input string.
     *
     * @return  the current location in the input string
     */
    int getLocation();

    /**
     * Checks whether the end of the input string has been reached.
     *
     * @return  true if the end of the input stream has been reached
     */
    boolean isEndOfContent();

    /**
     * Advances the current location by skipping the specified number of characters.
     *
     * @param offset  the number of characters to skip
     * @throws IndexOutOfBoundsException  if the new location exceeds the input string length
     */
    void advanceLocation(int offset) throws IndexOutOfBoundsException;

    /**
     * Indicates whether handlers should complain by throwing exceptions
     * in case of issues or be forgiving where possible and there is
     * a reason to be.
     *
     * @return  true if the parser is in the strict parsing mode,
     * otherwise - false.
     */
    boolean isStrict();

    /**
     * Returns the exception if there was one during parsing or null
     * if the line was parsed successfully.
     *
     * @return  the exception if there was one during parsing, otherwise null.
     */
    CommandFormatException getError();

    /**
     * Sets the error indicating that there was a problem
     * during parsing.
     * If the handler chose not to throw the exception and terminate
     * the parsing process immediately, then handling subsequent callbacks
     * may result subsequent errors. This method should set the error
     * only once (the first call) and ignore the subsequent ones.
     *
     * @param e  the error
     */
    void setError(CommandFormatException e);

    /**
     * Replaces system property specified with ${xxx} fomrat or a local variable
     * whose name is prefixed with '$'.
     * After the property or the variable has been replaced with its actual value,
     * the parsing continues from the same location but with the value resolved.
     *
     * @param exceptionIfNotResolved  whether an exception should be thrown
     *                                in case the property or the variable couldn't
     *                                be resolved or should it continue unnoticed
     *
     * @throws CommandFormatException
     */
    void resolveExpression(boolean systemProperty, boolean exceptionIfNotResolved) throws UnresolvedExpressionException;

    /**
     * This method is called after a backslash character is met.
     * In case the backslash and the following character (if any) are recognized
     * as a special character (e.g. \t, \b, \n, \r, \f), they are replaced
     * with the special character they signify.
     * Otherwise, the method returns w/o any effects on the input sequence.
     *
     * @return  true if the character was recognized as a special one and was
     * replaced, false otherwise
     */
    boolean replaceSpecialChars();

    /**
     * Indicates that the passed in the argument character is expected
     * further the line. This is useful for special characters like
     * brackets, braces, etc.
     * This method can be called multiple times during parsing. Each
     * invocation will push the current look-for character into the
     * look-for character stack.
     * Once the look-for character has been met, the met(char ch)
     * method must be called to pop the met character from the stack.
     *
     * @param ch  the character to look for
     */
    void lookFor(char c);

    /**
     * This is a convenient safe method which checks whether the passed in
     * character is the current look-for one. If it is then the parser
     * will consider that it's been met and will pop from the look-for stack
     * and will return true to indicate that.
     * If it's not then the method will simply return false.
     *
     * @param c  the character that should be checked and met
     * @return  if the character was in fact the current look-for one, false otherwise
     */
    boolean meetIfLookedFor(char c);

    /**
     * Checks whether the character is the one the parser is looking for.
     *
     * @param c  the character to check
     * @return  true if it's the current look-for character, false otherwise
     */
    boolean isLookingFor(char c);

    /**
     * Indicates that a control character temporarily shouldn't be treated
     * as a control but a usual content one.
     *
     * @param c  control character to deactivate
     */
    void deactivateControl(char c);

    /**
     * Activates a control character.
     * It has effect if the character has previously been deactivated.
     * Invoking this method for an active control character has no effect.
     *
     * @param c  control character to activate
     */
    void activateControl(char c);

    /**
     * Checks whether deactivateControl(c) was called for the character
     * and the character is still not active.
     *
     * @param c  the character to check
     * @return  true if the character was deactivated and still is,
     * otherwise - false
     */
    boolean isDeactivated(char c);
}
