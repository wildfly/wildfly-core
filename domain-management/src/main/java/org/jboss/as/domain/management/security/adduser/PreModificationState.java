/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.security.adduser;

import org.jboss.as.domain.management.logging.DomainManagementLogger;

/**
 * State to branch between adding and updating the user and outputting summary information if not running in silent mode.
 *
 */
public class PreModificationState implements State {

    private final ConsoleWrapper theConsole;
    private StateValues stateValues;

    public PreModificationState(final ConsoleWrapper theConsole, final StateValues stateValues) {
        this.theConsole = theConsole;
        this.stateValues = stateValues;
    }

    @Override
    public State execute() {
        final State continuingState;
        if (stateValues.isExistingUser()) {
            continuingState = new UpdateUser(theConsole, stateValues);
        } else {
            State addState = new AddUserState(theConsole, stateValues);

            if (stateValues.isSilentOrNonInteractive()) {
                continuingState = addState;
            } else {
                String message = DomainManagementLogger.ROOT_LOGGER.aboutToAddUser(stateValues.getUserName(), stateValues.getRealm());
                String prompt = DomainManagementLogger.ROOT_LOGGER.isCorrectPrompt() + " " + DomainManagementLogger.ROOT_LOGGER.yes() + "/" + DomainManagementLogger.ROOT_LOGGER.no() + "?";

                continuingState = new ConfirmationChoice(theConsole, message, prompt, addState, new PromptNewUserState(
                        theConsole, stateValues));
            }
        }

        return continuingState;
    }

}
