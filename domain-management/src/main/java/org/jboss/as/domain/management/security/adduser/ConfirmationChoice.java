/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import static org.jboss.as.domain.management.security.adduser.AddUser.NEW_LINE;
import static org.jboss.as.domain.management.security.adduser.AddUser.SPACE;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jboss.as.domain.management.logging.DomainManagementLogger;

/**
 * State to display a message to the user with option to confirm a choice.
 * <p/>
 * This state handles either a yes or no outcome and will loop with an error
 * on invalid input.
 */
public class ConfirmationChoice implements State {

    // These are deliberately using the default locale i.e. the same as the language the interface is presented in.
    private static final String LONG_YES = DomainManagementLogger.ROOT_LOGGER.yes().toLowerCase(Locale.getDefault());
    private static final String LONG_NO = DomainManagementLogger.ROOT_LOGGER.no().toLowerCase(Locale.getDefault());
    private static final String SHORT_YES = DomainManagementLogger.ROOT_LOGGER.shortYes().toLowerCase(Locale.getDefault());
    private static final String SHORT_NO = DomainManagementLogger.ROOT_LOGGER.shortNo().toLowerCase(Locale.getDefault());

    private ConsoleWrapper theConsole;
    private final String[] messageLines;
    private final String prompt;
    private final State yesState;
    private final State noState;

    private static final int YES = 0;
    private static final int NO = 1;
    private static final int INVALID = 2;

    public ConfirmationChoice(ConsoleWrapper theConsole,final String[] messageLines, final String prompt, final State yesState, final State noState) {
        this.theConsole = theConsole;
        this.messageLines = messageLines;
        this.prompt = prompt;
        this.yesState = yesState;
        this.noState = noState;
    }

    public ConfirmationChoice(ConsoleWrapper theConsole, final String message, final String prompt, final State yesState,
            final State noState) {
        this(theConsole, new String[] { message }, prompt, yesState, noState);
    }

    @Override
    public State execute() {
        if (messageLines != null) {
            for (String message : messageLines) {
                theConsole.printf(message);
                theConsole.printf(NEW_LINE);
            }
        }

        theConsole.printf(prompt);
        String temp = theConsole.readLine(SPACE);

        switch (convertResponse(temp)) {
            case YES:
                return yesState;
            case NO:
                return noState;
            default: {
                List<String> acceptedValues = new ArrayList<String>(4);
                acceptedValues.add(DomainManagementLogger.ROOT_LOGGER.yes());
                if (DomainManagementLogger.ROOT_LOGGER.shortYes().length() > 0) {
                    acceptedValues.add(DomainManagementLogger.ROOT_LOGGER.shortYes());
                }
                acceptedValues.add(DomainManagementLogger.ROOT_LOGGER.no());
                if (DomainManagementLogger.ROOT_LOGGER.shortNo().length() > 0) {
                    acceptedValues.add(DomainManagementLogger.ROOT_LOGGER.shortNo());
                }
                StringBuilder sb = new StringBuilder(acceptedValues.get(0));
                for (int i = 1; i < acceptedValues.size() - 1; i++) {
                    sb.append(", ");
                    sb.append(acceptedValues.get(i));
                }

                return new ErrorState(theConsole, DomainManagementLogger.ROOT_LOGGER.invalidConfirmationResponse(sb.toString(), acceptedValues.get(acceptedValues.size() - 1)), this);
            }
        }
    }

    private int convertResponse(final String response) {
        if (response != null) {
            String temp = response.toLowerCase(Locale.getDefault()); // We now need to match on the current local.
            if (LONG_YES.equals(temp) || SHORT_YES.equals(temp)) {
                return YES;
            }

            if (LONG_NO.equals(temp) || SHORT_NO.equals(temp)) {
                return NO;
            }
        }

        return INVALID;
    }

}
