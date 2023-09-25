/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.security.adduser;

/**
 * Test the password weakness
 *
 * @author <a href="mailto:flemming.harms@gmail.com">Flemming Harms</a>
 */
public class WeakCheckStateTestCase extends PropertyTestHelper {
/*
    @Test
    public void testState() throws IOException, StartException {

        WeakCheckState weakCheckState = new WeakCheckState(consoleMock, values);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder();
        consoleMock.setResponses(consoleBuilder);

        State duplicateUserCheckState = weakCheckState.execute();

        assertTrue("Expected the next state to be DuplicateUserCheckState", duplicateUserCheckState instanceof PreModificationState);
        consoleBuilder.validate();
    }

    @Test
    public void testWrongPassword() {
        values.setUserName("thesame");
        values.setPassword("thesame".toCharArray());
        WeakCheckState weakCheckState = new WeakCheckState(consoleMock, values);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().expectedErrorMessage(MESSAGES.usernamePasswordMatch());
        consoleMock.setResponses(consoleBuilder);

        State errorState = weakCheckState.execute();

        assertTrue("Expected the next state to be ErrorState", errorState instanceof ErrorState);
        State promptNewUserState = errorState.execute();
        assertTrue("Expected the next state to be PromptNewUserState", promptNewUserState instanceof PromptNewUserState);
        consoleBuilder.validate();
    }

    @Test
    public void testForbiddenPassword() {
        values.setUserName("willFail");
        values.setPassword("administrator".toCharArray());
        WeakCheckState weakCheckState = new WeakCheckState(consoleMock, values);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().expectedErrorMessage(MESSAGES.passwordMustNotBeEqual("administrator"));
        consoleMock.setResponses(consoleBuilder);

        State errorState = weakCheckState.execute();

        assertTrue("Expected the next state to be ErrorState", errorState instanceof ErrorState);
        State promptNewUserState = errorState.execute();
        assertTrue("Expected the next state to be PromptNewUserState", promptNewUserState instanceof PromptNewUserState);
        consoleBuilder.validate();
    }

    @Test
    public void testWeakPassword() {
        values.setUserName("willFail");
        values.setPassword("zxcvbnm1@".toCharArray());
        WeakCheckState weakCheckState = new WeakCheckState(consoleMock, values);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().expectedErrorMessage(MESSAGES.passwordNotStrongEnough("MODERATE", "MEDIUM"));
        consoleMock.setResponses(consoleBuilder);

        State errorState = weakCheckState.execute();

        assertTrue("Expected the next state to be ErrorState", errorState instanceof ErrorState);
        State promptNewUserState = errorState.execute();
        assertTrue("Expected the next state to be PromptNewUserState", promptNewUserState instanceof PromptNewUserState);
        consoleBuilder.validate();
    }

    @Test
    public void testTooShortPassword() {
        values.setUserName("willFail");
        values.setPassword("1QwD%rf".toCharArray());
        WeakCheckState weakCheckState = new WeakCheckState(consoleMock, values);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().expectedErrorMessage(MESSAGES.passwordNotLongEnough(8));
        consoleMock.setResponses(consoleBuilder);

        State errorState = weakCheckState.execute();

        assertTrue("Expected the next state to be ErrorState", errorState instanceof ErrorState);
        State promptNewUserState = errorState.execute();
        assertTrue("Expected the next state to be PromptNewUserState", promptNewUserState instanceof PromptNewUserState);
        consoleBuilder.validate();
    }

    @Test
    public void testNoDigitInPassword() {
        values.setUserName("willFail");
        values.setPassword("!QwD%rGf".toCharArray());
        WeakCheckState weakCheckState = new WeakCheckState(consoleMock, values);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().expectedErrorMessage(MESSAGES.passwordMustHaveDigit());
        consoleMock.setResponses(consoleBuilder);

        State errorState = weakCheckState.execute();

        assertTrue("Expected the next state to be ErrorState", errorState instanceof ErrorState);
        State promptNewUserState = errorState.execute();
        assertTrue("Expected the next state to be PromptNewUserState", promptNewUserState instanceof PromptNewUserState);
        consoleBuilder.validate();
    }

    @Test
    public void testNoSymbolInPassword() {
        values.setUserName("willFail");
        values.setPassword("1QwD5rGf".toCharArray());
        WeakCheckState weakCheckState = new WeakCheckState(consoleMock, values);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().expectedErrorMessage(MESSAGES.passwordMustHaveSymbol());
        consoleMock.setResponses(consoleBuilder);

        State errorState = weakCheckState.execute();

        assertTrue("Expected the next state to be ErrorState", errorState instanceof ErrorState);
        State promptNewUserState = errorState.execute();
        assertTrue("Expected the next state to be PromptNewUserState", promptNewUserState instanceof PromptNewUserState);
        consoleBuilder.validate();
    }

    @Test
    public void testNoAlphaInPassword() {
        values.setUserName("willFail");
        values.setPassword("1$*>5&#}".toCharArray());
        WeakCheckState weakCheckState = new WeakCheckState(consoleMock, values);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().expectedErrorMessage(MESSAGES.passwordMustHaveAlpha());
        consoleMock.setResponses(consoleBuilder);

        State errorState = weakCheckState.execute();

        assertTrue("Expected the next state to be ErrorState", errorState instanceof ErrorState);
        State promptNewUserState = errorState.execute();
        assertTrue("Expected the next state to be PromptNewUserState", promptNewUserState instanceof PromptNewUserState);
        consoleBuilder.validate();
    }

    @Test
    public void testUsernameNotAlphaNumeric() {
        values.setUserName("username&");
        WeakCheckState weakCheckState = new WeakCheckState(consoleMock, values);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().expectedErrorMessage(MESSAGES.usernameNotAlphaNumeric());
        consoleMock.setResponses(consoleBuilder);

        State errorState = weakCheckState.execute();

        assertTrue("Expected the next state to be ErrorState", errorState instanceof ErrorState);
        State promptNewUserState = errorState.execute();
        assertTrue("Expected the next state to be PromptNewUserState", promptNewUserState instanceof PromptNewUserState);
        consoleBuilder.validate();
    }

    @Test
    public void testBadUsername() {
        String[] BAD_USER_NAMES = {"admin", "administrator", "root"};
        for (String userName : BAD_USER_NAMES) {

            values.setUserName(userName);
            WeakCheckState weakCheckState = new WeakCheckState(consoleMock, values);

            AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().
                    expectedConfirmMessage(MESSAGES.usernameEasyToGuess(userName), MESSAGES.sureToAddUser(userName), "n");
            consoleMock.setResponses(consoleBuilder);

            State confirmationChoice = weakCheckState.execute();

            assertTrue("Expected the next state to be ConfirmationChoice", confirmationChoice instanceof ConfirmationChoice);
            State promptNewUserState = confirmationChoice.execute();
            assertTrue("Expected the next state to be PromptNewUserState", promptNewUserState instanceof PromptNewUserState);
            consoleBuilder.validate();
        }
    }

    @Test
    public void testUsernameWithValidPunctuation() {
        values.setUserName("username.@\\=,/");
        WeakCheckState weakCheckState = new WeakCheckState(consoleMock, values);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder();
        consoleMock.setResponses(consoleBuilder);

        State duplicateUserCheckState = weakCheckState.execute();

        assertTrue("Expected the next state to be DuplicateUserCheckState", duplicateUserCheckState instanceof PreModificationState);
        consoleBuilder.validate();
    }
*/
}
