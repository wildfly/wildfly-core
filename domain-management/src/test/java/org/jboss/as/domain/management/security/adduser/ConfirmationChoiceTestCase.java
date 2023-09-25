/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;


/**
 * Test the confirmation state
 *
 * @author <a href="mailto:flemming.harms@gmail.com">Flemming Harms</a>
 */
public class ConfirmationChoiceTestCase extends PropertyTestHelper {

    public static final String USER_DISPLAY_TEXT = "User display text";
    public static final String PLEASE_ANSWER = "Please answer";
/*
    @Test
    public void testState() throws IOException, StartException {

        ErrorState errorState = new ErrorState(consoleMock,null);
        PromptPasswordState passwordState = new PromptPasswordState(consoleMock,null);

        ConfirmationChoice confirmationChoice = new ConfirmationChoice(consoleMock, USER_DISPLAY_TEXT, PLEASE_ANSWER, passwordState,errorState);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().
                expectedConfirmMessage(USER_DISPLAY_TEXT, PLEASE_ANSWER, "y");

        consoleMock.setResponses(consoleBuilder);
        State promptPasswordState = confirmationChoice.execute();

        assertTrue("Expected the next state to be PromptPasswordState", promptPasswordState instanceof PromptPasswordState);
        consoleBuilder.validate();
    }

    @Test
    public void testWrongAnswer() throws IOException, StartException {

        ErrorState errorState = new ErrorState(consoleMock,null);
        PromptPasswordState passwordState = new PromptPasswordState(consoleMock,null);

        ConfirmationChoice confirmationChoice = new ConfirmationChoice(consoleMock, USER_DISPLAY_TEXT, PLEASE_ANSWER, passwordState,errorState);

        List<String> acceptedValues = new ArrayList<String>(4);
        acceptedValues.add(MESSAGES.yes());
        if (MESSAGES.shortYes().length() > 0) {
            acceptedValues.add(MESSAGES.shortYes());
        }
        acceptedValues.add(MESSAGES.no());
        if (MESSAGES.shortNo().length() > 0) {
            acceptedValues.add(MESSAGES.shortNo());
        }
        StringBuilder sb = new StringBuilder(acceptedValues.get(0));
        for (int i = 1; i < acceptedValues.size() - 1; i++) {
            sb.append(", ");
            sb.append(acceptedValues.get(i));
        }

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().expectedConfirmMessage(USER_DISPLAY_TEXT,
                PLEASE_ANSWER, "d").expectedErrorMessage(
                MESSAGES.invalidConfirmationResponse(sb.toString(), acceptedValues.get(acceptedValues.size() - 1)));

        consoleMock.setResponses(consoleBuilder);
        State nextState = confirmationChoice.execute();

        assertTrue("Expected the next state to be ErrorState", nextState instanceof ErrorState);
        nextState.execute();
        consoleBuilder.validate();
    }

    */
}
