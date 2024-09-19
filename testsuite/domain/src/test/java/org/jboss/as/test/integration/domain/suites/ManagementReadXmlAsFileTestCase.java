/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UUID;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.apache.commons.io.IOUtils;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test of various read-config-as-xml-file against the domain controller.
 *
 * @author Emmanuel Hugonnet (c) 2022 Red Hat, Inc.
 */
public class ManagementReadXmlAsFileTestCase extends ManagementReadXmlTestCase {

    @BeforeClass
    public static void setupDomain() throws Exception {
        ManagementReadXmlTestCase.setupDomain();
        testSupport = DomainTestSuite.createSupport(ManagementReadXmlAsFileTestCase.class.getSimpleName());
    }

    @Test
    public void testDomainReadConfigAsXmlFile() throws Exception {
        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
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
    public void testDomainReadConfigAsXmlFileWithCli() throws Exception {
        WildFlyManagedConfiguration config = domainPrimaryLifecycleUtil.getConfiguration();
        try (CLIWrapper cli = new CLIWrapper(config.getHostControllerManagementAddress(), config.getHostControllerManagementPort(), true)) {
            Path result = new File("target", "result-domain-cli.xml").toPath();
            cli.sendLine("attachment save --operation=:read-config-as-xml-file  --file=" + result.toString());
            String expectedXml = loadXMLConfigurationFile(Paths.get(domainPrimaryLifecycleUtil.getConfiguration().getDomainConfigFile()));
            Path expected = new File("target", "expected-domain.xml").toPath();
            Files.write(expected, Collections.singletonList(expectedXml));
            Assert.assertEquals(expectedXml, Files.readString(result));
            cli.quit();
        }
    }

    @Test
    public void testHostReadConfigAsXmlFile() throws Exception {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
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
    public void testHostReadConfigAsXmlFileWithCli() throws Exception {
        WildFlyManagedConfiguration config = domainPrimaryLifecycleUtil.getConfiguration();
        try (CLIWrapper cli = new CLIWrapper(config.getHostControllerManagementAddress(), config.getHostControllerManagementPort(), true)) {
            String expectedXml =  loadXMLHostConfigurationFile(Paths.get(domainPrimaryLifecycleUtil.getConfiguration().getHostConfigFile()), "primary");
            Path expected = new File("target", "expected-primary.xml").toPath();
            Files.write(expected, Collections.singletonList(expectedXml));
            Path result = new File("target", "result-primary-cli.xml").toPath();
            cli.sendLine("attachment save --operation=/host=primary/:read-config-as-xml-file  --file=" + result.toString());
            Assert.assertEquals(expectedXml, Files.readString(result));
            cli.quit();
        }
        try (CLIWrapper cli = new CLIWrapper(config.getHostControllerManagementAddress(), config.getHostControllerManagementPort(), true)) {
            Path result = new File("target", "result-secondary-cli.xml").toPath();
            cli.sendLine("attachment save --operation=/host=secondary/:read-config-as-xml-file  --file=" + result.toString());
            String expectedXml = loadXMLHostConfigurationFile(Paths.get(domainSecondaryLifecycleUtil.getConfiguration().getHostConfigFile()), "secondary");
            Path expected = new File("target", "expected-secondary.xml").toPath();
            Files.write(expected, Collections.singletonList(expectedXml));
            Assert.assertEquals(expectedXml, Files.readString(result));
            cli.quit();
        }
    }

    @Test
    public void testServerReadConfigAsXmlFile() throws Exception {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("read-config-as-xml-file");
        request.get(OP_ADDR).setEmptyList().add(HOST, "primary").add(SERVER, "main-one");

        OperationResponse opResponse = domainClient.executeOperation(Operation.Factory.create(request), OperationMessageHandler.DISCARD);
        String xml = validateOperationResponse(opResponse);
        Path expected = new File("target").toPath().resolve("test-classes").resolve("expected-main-one.xml");
        String expectedXml = readFileAsString(expected).replaceAll(System.lineSeparator(), "\n") + "\n";
        Files.write(new File("target", "expected-main-one.xml").toPath(), Collections.singletonList(expectedXml));
        Path result = new File("target", "result-main-one.xml").toPath();
        Files.write(result, Collections.singletonList(xml));
        Assert.assertEquals(expectedXml, xml + "\n");

        request = new ModelNode();
        request.get(OP).set("read-config-as-xml-file");
        request.get(OP_ADDR).setEmptyList().add(HOST, "secondary").add(SERVER, "main-three");
        opResponse = domainClient.executeOperation(Operation.Factory.create(request), OperationMessageHandler.DISCARD);
        xml = validateOperationResponse(opResponse);
        expected = new File("target").toPath().resolve("test-classes").resolve("expected-main-three.xml");
        expectedXml = readFileAsString(expected).replaceAll(System.lineSeparator(), "\n");
        Files.write(new File("target", "expected-main-three.xml").toPath(), Collections.singletonList(expectedXml));
        result = new File("target", "result-main-three.xml").toPath();
        Files.write(result, Collections.singletonList(xml));
        Assert.assertEquals(expectedXml, xml);
    }

    @Test
    public void testServerReadConfigAsXmlFileWithCli() throws Exception {
        WildFlyManagedConfiguration config = domainPrimaryLifecycleUtil.getConfiguration();
        try (CLIWrapper cli = new CLIWrapper(config.getHostControllerManagementAddress(), config.getHostControllerManagementPort(), true)) {
            Path result = new File("target", "result-main-one-cli.xml").toPath();
            cli.sendLine("attachment save --operation=/host=primary/server=main-one:read-config-as-xml-file  --file=" + result.toString());
            Path expected = new File("target").toPath().resolve("test-classes").resolve("expected-main-one.xml");
            String expectedXml = readFileAsString(expected).replaceAll(System.lineSeparator(), "\n");
            Files.write(new File("target", "expected-main-one.xml").toPath(), Collections.singletonList(expectedXml));
            Assert.assertEquals(expectedXml, Files.readString(result));

            result = new File("target", "result-main-three-cli.xml").toPath();
            cli.sendLine("attachment save --operation=/host=secondary/server=main-three:read-config-as-xml-file  --file=" + result.toString());
            expected = new File("target").toPath().resolve("test-classes").resolve("expected-main-three.xml");
            expectedXml = readFileAsString(expected).replaceAll(System.lineSeparator(), "\n");
            Files.write(new File("target", "expected-main-one.xml").toPath(), Collections.singletonList(expectedXml));
            Assert.assertEquals(expectedXml, Files.readString(result));
        }
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
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
