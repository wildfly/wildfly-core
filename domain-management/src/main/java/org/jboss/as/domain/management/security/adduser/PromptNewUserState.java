/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import org.jboss.as.domain.management.logging.DomainManagementLogger;

/**
 * State to prompt the user for the realm, username and password to use, this State can be called back to so allows for a
 * pre-defined realm and username to be used.
 */
public class PromptNewUserState implements State {
    private final StateValues stateValues;
    private ConsoleWrapper theConsole;

    public PromptNewUserState(ConsoleWrapper theConsole, final StateValues stateValues) {
        this.theConsole = theConsole;
        this.stateValues = stateValues;
    }

    @Override
    public State execute() {
        State continuingState = new ValidateUserState(theConsole, stateValues);
        if (stateValues.isSilentOrNonInteractive() == false) {
            stateValues.setPassword(null); // If interactive we want to be sure to capture this.

            /*
            * Prompt for username.
            */
            String existingUsername = stateValues.getUserName();
            String usernamePrompt = existingUsername == null ? DomainManagementLogger.ROOT_LOGGER.usernamePrompt() :
                    DomainManagementLogger.ROOT_LOGGER.usernamePrompt(existingUsername);
            theConsole.printf(usernamePrompt);
            String temp = theConsole.readLine(" : ");
            if (temp != null && temp.length() > 0) {
                existingUsername = temp;
            }
            // The user could have pressed Ctrl-D, in which case we do not use the default value.
            if (temp == null || existingUsername == null || existingUsername.length() == 0) {
                return new ErrorState(theConsole, DomainManagementLogger.ROOT_LOGGER.noUsernameExiting(), stateValues);
            }
            stateValues.setUserName(existingUsername);
        }

        return continuingState;
    }

}