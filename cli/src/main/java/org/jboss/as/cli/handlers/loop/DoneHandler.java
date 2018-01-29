/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.cli.handlers.loop;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.impl.ArgumentWithoutValue;

/**
 *
 * @author jfdenise
 */
public class DoneHandler extends CommandHandlerWithHelp {

    private final ArgumentWithoutValue discard = new ArgumentWithoutValue(this, "--discard");

    public DoneHandler() {
        super("done", true);
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        return ForControlFlow.get(ctx) != null;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {

        final ForControlFlow forCF = ForControlFlow.get(ctx);
        if (forCF == null) {
            throw new CommandLineException("done is not available outside 'for' loop");
        }
        forCF.run(ctx, discard.isPresent(ctx.getParsedCommandLine()));
    }
}
