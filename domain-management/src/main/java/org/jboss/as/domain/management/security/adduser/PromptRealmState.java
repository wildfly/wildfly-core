/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import static org.jboss.as.domain.management.security.adduser.AddUser.NEW_LINE;

import org.jboss.as.domain.management.logging.DomainManagementLogger;

/**
 * State to prompt the user to choose the name of the realm.
 *
 * For most users the realm should not be modified as it is dependent on being in sync with the core configuration. At a later
 * point it may be possible to split the realm name definition out of the core configuration.
 *
 * This state is only expected to be called when running in interactive mode.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class PromptRealmState implements State {

    private final StateValues stateValues;
    private ConsoleWrapper theConsole;

    public PromptRealmState(ConsoleWrapper theConsole, final StateValues stateValues) {
        this.theConsole = theConsole;
        this.stateValues = stateValues;
    }

    @Override
    public State execute() {
        theConsole.printf(NEW_LINE);
        theConsole.printf(DomainManagementLogger.ROOT_LOGGER.enterNewUserDetails());
        theConsole.printf(NEW_LINE);

        /*
         * Prompt for realm.
         */
        String existingRealm = stateValues.getRealm();
        if (existingRealm == null) {
            existingRealm = "";
        }

        switch (stateValues.getRealmMode()) {
            case DISCOVERED:
                theConsole.printf(DomainManagementLogger.ROOT_LOGGER.discoveredRealm(existingRealm));
                theConsole.printf(NEW_LINE);

                return new PromptNewUserState(theConsole, stateValues);
            case USER_SUPPLIED:
                theConsole.printf(DomainManagementLogger.ROOT_LOGGER.userSuppliedRealm(existingRealm));
                theConsole.printf(NEW_LINE);

                return new PromptNewUserState(theConsole, stateValues);
            default:
                theConsole.printf(DomainManagementLogger.ROOT_LOGGER.realmPrompt(existingRealm));
                String temp = theConsole.readLine(" : ");
                if (temp == null) {
                    /*
                     * This will return user to the command prompt so add a new line to ensure the command prompt is on the next
                     * line.
                     */
                    theConsole.printf(NEW_LINE);
                    return null;
                }
                if (temp.length() > 0 || stateValues.getRealm() == null) {
                    stateValues.setRealm(temp);
                }

                return new ValidateRealmState(theConsole, stateValues);
        }
    }

}
