/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation.header;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.EnterStateCharacterHandler;
import org.jboss.as.cli.parsing.LineBreakHandler;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.operation.PropertyListState;


/**
 *
 * @author Alexey Loubyansky
 */
public class RolloutPlanState extends DefaultParsingState {

    public static final RolloutPlanState INSTANCE = new RolloutPlanState();
    public static final String ID = "ROLLOUT_PLAN_HEADER";

    RolloutPlanState() {
        this(ServerGroupListState.INSTANCE, new PropertyListState(' ', ' ', ';', '}'));
    }

    RolloutPlanState(final ServerGroupListState sgList, final PropertyListState props) {
        super(ID);
        this.setIgnoreWhitespaces(true);
        //setEnterHandler(new EnterStateCharacterHandler(sgList));
        setEnterHandler(new LineBreakHandler(false, false){
            @Override
            public void doHandle(ParsingContext ctx) throws CommandFormatException {
                final String input = ctx.getInput();
                final int location = ctx.getLocation();
                if(inputHasArgument("id", input, location) || (inputHasArgument("name", input, location))) {
                    ctx.enterState(props);
                } else {
                    ctx.enterState(sgList);
                }
            }});
        setDefaultHandler(new EnterStateCharacterHandler(props));
        setReturnHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.isEndOfContent()) {
                    return;
                }
                final char ch = ctx.getCharacter();
                if(ch == '}' || ch == ';') {
                    ctx.leaveState();
                    return;
                }
                ctx.enterState(props);
                //ctx.leaveState();
            }});
    }

    private static boolean inputHasArgument(String argName, String input, int location) {
        return input.startsWith(argName, location) &&
                input.length() > location + argName.length() &&
                (input.charAt(location + argName.length()) == '=' || Character.isWhitespace(input.charAt(location + argName.length())));
    }
}
