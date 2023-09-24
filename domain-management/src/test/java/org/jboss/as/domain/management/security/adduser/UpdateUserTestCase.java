/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import org.jboss.msc.service.StartException;
import org.junit.Test;

import java.io.IOException;

/**
 * Test update user
 *
 * @author <a href="mailto:flemming.harms@gmail.com">Flemming Harms</a>
 */
public class UpdateUserTestCase extends PropertyTestHelper {

    @Test
    public void testState() throws IOException, StartException {
        values.setGroups(null);
        UpdateUser updateUserState = new UpdateUser(consoleMock, values);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().
                expectedDisplayText(updateUserState.consoleUserMessage(values.getUserFiles().get(0).getCanonicalPath())).
                expectedDisplayText(AddUser.NEW_LINE);
        consoleMock.setResponses(consoleBuilder);
        updateUserState.update(values);

        assertUserPropertyFile(USER_NAME);

        consoleBuilder.validate();
    }

    @Test
    public void testStateRoles() throws IOException, StartException {
        values.setGroups(ROLES);
        UpdateUser updateUserState = new UpdateUser(consoleMock, values);
        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().
                expectedDisplayText(updateUserState.consoleUserMessage(values.getUserFiles().get(0).getCanonicalPath())).
                expectedDisplayText(AddUser.NEW_LINE).
                expectedDisplayText(updateUserState.consoleGroupsMessage(values.getGroupFiles().get(0).getCanonicalPath())).
                expectedDisplayText(AddUser.NEW_LINE);
        consoleMock.setResponses(consoleBuilder);
        updateUserState.update(values);

        assertUserPropertyFile(USER_NAME);
        assertRolePropertyFile(USER_NAME);

        consoleBuilder.validate();
    }


}
