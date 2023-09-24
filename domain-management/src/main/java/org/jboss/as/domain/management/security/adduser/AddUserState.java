/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import org.jboss.as.domain.management.logging.DomainManagementLogger;

/**
 * State to perform the actual addition to the discovered properties files.
 * <p/>
 * By this time ALL validation should be complete, this State will only fail for IOExceptions encountered
 * performing the actual writes.
 */
public class AddUserState extends UpdatePropertiesHandler implements State {

    private final StateValues stateValues;
    private final ConsoleWrapper theConsole;

    public AddUserState(ConsoleWrapper theConsole, final StateValues stateValues) {
        super(theConsole);
        this.theConsole = theConsole;
        this.stateValues = stateValues;
    }

    @Override
    public State execute() {
        final String password = stateValues.getPassword();
        State nextState;
        if (password == null) {
            // The user doesn't exist and the password is not provided !
            nextState = new ErrorState(theConsole, DomainManagementLogger.ROOT_LOGGER.noPasswordExiting(), null, stateValues);
        } else {
            nextState = update(stateValues);
        }

        return nextState;
    }

    @Override
    String consoleUserMessage(String filePath) {
        return DomainManagementLogger.ROOT_LOGGER.addedUser(stateValues.getUserName(), filePath);
    }

    @Override
    String consoleGroupsMessage(String filePath) {
        return DomainManagementLogger.ROOT_LOGGER.addedGroups(stateValues.getUserName(), stateValues.getGroups(), filePath);
    }

    @Override
    String errorMessage(String filePath, Throwable e) {
        return DomainManagementLogger.ROOT_LOGGER.unableToAddUser(filePath, e.getMessage());
    }
}
