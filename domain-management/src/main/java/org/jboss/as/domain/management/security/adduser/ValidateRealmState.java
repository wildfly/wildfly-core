/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import static org.jboss.as.domain.management.security.adduser.AddUser.DEFAULT_APPLICATION_REALM;
import static org.jboss.as.domain.management.security.adduser.AddUser.DEFAULT_MANAGEMENT_REALM;

import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.as.domain.management.security.adduser.AddUser.FileMode;

/**
 * State to perform some validation in the entered realm.
 *
 * Primarily this is just to warn users who have chosen a different realm name.
 *
 * This state is only expected to be used in interactive mode.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ValidateRealmState implements State {

    private final StateValues stateValues;
    private ConsoleWrapper theConsole;

    public ValidateRealmState(ConsoleWrapper theConsole, final StateValues stateValues) {
        this.theConsole = theConsole;
        this.stateValues = stateValues;
    }

    @Override
    public State execute() {
        String enteredRealm = stateValues.getRealm();
        if (enteredRealm.length() == 0) {
            return new ErrorState(theConsole, DomainManagementLogger.ROOT_LOGGER.realmMustBeSpecified(), new PromptRealmState(theConsole, stateValues), stateValues);
        }

        if (stateValues.getFileMode() != FileMode.UNDEFINED) {
            final String expectedRealm = stateValues.getFileMode() == FileMode.MANAGEMENT ? DEFAULT_MANAGEMENT_REALM
                    : DEFAULT_APPLICATION_REALM;

            if (expectedRealm.equals(enteredRealm) == false) {
                String message = DomainManagementLogger.ROOT_LOGGER.alternativeRealm(expectedRealm);
                String prompt = DomainManagementLogger.ROOT_LOGGER.realmConfirmation(enteredRealm) + " " + DomainManagementLogger.ROOT_LOGGER.yes() + "/" + DomainManagementLogger.ROOT_LOGGER.no() + "?";

                return new ConfirmationChoice(theConsole, message, prompt, new PromptNewUserState(theConsole, stateValues),
                        new PromptRealmState(theConsole, stateValues));

            }
        }

        return new PromptNewUserState(theConsole, stateValues);
    }

}
