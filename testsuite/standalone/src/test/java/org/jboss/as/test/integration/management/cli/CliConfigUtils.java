package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.impl.Namespace;
import org.jboss.as.test.shared.TestSuiteEnvironment;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.fail;

public class CliConfigUtils {
    protected static File createConfigFile(Boolean enable, int timeout,
                                           Boolean validate, Boolean outputJSON, Boolean colorOutput, Boolean outputPaging) {
        File f = new File(TestSuiteEnvironment.getTmpDir(), "test-jboss-cli" +
                System.currentTimeMillis() + ".xml");
        f.deleteOnExit();
        String namespace = Namespace.CURRENT.getUriString();
        XMLOutputFactory output = XMLOutputFactory.newInstance();
        try (Writer stream = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            XMLStreamWriter writer = output.createXMLStreamWriter(stream);
            writer.writeStartDocument();
            writer.writeStartElement("jboss-cli");
            writer.writeDefaultNamespace(namespace);
            writer.writeStartElement("echo-command");
            writer.writeCharacters(enable.toString());
            writer.writeEndElement(); //echo-command
            if (timeout != 0) {
                writer.writeStartElement("command-timeout");
                writer.writeCharacters("" + timeout);
                writer.writeEndElement(); //command-timeout
            }
            writer.writeStartElement("validate-operation-requests");
            writer.writeCharacters(validate.toString());
            writer.writeEndElement(); //validate-operation-requests

            writer.writeStartElement("output-json");
            writer.writeCharacters(outputJSON.toString());
            writer.writeEndElement(); //output-json

            writeColorConfig(writer, colorOutput, "", "", "", "", "");

            writer.writeStartElement("output-paging");
            writer.writeCharacters(outputPaging.toString());
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

    protected static void writeColorConfig(XMLStreamWriter writer, Boolean enabled, String error,
                                         String warn, String success, String required, String batch) throws XMLStreamException {
        writer.writeStartElement("color-output");
        writer.writeStartElement("enabled");
        writer.writeCharacters(enabled.toString());
        writer.writeEndElement(); // enabled

        if (!"".equals(error)) {
            writer.writeStartElement("error-color");
            writer.writeCharacters(error);
            writer.writeEndElement();
        }

        if (!"".equals(warn)) {
            writer.writeStartElement("warn-color");
            writer.writeCharacters(warn);
            writer.writeEndElement();
        }

        if (!"".equals(success)) {
            writer.writeStartElement("success-color");
            writer.writeCharacters(success);
            writer.writeEndElement();
        }

        if (!"".equals(required)) {
            writer.writeStartElement("required-color");
            writer.writeCharacters(required);
            writer.writeEndElement();
        }

        if (!"".equals(batch)) {
            writer.writeStartElement("workflow-color");
            writer.writeCharacters(batch);
            writer.writeEndElement();
        }

        writer.writeEndElement(); //color-output
    }
}
