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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.launcher.CliCommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author Jean-François Denise <jdenise@redhat.com>
 */
// Required by Bootable JAR in order to create the installation directory
@RunWith(WildFlyRunner.class)
public class JBossCliXmlConfigTestCase {

    @Test
    public void testSSLElement() {
        testConfig("2.0");
        testConfig("3.0");
        testConfig("3.1");
        testConfig("3.2");
    }

    private static void testConfig(String version) {
        File f = createConfigFile(version);
        execute(f);
    }

    private static void execute(File f) {
        if (!f.exists()) {
            fail("Config file " + f.getPath() + " doesn't exist");
        }

        final String jbossDist = TestSuiteEnvironment.getSystemProperty("jboss.dist");
        if (jbossDist == null) {
            fail("jboss.dist system property is not set");
        }

        final CliCommandBuilder commandBuilder = CliCommandBuilder.asModularLauncher(jbossDist);
        commandBuilder.addJavaOptions("-Djboss.cli.config=" + f.toPath());
        commandBuilder.addJavaOptions(System.getProperty("cli.jvm.args", "").split("\\s+"));
        commandBuilder.addCliArgument("--command=help");
        Process cliProc = null;
        try {
            cliProc = Launcher.of(commandBuilder)
                    .setRedirectErrorStream(true)
                    .launch();
            // Must flush otherwise process doesn't exit.
            flushInStream(cliProc.getInputStream());
            cliProc.waitFor(TimeoutUtil.adjust(10000), TimeUnit.MILLISECONDS);
        } catch (InterruptedException | IOException e) {
            fail("Failed to start CLI process: " + e.getLocalizedMessage());
        }
        if (cliProc.exitValue() != 0) {
            fail("Cli process failed with exit code " + cliProc.exitValue());
        }
    }

    private static void flushInStream(InputStream stream) throws IOException {
        byte[] buff = new byte[1024];
        while ((stream.read(buff)) >= 0) {
            // NO-OP
        }
    }

    /*
        <jboss-cli xmlns="urn:jboss:cli:2.0|3.0">
            <ssl>
            </ssl>
        </jboss-cli>
     */
    private static File createConfigFile(String version) {
        File f = new File(TestSuiteEnvironment.getTmpDir(), "test-jboss-cli.xml");
        String namespace = "urn:jboss:cli:" + version;
        XMLOutputFactory output = XMLOutputFactory.newInstance();
        try (Writer stream = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            XMLStreamWriter writer = output.createXMLStreamWriter(stream);
            writer.writeStartDocument();
            writer.writeStartElement("jboss-cli");
            writer.writeDefaultNamespace(namespace);
            writer.writeStartElement("ssl");
            writer.writeEndElement(); //ssl
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
