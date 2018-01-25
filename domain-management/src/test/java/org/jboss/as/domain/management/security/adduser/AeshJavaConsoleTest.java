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

import static org.junit.Assert.*;

import org.aesh.readline.terminal.Key;
import org.aesh.readline.tty.terminal.TerminalConnection;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;

/**
 * Created by Marek Marusic <mmarusic@redhat.com> on 8/9/17.
 */
public class AeshJavaConsoleTest {

    @Test
    public void testLeftArrow() throws IOException, InterruptedException {
        AeshJavaConsoleTestResource aeshJavaConsoleTestResource = new AeshJavaConsoleTestResource();
        aeshJavaConsoleTestResource.start();
        Thread.sleep(100);

        aeshJavaConsoleTestResource.write("Foo");
        aeshJavaConsoleTestResource.write(Key.LEFT);
        aeshJavaConsoleTestResource.write("F");
        aeshJavaConsoleTestResource.write(Key.ENTER);
        aeshJavaConsoleTestResource.flushWrites();

        Thread.sleep(100);
        assertEquals("FoFo", aeshJavaConsoleTestResource.getLine());
    }

    @Test
    public void testUpArrow() throws IOException, InterruptedException {
        AeshJavaConsoleTestResource aeshJavaConsoleTestResource = new AeshJavaConsoleTestResource();
        aeshJavaConsoleTestResource.start();
        Thread.sleep(100);

        aeshJavaConsoleTestResource.write("Foo");
        aeshJavaConsoleTestResource.write(Key.UP);
        aeshJavaConsoleTestResource.write("F");
        aeshJavaConsoleTestResource.write(Key.ENTER);
        aeshJavaConsoleTestResource.flushWrites();

        Thread.sleep(100);
        assertEquals("FooF", aeshJavaConsoleTestResource.getLine());
    }

    @Test
    public void testInterruptionSignal() throws IOException, InterruptedException {
        AeshJavaConsoleTestResource aeshJavaConsoleTestResource = new AeshJavaConsoleTestResource();
        aeshJavaConsoleTestResource.start();
        Thread.sleep(100);

        aeshJavaConsoleTestResource.write("Foo".getBytes());
        aeshJavaConsoleTestResource.write(Key.CTRL_C.getKeyValuesAsString().getBytes());
        aeshJavaConsoleTestResource.flushWrites();

        Thread.sleep(100);
        assertEquals(false, aeshJavaConsoleTestResource.isAlive());
        assertEquals(Thread.State.TERMINATED, aeshJavaConsoleTestResource.getState());
    }

    private static class AeshJavaConsoleTestResource extends Thread {
        private PipedOutputStream pipedOutputStream;
        private PipedInputStream pipedInputStream;
        private ByteArrayOutputStream out;
        private String line;

        public AeshJavaConsoleTestResource() throws IOException {
            pipedOutputStream = new PipedOutputStream();
            pipedInputStream = new PipedInputStream(pipedOutputStream);
            out = new ByteArrayOutputStream();
        }

        public String getLine() {
            return line;
        }

        public void flushWrites() throws IOException {
            pipedOutputStream.flush();
        }

        public void write(String data) throws IOException {
            write(data.getBytes());
        }

        public void write(byte[] data) throws IOException {
            pipedOutputStream.write(data);
        }

        public void write(Key key) throws IOException {
            write(key.getKeyValuesAsString().getBytes());
        }

        public void run() {
            // Create JavaConsole with custom input, output streams for testing
            JavaConsole javaConsole = new JavaConsole() {
                @Override
                protected void createTerminalConnection(ReadLineHandler readLineHandler) throws IOException {
                    new TerminalConnection(Charset.defaultCharset(), pipedInputStream, out, readLineHandler);
                }
            };

            line = javaConsole.readLine("");
        }
    }

}