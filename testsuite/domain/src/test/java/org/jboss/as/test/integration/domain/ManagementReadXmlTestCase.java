/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UUID;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.io.IOUtils;

import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.DomainTestSuite;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test of various read-config-as-xml against the domain controller.
 *
 * @author Emmanuel Hugonnet (c) 2022 Red Hat, Inc.
 */
public class ManagementReadXmlTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ManagementReadXmlTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testDomainReadConfigAsXml() throws Exception {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("read-config-as-xml");
        request.get(OP_ADDR).setEmptyList();

        ModelNode response = domainClient.execute(request);
        validateResponse(response);
        // TODO make some more assertions about result content
        request = new ModelNode();
        request.get(OP).set("read-config-as-xml-file");
        request.get(OP_ADDR).setEmptyList();

        OperationResponse opResponse = domainClient.executeOperation(Operation.Factory.create(request), OperationMessageHandler.DISCARD);
        String xml = validateOperationResponse(opResponse);
        String expectedXml = loadXMLConfigurationFile(Paths.get(domainPrimaryLifecycleUtil.getConfiguration().getDomainConfigFile()));
        Path expected = new File("target", "expected-domain.xml").toPath();
        Files.write(expected, Collections.singletonList(expectedXml));
        Path result = new File("target", "result-domain.xml").toPath();
        Files.write(result, Collections.singletonList(xml));
        Assert.assertEquals(expectedXml, xml);
    }

    @Test
    public void testHostReadConfigAsXml() throws Exception {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("read-config-as-xml");
        request.get(OP_ADDR).setEmptyList().add(HOST, "primary");

        ModelNode response = domainClient.execute(request);
        validateResponse(response);
        // TODO make some more assertions about result content

        request.get(OP_ADDR).setEmptyList().add(HOST, "secondary");
        response = domainClient.execute(request);
        validateResponse(response);

        request = new ModelNode();
        request.get(OP).set("read-config-as-xml-file");
        request.get(OP_ADDR).setEmptyList().add(HOST, "primary");

        OperationResponse opResponse = domainClient.executeOperation(Operation.Factory.create(request), OperationMessageHandler.DISCARD);
        String xml = validateOperationResponse(opResponse);
        String expectedXml = loadXMLHostConfigurationFile(Paths.get(domainPrimaryLifecycleUtil.getConfiguration().getHostConfigFile()), "primary");
        Path expected = new File("target", "expected-primary.xml").toPath();
        Files.write(expected, Collections.singletonList(expectedXml));
        Path result = new File("target", "result-primary.xml").toPath();
        Files.write(result, Collections.singletonList(xml));
        Assert.assertEquals(expectedXml, xml);

        request.get(OP_ADDR).setEmptyList().add(HOST, "secondary");
        opResponse = domainClient.executeOperation(Operation.Factory.create(request), OperationMessageHandler.DISCARD);
        xml = validateOperationResponse(opResponse);
        expectedXml = loadXMLHostConfigurationFile(Paths.get(domainSecondaryLifecycleUtil.getConfiguration().getHostConfigFile()), "secondary");
        expected = new File("target", "expected-secondary.xml").toPath();
        Files.write(expected, Collections.singletonList(expectedXml));
        result = new File("target", "result-secondary.xml").toPath();
        Files.write(result, Collections.singletonList(xml));
        Assert.assertEquals(expectedXml, xml);
    }

    @Test
    public void testServerReadConfigAsXml() throws Exception {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("read-config-as-xml-file");
        request.get(OP_ADDR).setEmptyList().add(HOST, "primary").add(SERVER, "main-one");

        OperationResponse opResponse = domainClient.executeOperation(Operation.Factory.create(request), OperationMessageHandler.DISCARD);
        String xml = validateOperationResponse(opResponse);
        Path expected = new File("target/test-classes", "expected-main-one.xml").toPath();
        String expectedXml = readFileAsString(expected).replaceAll(System.lineSeparator(), "\n");
        Files.write(new File("target", "expected-main-one.xml").toPath(), Collections.singletonList(expectedXml));
        Path result = new File("target", "result-main-one.xml").toPath();
        Files.write(result, Collections.singletonList(xml));
        Assert.assertEquals(expectedXml, xml + "\n");

        request = new ModelNode();
        request.get(OP).set("read-config-as-xml-file");
        request.get(OP_ADDR).setEmptyList().add(HOST, "secondary").add(SERVER, "main-three");
        opResponse = domainClient.executeOperation(Operation.Factory.create(request), OperationMessageHandler.DISCARD);
        xml = validateOperationResponse(opResponse);
        expected = new File("target/test-classes", "expected-main-three.xml").toPath();
        expectedXml = readFileAsString(expected).replaceAll(System.lineSeparator(), "\n");
        Files.write(new File("target", "expected-main-three.xml").toPath(), Collections.singletonList(expectedXml));
        result = new File("target", "result-main-three.xml").toPath();
        Files.write(result, Collections.singletonList(xml));
        Assert.assertEquals(expectedXml, xml);
    }

    private static String validateOperationResponse(OperationResponse response) throws IOException {
        Assert.assertEquals(1, response.getInputStreams().size());
        Assert.assertEquals(SUCCESS, response.getResponseNode().require(OUTCOME).asString());
        String uuid = response.getResponseNode().require(RESULT).require(UUID).asStringOrNull();
        Assert.assertNotNull(uuid);
        OperationResponse.StreamEntry stream = response.getInputStream(uuid);
        Assert.assertNotNull(stream);
        Assert.assertEquals("application/xml", stream.getMimeType());
        try (InputStream in = stream.getStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            IOUtils.copy(in, out);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String readFileAsString(Path file) throws Exception {
        return new String(Files.readAllBytes((file)));
    }

    private static String loadXMLConfigurationFile(Path file) throws Exception {
        try (Reader in = Files.newBufferedReader(file)) {
            XMLInputFactory xmlif = XMLInputFactory.newInstance();
            xmlif.setProperty("javax.xml.stream.isCoalescing", Boolean.TRUE);
            xmlif.setProperty("javax.xml.stream.isReplacingEntityReferences", Boolean.FALSE);
            xmlif.setProperty("javax.xml.stream.isNamespaceAware", Boolean.FALSE);
            xmlif.setProperty("javax.xml.stream.isValidating", Boolean.FALSE);
            Source source = new StreamSource(in);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StreamResult result = new StreamResult(new StringWriter());
            transformer.transform(source, result);
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n"
                    + result.getWriter().toString()
                            .replaceAll("(?s)<!--.*?-->", "")
                            .replaceAll(System.lineSeparator(), "\n")
                            .replaceAll("(?m)^[ \t]*\n?\n", "")
                            .trim();
        }
    }

    private static String loadXMLHostConfigurationFile(Path file, String hostName) throws Exception {
        return loadXMLConfigurationFile(file)
                .replace(hostName + "\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", hostName + "\"")
                .trim();
    }
}
