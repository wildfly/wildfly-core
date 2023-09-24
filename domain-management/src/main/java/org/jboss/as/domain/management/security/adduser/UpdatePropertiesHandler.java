/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */


package org.jboss.as.domain.management.security.adduser;

import org.jboss.msc.service.StartException;
import org.wildfly.security.sasl.util.UsernamePasswordHashUtil;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

import static org.jboss.as.domain.management.security.adduser.AddUser.NEW_LINE;

public abstract class UpdatePropertiesHandler {

    private ConsoleWrapper theConsole;

    public UpdatePropertiesHandler(ConsoleWrapper theConsole) {
        this.theConsole = theConsole;
    }

    /**
     * Implement the persistence handler for storing the group properties.
     */
    void persist(final String key, final String value, final boolean enableDisableMode, final boolean disable, final File file) throws IOException, StartException {
        persist(key, value, enableDisableMode, disable, file, null);
    }

    /**
     * Implement the persistence handler for storing the user properties.
     */
    void persist(final String key, final String value, final boolean enableDisableMode, final boolean disable, final File file, final String realm) throws IOException, StartException {
        final PropertiesFileLoader propertiesHandler = realm == null ? new PropertiesFileLoader(file.getAbsolutePath()) :
                new UserPropertiesFileLoader(file.getAbsolutePath());
        try {
            propertiesHandler.start(null);
            if (realm != null) {
                ((UserPropertiesFileLoader) propertiesHandler).setRealmName(realm);
            }
            Properties prob = propertiesHandler.getProperties();
            if (value != null) {
                prob.setProperty(key, value);
            }
            if (enableDisableMode) {
                prob.setProperty(key + "!disable", String.valueOf(disable));
            }
            propertiesHandler.persistProperties();
        } finally {
            propertiesHandler.stop(null);
        }
    }

    /**
     * Customize message for update or add users
     * @param fileName - the filename of the updated property file;
     * @return the console message that should be present to the user
     */
    abstract String consoleUserMessage(String fileName);

    /**
     * Customize message for update or add groups
     * @param fileName - the filename of the updated property file;
     * @return the console message that should be present to the user
     */
    abstract String consoleGroupsMessage(String fileName);

    /**
     * Customize error message for update or add users groups / properties
     * @param fileName - the filename of the updated property file;
     * @return the console message that should be present to the user
     */
    abstract String errorMessage(String fileName, Throwable e);

    State update(StateValues stateValues) {
        final String userName = stateValues.getUserName();
        final boolean enableDisableMode = stateValues.getOptions().isEnableDisableMode();
        final boolean disable = stateValues.getOptions().isDisable();
        final String groups = stateValues.getGroups();
        final String password;
        if (stateValues.getPassword() != null) {
            try {
                password = new UsernamePasswordHashUtil().generateHashedHexURP(
                        stateValues.getUserName(),
                        stateValues.getRealm(),
                        stateValues.getPassword().toCharArray());
            } catch (NoSuchAlgorithmException e) {
                return new ErrorState(theConsole, e.getMessage(), null, stateValues);
            }
        } else {
            password = null;
        }
        // Persist username=password
        for (File current : stateValues.getUserFiles()) {
            try {
                persist(userName, password, enableDisableMode, disable, current, stateValues.getRealm());
                if (stateValues.isSilent() == false) {
                    theConsole.printf(consoleUserMessage(current.getCanonicalPath()));
                    theConsole.printf(NEW_LINE);
                }
            } catch (Exception e) {
                return new ErrorState(theConsole, errorMessage(current.getAbsolutePath(), e), null, stateValues);
            }
        }
        // Persist username=groups
        if (stateValues.groupPropertiesFound() && (groups != null || enableDisableMode)) {
            for (final File current : stateValues.getGroupFiles()) {
                try {
                    persist(userName, groups, enableDisableMode, disable, current);
                    if (stateValues.isSilent() == false) {
                        theConsole.printf(consoleGroupsMessage(current.getCanonicalPath()));
                        theConsole.printf(NEW_LINE);
                    }
                } catch (Exception e) {
                    return new ErrorState(theConsole, errorMessage(current.getAbsolutePath(), e), null, stateValues);
                }
            }
        }

        return null;
    }

}
