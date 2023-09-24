/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import org.jboss.as.domain.management.logging.DomainManagementLogger;

/**
 * Describe the purpose
 *
 * @author <a href="mailto:flemming.harms@gmail.com">Flemming Harms</a>
 */
public class UpdateUser extends UpdatePropertiesHandler implements State {

    private final StateValues stateValues;

    public UpdateUser(ConsoleWrapper theConsole, final StateValues stateValues) {
        super(theConsole);
        this.stateValues = stateValues;
    }

    @Override
    public State execute() {
        return update(stateValues);
    }

    @Override
    String consoleUserMessage(String fileName) {
        return DomainManagementLogger.ROOT_LOGGER.updateUser(stateValues.getUserName(), fileName);
    }

    @Override
    String consoleGroupsMessage(String fileName) {
        return DomainManagementLogger.ROOT_LOGGER.updatedGroups(stateValues.getUserName(), stateValues.getGroups(), fileName);
    }

    @Override
    String errorMessage(String fileName, Throwable e) {
        return DomainManagementLogger.ROOT_LOGGER.unableToUpdateUser(fileName, e.getMessage());
    }
}
