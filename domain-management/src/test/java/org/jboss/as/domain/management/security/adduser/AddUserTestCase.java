/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.msc.service.StartException;
import org.junit.Test;
import org.wildfly.security.sasl.util.UsernamePasswordHashUtil;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Test the AddUser state
 *
 * @author <a href="mailto:flemming.harms@gmail.com">Flemming Harms</a>
 */
public class AddUserTestCase extends PropertyTestHelper {

    @Test
    public void testAddUser() throws IOException, StartException {
        values.setGroups(ROLES);

        AssertConsoleBuilder consoleBuilder = buildAddUserGroupAssertConsole();

        AddUserState addUserState = new AddUserState(consoleMock, values);
        addUserState.update(values);

        assertRolePropertyFile(values.getUserName());
        assertUserPropertyFile(values.getUserName());

        consoleBuilder.validate();
    }

    @Test
    public void testAddEnabledUser() throws IOException, StartException {
        enableUser("Donny.Donowitz", UUID.randomUUID().toString(), ROLES);
    }

    @Test
    public void testAddDisabledUser() throws IOException, StartException {
        disableUser("Hugo.Stiglitz", UUID.randomUUID().toString(), ROLES);
    }

    @Test
    public void testEnableDisabledUser_keepRolesPassword() throws IOException, StartException {
        // Disable user
        disableUser("Omar.Ulmer", UUID.randomUUID().toString(), ROLES);
        // Enable user with the same roles(groups)/password
        enableUser("Omar.Ulmer", null, null);
    }

    @Test
    public void testEnableDisabledUser_newRolesPassword() throws IOException, StartException {
        // Disable user
        disableUser("Guillaume.Grossetie", UUID.randomUUID().toString(), ROLES);
        // Enable user with a new password and groups
        enableUser("Guillaume.Grossetie", UUID.randomUUID().toString(), "developer");
    }

    @Test
    public void testEnableEnabledUser() throws IOException, StartException {
        // Enable user
        enableUser("Aldo.Raine", UUID.randomUUID().toString(), ROLES);
        // (Re)Enable user
        enableUser("Aldo.Raine", null, null);
    }

    @Test
    public void testDisableDisabledUser() throws IOException, StartException {
        // Disable user
        disableUser("Archie.Hicox", UUID.randomUUID().toString(), ROLES);
        // (Re)Disable user
        disableUser("Archie.Hicox", null, null);
    }

    private void disableUser(String userName, String password, String groups) throws IOException, StartException {
        values.setUserName(userName);
        values.setPassword(password);
        values.setGroups(groups);
        values.getOptions().setDisable(true);
        values.getOptions().setEnableDisableMode(true);

        final String expectedPassword;
        if (password == null) {
            // Keep the previous password (already encrypted)
            expectedPassword = getPassword(values.getUserName());
        } else {
            // Encrypt the new password
            try {
                expectedPassword = new UsernamePasswordHashUtil().generateHashedHexURP(
                        values.getUserName(),
                        values.getRealm(),
                        password.toCharArray());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("", e);
            }
        }
        final String expectedGroups;
        if (groups == null) {
            // Keep the previous groups
            expectedGroups = getRoles(values.getUserName());
        } else {
            // Use the defined groups
            expectedGroups = groups;
        }
        // Count the previous number of lines in the role and user files
        int previousRoleFileLineNumber = countLineNumberRoleFile();
        int previousUserFileLineNumber = countLineNumberUserFile();

        AssertConsoleBuilder consoleBuilder = buildAddUserGroupAssertConsole();

        AddUserState addUserState = new AddUserState(consoleMock, values);
        addUserState.update(values);

        assertNull("The user is disabled, the user line must start with #", getEnabledUserPassword(values.getUserName()));
        assertNull("The user is disabled, the roles line must start with #", getEnabledUserRoles(values.getUserName()));
        assertEnableDisableUser(expectedPassword, expectedGroups, previousRoleFileLineNumber, previousUserFileLineNumber, consoleBuilder);
    }

    private void enableUser(String userName, String password, String groups) throws IOException, StartException {
        values.setUserName(userName);
        values.setPassword(password);
        values.setGroups(groups);
        values.getOptions().setDisable(false);
        values.getOptions().setEnableDisableMode(true);

        final String expectedPassword;
        if (password == null) {
            // Keep the previous password (already encrypted)
            expectedPassword = getPassword(values.getUserName());
        } else {
            // Encrypt the new password
            try {
                expectedPassword = new UsernamePasswordHashUtil().generateHashedHexURP(
                        values.getUserName(),
                        values.getRealm(),
                        password.toCharArray());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("", e);
            }
        }
        final String expectedGroups;
        if (groups == null) {
            // Keep the previous groups
            expectedGroups = getRoles(values.getUserName());
        } else {
            // Use the defined groups
            expectedGroups = groups;
        }
        // Count the previous number of lines in the role and user files
        int previousRoleFileLineNumber = countLineNumberRoleFile();
        int previousUserFileLineNumber = countLineNumberUserFile();

        AssertConsoleBuilder consoleBuilder = buildAddUserGroupAssertConsole();

        AddUserState addUserState = new AddUserState(consoleMock, values);
        addUserState.update(values);

        assertEnableDisableUser(expectedPassword, expectedGroups, previousRoleFileLineNumber, previousUserFileLineNumber, consoleBuilder);
    }

    /**
     * Build an "Assert Console" to validate that the output will display "user added" and "groups added".
     * @return The "Assert Console" built
     * @throws IOException
     */
    private AssertConsoleBuilder buildAddUserGroupAssertConsole() throws IOException {
        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().
                expectedDisplayText(DomainManagementLogger.ROOT_LOGGER.addedUser(values.getUserName(), values.getUserFiles().get(0).getCanonicalPath())).
                expectedDisplayText(AddUser.NEW_LINE).
                expectedDisplayText(DomainManagementLogger.ROOT_LOGGER.addedGroups(values.getUserName(), values.getGroups(), values.getGroupFiles().get(0).getCanonicalPath())).
                expectedDisplayText(AddUser.NEW_LINE);
        consoleMock.setResponses(consoleBuilder);
        return consoleBuilder;
    }

    /**
     * Assert that the enabled/disabled user is correctly added to the user and roles files.
     * @param expectedPassword The expected password
     * @param expectedGroups The expected groups
     * @param previousRoleFileLineNumber The number of lines of the roles file before enabling/disabling the user
     * @param previousUserFileLineNumber The number of lines of the user file before enabling/disabling the user
     * @param assertConsole The console to validate the output
     * @throws StartException
     * @throws IOException
     */
    private void assertEnableDisableUser(String expectedPassword, String expectedGroups, int previousRoleFileLineNumber, int previousUserFileLineNumber, AssertConsoleBuilder assertConsole) throws StartException, IOException {
        assertRolePropertyFile(values.getUserName(), expectedGroups);
        assertUserPropertyFile(values.getUserName(), expectedPassword);
        if (previousRoleFileLineNumber > 0) {
            assertEquals("Enabling/disabling a role just uncomment/comment out the line and must not create a new one", previousRoleFileLineNumber, countLineNumberRoleFile());
        }
        if (previousUserFileLineNumber > 0) {
            assertEquals("Enabling/disabling a user just uncomment/comment out the line and must not create a new one", previousUserFileLineNumber, countLineNumberUserFile());
        }
        assertConsole.validate();
    }
}
