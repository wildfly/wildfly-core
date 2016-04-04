/*
 * Copyright (C) 2014 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.server.deployment.scanner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.logging.Logger;
import org.jboss.as.server.deployment.scanner.logging.DeploymentScannerLogger;
import org.junit.After;
import org.junit.Before;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class XmlCompletionScannerTest {
    private FakeHandler handler;
    private static final DeploymentScannerLogger logger = DeploymentScannerLogger.ROOT_LOGGER;

    public XmlCompletionScannerTest() {
    }

    @Before
    public void preparehandler() {
        Logger lmLogger = Logger.getLogger("org.jboss.as.server.deployment.scanner");
        handler = new FakeHandler();
        lmLogger.addHandler(handler);
    }

    @After
    public void removehandler() {
        Logger lmLogger = Logger.getLogger("org.jboss.as.server.deployment.scanner");
        lmLogger.removeHandler(handler);
    }

    @Test
    public void testCompleteDocument() throws Exception {
        handler.messages.clear();
        File file = new File(XmlCompletionScannerTest.class.getClassLoader().getResource("loop-vdb.xml").toURI());
        boolean result = XmlCompletionScanner.isCompleteDocument(file);
        assertThat(result, is(true));
    }

    @Test
    public void testIncompleteDocument() throws Exception {
        File file = new File(XmlCompletionScannerTest.class.getClassLoader().getResource("loop-vdb-error.xml").toURI());
        boolean result = XmlCompletionScanner.isCompleteDocument(file);
        assertThat(result, is(false));
        assertThat(handler.messages, is(not(nullValue())));
        assertThat(handler.messages.size(), is(1));
        String infoMessage = handler.messages.get(0);
        assertThat(infoMessage, is(DeploymentScannerLogger.ROOT_LOGGER.invalidXmlFileFound("loop-vdb-error.xml", 18, 7)));
        handler.messages.clear();
    }

    private static class FakeHandler extends Handler {
        private List<String> messages = new ArrayList<String>();

        @Override
        public void publish(LogRecord record) {
            messages.add(String.format(record.getMessage(), record.getParameters()));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }
    }
}
