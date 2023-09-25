/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.security.adduser;

import org.jboss.as.domain.management.logging.DomainManagementLogger;

/**
 * State responsible for prompting for the list of groups for a user.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class PromptGroupsState implements State {

    private final StateValues stateValues;
    private final ConsoleWrapper theConsole;

    public PromptGroupsState(ConsoleWrapper theConsole, final StateValues stateValues) {
        this.theConsole = theConsole;
        this.stateValues = stateValues;
    }

    @Override
    public State execute() {
        if (stateValues.isSilentOrNonInteractive() == false) {
            if (!stateValues.getGroupFiles().isEmpty()) {
                theConsole.printf(DomainManagementLogger.ROOT_LOGGER.groupsPrompt());
                String userGroups = stateValues.getKnownGroups().get(stateValues.getUserName());
                stateValues.setGroups(theConsole.readLine("[%1$2s]: ", (userGroups == null ? "" : userGroups)));
            }
        }

        return new PreModificationState(theConsole, stateValues);
    }

}
