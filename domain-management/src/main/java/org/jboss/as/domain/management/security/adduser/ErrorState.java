/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import static org.jboss.as.domain.management.security.adduser.AddUser.NEW_LINE;

import org.jboss.as.domain.management.logging.DomainManagementLogger;

/**
 * State to report an error to the user, optionally a nextState can be supplied so the process can continue even though an
 * error has been reported.
 */
public class ErrorState implements State {

    private final State nextState;
    private final String errorMessage;
    private final StateValues stateValues;
    private ConsoleWrapper theConsole;

    public ErrorState(ConsoleWrapper theConsole, String errorMessage) {
        this(theConsole, errorMessage, null, null);
    }

    public ErrorState(ConsoleWrapper theConsole, String errorMessage, StateValues stateValues) {
        this(theConsole, errorMessage, null, stateValues);
    }

    public ErrorState(ConsoleWrapper theConsole, String errorMessage, State nextState) {
        this(theConsole, errorMessage, nextState, null);
    }

    public ErrorState(ConsoleWrapper theConsole, String errorMessage, State nextState, StateValues stateValues) {
        this.errorMessage = errorMessage;
        this.nextState = nextState;
        this.stateValues = stateValues;
        this.theConsole = theConsole;
    }

    public State execute() {
        boolean direct = theConsole.hasConsole();
        // Errors should be output in all modes.
        printf(NEW_LINE, direct);
        printf(" * ", direct);
        printf(DomainManagementLogger.ROOT_LOGGER.errorHeader(), direct);
        printf(" * ", direct);
        printf(NEW_LINE, direct);
        printf(errorMessage, direct);
        printf(NEW_LINE, direct);
        printf(NEW_LINE, direct);
        // Throw an exception if the mode is non-interactive and there's no next state.
        if ((stateValues != null && !stateValues.isInteractive()) && nextState == null) {
            throw new AddUserFailedException(errorMessage);
        }
        return nextState;
    }

    private void printf(final String message, final boolean direct) {
        if (direct) {
            System.err.print(message);
        } else {
            theConsole.printf(message);
        }
    }

}
