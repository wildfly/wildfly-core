/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.impl.aesh.commands.batch;

import java.io.IOException;
import java.util.Set;
import org.aesh.command.CommandException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.util.SimpleTable;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author jfdenise
 */
public class CommandUtil {

    public static void displayResponseHeaders(CommandContext context, ModelNode response) {
        if (response.has(org.jboss.as.cli.Util.RESPONSE_HEADERS)) {
            final ModelNode headers = response.get(org.jboss.as.cli.Util.RESPONSE_HEADERS);
            final Set<String> keys = headers.keys();
            final SimpleTable table = new SimpleTable(2, context.getTerminalWidth());
            for (String key : keys) {
                table.addLine(new String[]{key + ':', headers.get(key).asString()});
            }
            final StringBuilder buf = new StringBuilder();
            table.append(buf, true);
            context.println(buf.toString());
        }
    }

    public static ModelNode execute(ModelNode request, CommandContext ctx) throws CommandException {
        final ModelControllerClient client = ctx.getModelControllerClient();
        try {
            ModelNode response = client.execute(request);
            if (!Util.isSuccess(response)) {
                throw new CommandException(Util.getFailureDescription(response));
            }
            return response;
        } catch (IOException | CommandException e) {
            throw new CommandException(e.getMessage(), e);
        }
    }
}
