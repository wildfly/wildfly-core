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

package org.jboss.as.domain.management.security.adduser;

import java.util.function.Consumer;

import org.aesh.readline.Prompt;
import org.aesh.readline.Readline;
import org.aesh.readline.ReadlineBuilder;
import org.aesh.terminal.Connection;
import org.aesh.terminal.tty.Signal;

/**
 * Class to read line from terminal using Aesh-readline library.
 * Created by Marek Marusic <mmarusic@redhat.com> on 8/7/17.
 */
public class ReadLineHandler implements Consumer<Connection> {
    private String line;
    private Prompt prompt;

    public ReadLineHandler(Prompt prompt) {
        this.prompt = prompt;
    }

    public String getLine() {
        return line;
    }

    @Override
    public void accept(Connection connection) {
        read(connection, ReadlineBuilder.builder().enableHistory(false).build(), prompt);
        // lets open the connection to the terminal using this thread
        connection.openBlocking();
    }

    protected void read(Connection connection, Readline readline, Prompt prompt) {
        // Set the Ctrl-C handler
        connection.setSignalHandler(signal -> {
            if (signal == Signal.INT)
                connection.close();
        });

        readline.readline(connection, prompt, line -> {
            // we specify a simple lambda consumer to read the input thats saved into line.
            if (line != null) {
                this.line = line;
                connection.close();
            } else
                read(connection, readline, prompt);
        });
    }
}
