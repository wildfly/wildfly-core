/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */


package org.jboss.as.domain.management.security.adduser;

import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * Test the duplicated user check state
 *
 * @author <a href="mailto:flemming.harms@gmail.com">Flemming Harms</a>
 */
public class DuplicateUserCheckStateTestCase extends PropertyTestHelper {

    @Test
    public void newUser() throws IOException {
        values.setExistingUser(false);
        values.setGroups(ROLES);
        PreModificationState userCheckState = new PreModificationState(consoleMock, values);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().
                expectedDisplayText(DomainManagementLogger.ROOT_LOGGER.aboutToAddUser(values.getUserName(), values.getRealm())).
                expectedDisplayText(AddUser.NEW_LINE).
                expectedDisplayText(DomainManagementLogger.ROOT_LOGGER.isCorrectPrompt() + " " + DomainManagementLogger.ROOT_LOGGER.yes() + "/" + DomainManagementLogger.ROOT_LOGGER.no() + "?").
                expectedDisplayText(" ").
                expectedInput(DomainManagementLogger.ROOT_LOGGER.yes()).
                expectedDisplayText(DomainManagementLogger.ROOT_LOGGER.addedUser(values.getUserName(), values.getUserFiles().get(0).getCanonicalPath())).
                expectedDisplayText(AddUser.NEW_LINE).
                expectedDisplayText(DomainManagementLogger.ROOT_LOGGER.addedGroups(values.getUserName(), values.getGroups(), values.getGroupFiles().get(0).getCanonicalPath())).
                expectedDisplayText(AddUser.NEW_LINE);
        consoleMock.setResponses(consoleBuilder);

        State nextState = userCheckState.execute();
        assertTrue(nextState instanceof ConfirmationChoice);
        nextState = nextState.execute();
        assertTrue(nextState instanceof AddUserState);
        nextState.execute();
        consoleBuilder.validate();
    }

    @Test
    public void existingUSer() throws IOException {
        values.setExistingUser(true);
        values.setGroups(ROLES);
        PreModificationState userCheckState = new PreModificationState(consoleMock, values);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().
                expectedDisplayText(DomainManagementLogger.ROOT_LOGGER.updateUser(values.getUserName(), values.getUserFiles().get(0).getCanonicalPath())).
                expectedDisplayText(AddUser.NEW_LINE).
                expectedDisplayText(DomainManagementLogger.ROOT_LOGGER.updatedGroups(values.getUserName(), values.getGroups(), values.getGroupFiles().get(0).getCanonicalPath())).
                expectedDisplayText(AddUser.NEW_LINE);
        consoleMock.setResponses(consoleBuilder);

        State nextState = userCheckState.execute();
        assertTrue(nextState instanceof UpdateUser);
        nextState = nextState.execute();
        consoleBuilder.validate();
    }

}
