/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class CliConfigTestCase {
    @Test
    public void testEchoCommand() throws Exception {
        File f = createConfigFile(true);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--command=version");
        final String result = cli.executeNonInteractive();
        assertNotNull(result);
        assertTrue(result, result.contains("[disconnected /] version"));
        assertTrue(result, result.contains("JBoss AS release"));
    }

    @Test
    public void testNoEchoCommand() throws Exception {
        File f = createConfigFile(false);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--command=version");
        final String result = cli.executeNonInteractive();
        assertNotNull(result);
        assertFalse(result, result.contains("[disconnected /] version"));
        assertTrue(result, result.contains("JBoss AS release"));
    }

    private static File createConfigFile(Boolean enable) {
        File f = new File(TestSuiteEnvironment.getTmpDir(), "test-jboss-cli" +
                System.currentTimeMillis() + ".xml");
        f.deleteOnExit();
        String namespace = "urn:jboss:cli:3.1";
        XMLOutputFactory output = XMLOutputFactory.newInstance();
        try (Writer stream = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            XMLStreamWriter writer = output.createXMLStreamWriter(stream);
            writer.writeStartDocument();
            writer.writeStartElement("jboss-cli");
            writer.writeDefaultNamespace(namespace);
            writer.writeStartElement("echo-command");
            writer.writeCharacters(enable.toString());
            writer.writeEndElement(); //echo-command
            writer.writeEndElement(); //jboss-cli
            writer.writeEndDocument();
            writer.flush();
            writer.close();
        } catch (XMLStreamException | IOException ex) {
            fail("Failure creating config file " + ex);
        }
        return f;
    }
}
