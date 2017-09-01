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
package org.jboss.as.cli.impl.aesh.commands;

import java.util.HashMap;
import java.util.Map;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.commands.operation.OperationCommandContainer;

/**
 * This class handles all rewriting from legacy syntax to new syntax.
 *
 * @author jdenise@redhat.com
 */
public class LegacyCommandRewriters {

    public interface LegacyCommandRewriter {

        String rewrite(String cmd, CommandContext ctx);
    }
    private static final Map<String, LegacyCommandRewriter> REWRITERS = new HashMap<>();

    public static void register(String name, LegacyCommandRewriter converter) {
        REWRITERS.put(name, converter);
    }

    public static String rewrite(String str, CommandContext ctx) {
        if (str == null || str.isEmpty() || OperationCommandContainer.isOperation(str)) {
            return str;
        }
        // The Aesh parser doesn't handle \\n
        // So remove them all.
        String sep = "\\" + Util.LINE_SEPARATOR;
        if (str.contains(sep)) {
            String[] split = str.split("\\" + sep);
            StringBuilder builder = new StringBuilder();
            for (String ss : split) {
                builder.append(ss + " ");
            }
            str = builder.toString();
        }
        String[] args = str.split(" ");
        if (args.length == 0) {
            return str;
        }
        LegacyCommandRewriter rewriter = REWRITERS.get(args[0]);
        if (rewriter == null) {
            return str;
        }
        return rewriter.rewrite(str, ctx);
    }

}
