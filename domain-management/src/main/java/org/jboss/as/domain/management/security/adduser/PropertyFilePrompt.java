/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.security.adduser;

import static org.jboss.as.domain.management.security.adduser.AddUser.DEFAULT_APPLICATION_REALM;
import static org.jboss.as.domain.management.security.adduser.AddUser.DEFAULT_MANAGEMENT_REALM;

import java.util.Locale;

import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.as.domain.management.security.adduser.AddUser.FileMode;
import org.jboss.as.domain.management.security.adduser.AddUser.RealmMode;

/**
 * State responsible for asking the user if they are adding a management user or an application user.
 *
 * @author <a href="mailto:flemming.harms@gmail.com">Flemming Harms</a>
 */
public class PropertyFilePrompt implements State {

    private ConsoleWrapper theConsole;
    private StateValues stateValues;

    public PropertyFilePrompt(ConsoleWrapper theConsole, StateValues stateValues) {
        this.theConsole = theConsole;
        this.stateValues = stateValues;
    }

    @Override
    public State execute() {

        theConsole.printf(AddUser.NEW_LINE);
        theConsole.printf(DomainManagementLogger.ROOT_LOGGER.filePrompt());
        theConsole.printf(AddUser.NEW_LINE);

        String temp = theConsole.readLine("(a): ");
        if (temp == null) {
            /*
             * This will return user to the command prompt so add a new line to ensure the command prompt is on the next
             * line.
             */
            theConsole.printf(AddUser.NEW_LINE);
            return null;
        }

        boolean setRealm = stateValues.getRealmMode() != RealmMode.USER_SUPPLIED;

        switch (convertResponse(temp)) {
            case MANAGEMENT:
                stateValues.setFileMode(FileMode.MANAGEMENT);
                if (setRealm) {
                    stateValues.setRealm(DEFAULT_MANAGEMENT_REALM);
                }
                return new PropertyFileFinder(theConsole, stateValues);
            case APPLICATION:
                stateValues.setFileMode(FileMode.APPLICATION);
                if (setRealm) {
                    stateValues.setRealm(DEFAULT_APPLICATION_REALM);
                }
                return new PropertyFileFinder(theConsole, stateValues);
            default:
                return new ErrorState(theConsole, DomainManagementLogger.ROOT_LOGGER.invalidChoiceResponse(), this, stateValues);
        }
    }

    private Option convertResponse(final String response) {
        String temp = response.toLowerCase(Locale.ENGLISH);
        if ("".equals(temp) || "a".equals(temp)) {
            return Option.MANAGEMENT;
        }

        if ("b".equals(temp)) {
            return Option.APPLICATION;
        }

        return Option.INVALID;
    }

    private enum Option {
        MANAGEMENT, APPLICATION, INVALID;
    }

}
