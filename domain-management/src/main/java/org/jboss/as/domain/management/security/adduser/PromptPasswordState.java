/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import static org.jboss.as.domain.management.security.adduser.AddUser.NEW_LINE;

import java.util.Arrays;
import java.util.List;

import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.as.domain.management.security.password.PasswordRestriction;
import org.jboss.as.domain.management.security.password.RestrictionLevel;

/**
 * State to prompt the user for a password
 * <p/>
 * This state handles password validation by let the user re-enter the password in case of the password mismatch the user will
 * be present for an error and will re-enter the PromptPasswordState again
 */
public class PromptPasswordState implements State {

    private final ConsoleWrapper theConsole;
    private final StateValues stateValues;
    private final boolean rePrompt;

    public PromptPasswordState(ConsoleWrapper theConsole, StateValues stateValues, boolean rePrompt) {
        this.theConsole = theConsole;
        this.stateValues = stateValues;
        this.rePrompt = rePrompt;
    }

    @Override
    public State execute() {
        if (stateValues.isSilentOrNonInteractive() == false) {
            if (rePrompt == false) {
                // Password requirements.
                RestrictionLevel level = stateValues.getOptions().getCheckUtil().getRestrictionLevel();
                if (!RestrictionLevel.RELAX.equals(level)) {
                    final List<PasswordRestriction> passwordRestrictions = stateValues.getOptions().getCheckUtil().getPasswordRestrictions();
                    if (!passwordRestrictions.isEmpty()) {
                        if (level == RestrictionLevel.REJECT) {
                            theConsole.printf(DomainManagementLogger.ROOT_LOGGER.passwordRequirements());
                        } else {
                            theConsole.printf(DomainManagementLogger.ROOT_LOGGER.passwordRecommendations());
                        }
                        theConsole.printf(NEW_LINE);
                        for (PasswordRestriction passwordRestriction : passwordRestrictions) {
                            final String message = passwordRestriction.getRequirementMessage();
                            if (message != null && !message.isEmpty()) {
                                theConsole.printf(" - ");
                                theConsole.printf(message);
                                theConsole.printf(NEW_LINE);
                            }
                        }
                    }
                }
                // Prompt for password.
                theConsole.printf(DomainManagementLogger.ROOT_LOGGER.passwordPrompt());
                char[] tempChar = theConsole.readPassword(" : ");
                if (tempChar == null || tempChar.length == 0) {
                    return new ErrorState(theConsole, DomainManagementLogger.ROOT_LOGGER.noPasswordExiting(), stateValues);
                }
                stateValues.setPassword(new String(tempChar));

                return new ValidatePasswordState(theConsole, stateValues);
            } else {

                theConsole.printf(DomainManagementLogger.ROOT_LOGGER.passwordConfirmationPrompt());
                char[] secondTempChar = theConsole.readPassword(" : ");
                if (secondTempChar == null) {
                    secondTempChar = new char[0]; // If re-entry missed allow fall through to comparison.
                }

                if (Arrays.equals(stateValues.getPassword().toCharArray(), secondTempChar) == false) {
                    // Start again at the first password.
                    return new ErrorState(theConsole, DomainManagementLogger.ROOT_LOGGER.passwordMisMatch(), new PromptPasswordState(theConsole, stateValues, false));
                }

                // As long as it matches the actual value has already been validated.
                // Rather than checking if we are in management mode we need to check if we found any group property files.
                return stateValues.groupPropertiesFound() ? new PromptGroupsState(theConsole, stateValues)
                        : new PreModificationState(theConsole, stateValues);
            }
        }

        return new ValidatePasswordState(theConsole, stateValues);
    }
}
