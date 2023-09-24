/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.security.adduser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.jboss.as.domain.management.logging.DomainManagementLogger;

/**
 * State to perform validation of the supplied username.
 *
 * Checks include: - Valid characters. Easy to guess user names. Duplicate users.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ValidateUserState extends AbstractValidationState {

    private static final String[] BAD_USER_NAMES = {"admin", "administrator", "root"};

    private static final char[] VALID_PUNCTUATION;
    private static final String VALID_SYMBOLS;

    static {
        char[] validPunctuation = { '.', '-', ',', '@', '/', '\\', '=' };
        Arrays.sort(validPunctuation);
        VALID_PUNCTUATION = validPunctuation;

        StringBuilder sb = new StringBuilder();
        for (int i=0 ; i < VALID_PUNCTUATION.length ; i++) {
            sb.append("\"");
            sb.append(VALID_PUNCTUATION[i]);
            sb.append("\"");
            if (i < VALID_PUNCTUATION.length -1) {
                sb.append(", ");
            }
        }
        VALID_SYMBOLS = sb.toString();
    }

    private final StateValues stateValues;
    private final ConsoleWrapper theConsole;

    public ValidateUserState(ConsoleWrapper theConsole, final StateValues stateValues) {
        this.theConsole = theConsole;
        this.stateValues = stateValues;
    }

    @Override
    protected Collection<State> getValidationStates() {
        List<State> validationStates = new ArrayList<State>(3);
        validationStates.add(getValidCharactersState());
        validationStates.add(getDuplicateCheckState());
        validationStates.add(getCommonNamesCheckState());

        return validationStates;
    }

    @Override
    protected State getSuccessState() {
        return new PromptPasswordState(theConsole, stateValues, false);
    }

    private State getRetryState() {
        return stateValues.isSilentOrNonInteractive() ? null : new PromptNewUserState(theConsole, stateValues);
    }

    private State getValidCharactersState() {
        return new State() {

            @Override
            public State execute() {
                for (char currentChar : stateValues.getUserName().toCharArray()) {
                    if ((!isValidPunctuation(currentChar))
                            && (isValidLetter(currentChar) || Character.isDigit(currentChar)) == false) {
                        return new ErrorState(theConsole, DomainManagementLogger.ROOT_LOGGER.usernameNotAlphaNumeric(VALID_SYMBOLS), getRetryState(), stateValues);
                    }
                }

                return ValidateUserState.this;
            }

            private boolean isValidPunctuation(char currentChar) {
                return (Arrays.binarySearch(VALID_PUNCTUATION, currentChar) >= 0);
            }

            private boolean isValidLetter(char c) {
                return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            }
        };
    }



    private State getDuplicateCheckState() {
        return new State() {

            @Override
            public State execute() {
                if (stateValues.isExistingDisabledUser() || stateValues.isExistingEnabledUser()) {
                    State duplicateContinuing = stateValues.isSilentOrNonInteractive() ? null :
                            new PromptNewUserState(theConsole, stateValues);
                    stateValues.setExistingUser(true);
                    if (stateValues.isSilentOrNonInteractive()) {
                        return ValidateUserState.this;
                    } else {
                        final boolean existingDisabledUser = stateValues.isExistingDisabledUser();
                        if (existingDisabledUser) {
                            theConsole.printf(DomainManagementLogger.ROOT_LOGGER.aboutToUpdateDisabledUser(stateValues.getUserName()));
                        } else {
                            theConsole.printf(DomainManagementLogger.ROOT_LOGGER.aboutToUpdateEnabledUser(stateValues.getUserName()));
                        }
                        theConsole.printf(AddUser.NEW_LINE);
                        String response = theConsole.readLine("(a): ");
                        if (response == null) {
                            // This will return user to the command prompt so add a new line to ensure
                            // the command prompt is on the next line.
                            theConsole.printf(AddUser.NEW_LINE);
                            return null;
                        }
                        Option option = convertResponse(response, existingDisabledUser);
                        switch (option) {
                            case NEW:
                                return duplicateContinuing;
                            case UPDATE:
                                break;
                            case ENABLE:
                                stateValues.getOptions().setEnableDisableMode(true);
                                stateValues.getOptions().setDisable(false);
                                return new PreModificationState(theConsole, stateValues);
                            case DISABLE:
                                stateValues.getOptions().setEnableDisableMode(true);
                                stateValues.getOptions().setDisable(true);
                                return new PreModificationState(theConsole, stateValues);
                            default:
                                return new ErrorState(theConsole, DomainManagementLogger.ROOT_LOGGER.invalidChoiceUpdateUserResponse(), this, stateValues);
                        }
                        return ValidateUserState.this;
                    }
                } else {
                    stateValues.setExistingUser(false);
                    return ValidateUserState.this;
                }
            }
        };
    }

    private State getCommonNamesCheckState() {
        return new State() {

            @Override
            public State execute() {
                // If this is updating an existing user then the name is already accepted.
                if (stateValues.isExistingUser() == false && stateValues.isSilentOrNonInteractive() == false) {
                    for (String current : BAD_USER_NAMES) {
                        if (current.equals(stateValues.getUserName().toLowerCase(Locale.ENGLISH))) {
                            String message = DomainManagementLogger.ROOT_LOGGER.usernameEasyToGuess(stateValues.getUserName());
                            String prompt = DomainManagementLogger.ROOT_LOGGER.sureToAddUser(stateValues.getUserName());

                            return new ConfirmationChoice(theConsole, message, prompt, ValidateUserState.this, getRetryState());
                        }
                    }
                }

                return ValidateUserState.this;
            }
        };
    }

    private Option convertResponse(final String response, final boolean existingDisabledUser) {
        String responseLowerCase = response.toLowerCase(Locale.ENGLISH);
        if ("".equals(responseLowerCase) || "a".equals(responseLowerCase)) {
            return Option.UPDATE;
        } else if ("b".equals(responseLowerCase)) {
            // Opposite option...
            if (existingDisabledUser) {
                // ... when the existing user is disabled, enable!
                return Option.ENABLE;
            } else {
                // ... when the existing user is enabled, disable!
                return Option.DISABLE;
            }
        } else if ("c".equals(responseLowerCase)) {
            return Option.NEW;
        }
        return Option.INVALID;
    }

    private enum Option {
        UPDATE,
        DISABLE,
        ENABLE,
        NEW,
        INVALID
    }
}
