/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.security.adduser;

import static org.jboss.as.domain.management.security.adduser.AddUser.NEW_LINE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.as.domain.management.security.password.PasswordCheckResult;
import org.jboss.as.domain.management.security.password.RestrictionLevel;

/**
 * State to perform validation of the supplied password.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ValidatePasswordState extends AbstractValidationState {

    private final StateValues stateValues;
    private final ConsoleWrapper theConsole;

    public ValidatePasswordState(ConsoleWrapper theConsole, final StateValues stateValues) {
        this.theConsole = theConsole;
        this.stateValues = stateValues;
    }

    @Override
    protected Collection<State> getValidationStates() {
        final List<State> validationStates;
        final boolean relaxRestriction = RestrictionLevel.RELAX.equals(stateValues.getOptions().getCheckUtil().getRestrictionLevel());
        if (!relaxRestriction && !stateValues.getOptions().isEnableDisableMode()) {
            validationStates = new ArrayList<State>(1);
            validationStates.add(getDetailedCheckState());
        } else {
            validationStates = new ArrayList<State>(0);
        }
        return validationStates;
    }

    private State getRetryState() {
        return stateValues.isSilentOrNonInteractive() ? null : new PromptNewUserState(theConsole, stateValues);
    }

    private State getDetailedCheckState() {
        return new State() {

            @Override
            public State execute() {
                PasswordCheckResult result = stateValues.getOptions().getCheckUtil().check(false, stateValues.getUserName(), stateValues.getPassword());
                final boolean warnResult = PasswordCheckResult.Result.WARN.equals(result.getResult());
                final boolean rejectResult = PasswordCheckResult.Result.REJECT.equals(result.getResult());
                switch (stateValues.getOptions().getCheckUtil().getRestrictionLevel()) {
                    case WARN:
                        if ((warnResult || rejectResult) && !stateValues.isSilentOrNonInteractive()) {
                            return confirmWeakPassword(result);
                        }
                        break;
                    case REJECT:
                        if (warnResult && !stateValues.isSilentOrNonInteractive()) {
                            return confirmWeakPassword(result);
                        }
                        if (rejectResult) {
                            return new ErrorState(theConsole, result.getMessage(), getRetryState(), stateValues);
                        }
                        break;
                    default:
                        break;
                }
                return ValidatePasswordState.this;
            }
        };
    }

    private State confirmWeakPassword(PasswordCheckResult result) {
        if (stateValues.getOptions().isConfirmWarning()) {
            theConsole.printf(result.getMessage());
            theConsole.printf(DomainManagementLogger.ROOT_LOGGER.sureToSetPassword());
            theConsole.printf(NEW_LINE);
            return ValidatePasswordState.this;
        } else {
            String message = result.getMessage();
            String prompt = DomainManagementLogger.ROOT_LOGGER.sureToSetPassword();
            State noState = new PromptNewUserState(theConsole, stateValues);
            return new ConfirmationChoice(theConsole, message, prompt, this, noState);
        }
    }

    @Override
    protected State getSuccessState() {
        // We like the password but want the user to re-enter to confirm they know the password.
        return stateValues.isInteractive() ? new PromptPasswordState(theConsole, stateValues, true) : new PreModificationState(
                theConsole, stateValues);
    }

}
