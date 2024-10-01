/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;


import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;

import java.io.File;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
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

    static final Logger log = Logger.getLogger("org.jboss.as.test.integration.domain");

    static DomainTestSupport testSupport;
    static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        DomainTestSuite.stopSupport();
        testSupport = DomainTestSuite.createSupport(ManagementReadXmlTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
        prepareExpectedServerConfiguration("one", true, 0);
        prepareExpectedServerConfiguration("three", false, 350);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    private static void prepareExpectedServerConfiguration(String server, boolean propertyOne, int offset) throws Exception {
        String serverConfigFileName = "expected-main-" + server + ".xml";
        Path serverHome = new File(domainPrimaryLifecycleUtil.getConfiguration().getJbossHome()).toPath();
        Path configurationDir = serverHome.resolve("standalone").resolve("configuration");
        Files.createDirectories(configurationDir);
        Path referenceConfiguration = configurationDir.resolve(serverConfigFileName);
        Files.copy(new File("target").toPath().resolve("test-classes").resolve("base-server-config.xml"), referenceConfiguration, REPLACE_EXISTING);
        try (CLIWrapper cli = new CLIWrapper(false)) {
            log.info("Executing the following command on cli: embed-server --admin-only --server-config=" + serverConfigFileName + " --jboss-home=" + serverHome.toAbsolutePath());
            cli.sendLine("embed-server --admin-only --server-config=" + serverConfigFileName + " --jboss-home=" + serverHome.toAbsolutePath());
            if(!propertyOne) {
                cli.sendLine("/system-property=jboss.domain.test.property.one:remove()");
                cli.sendLine("/system-property=jboss.domain.test.property.one:add(value=ONE)");
                cli.sendLine("/path=domainTestPath:remove()");
                cli.sendLine("/path=domainTestPath:add(path=\"/tmp\")");
            }
            cli.sendLine("/subsystem=logging:add()");
            cli.sendLine("/subsystem=logging/console-handler=CONSOLE:add(level=INFO, formatter=\"%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n\")");
            cli.sendLine("/subsystem=logging/periodic-rotating-file-handler=FILE:add(level=INFO, suffix=\".yyyy-MM-dd\", formatter=\"%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n\", file={relative-to=\"jboss.server.log.dir\", path=\"server.log\"})");
            cli.sendLine("/subsystem=logging/logger=org.jboss.as.controller:add(category=\"org.jboss.as.controller\", level=TRACE)");
            cli.sendLine("/subsystem=logging/root-logger=ROOT:add(handlers=[CONSOLE, FILE], level=INFO)");
            cli.sendLine("/subsystem=core-management:add()");

            cli.sendLine("batch");
            cli.sendLine("/subsystem=elytron:add(disallowed-providers=[\"OracleUcrypto\"],final-providers=combined-providers)");
            cli.sendLine("/subsystem=elytron/provider-loader=elytron:add(module=org.wildfly.security.elytron)");
            cli.sendLine("/subsystem=elytron/provider-loader=openssl:add(module=org.wildfly.openssl)");
            cli.sendLine("/subsystem=elytron/aggregate-providers=combined-providers:add(providers=[\"elytron\",\"openssl\"])");
            cli.sendLine("/subsystem=elytron/identity-realm=local:add(identity=\"$local\")");
            cli.sendLine("/subsystem=elytron/file-audit-log=local-audit:add(format=JSON,path=audit.log,relative-to=jboss.server.log.dir)");
            cli.sendLine("/subsystem=elytron/constant-realm-mapper=local:add(realm-name=local)");
            cli.sendLine("/subsystem=elytron/provider-http-server-mechanism-factory=global:add()");
            cli.sendLine("/subsystem=elytron/simple-permission-mapper=default-permission-mapper:add(mapping-mode=first,"
                    + "permission-mappings=[{\"principals\" => [\"anonymous\"],"
                    + "\"permission-sets\" => [(\"permission-set\" => \"default-permissions\")]},{\"match-all\" => \"true\","
                    + "\"permission-sets\" => [(\"permission-set\" => \"login-permission\"),(\"permission-set\" => \"default-permissions\")]}])");
            cli.sendLine("/subsystem=elytron/simple-role-decoder=groups-to-roles:add(attribute=groups)");
            cli.sendLine("/subsystem=elytron/provider-sasl-server-factory=global:add");
            cli.sendLine("/subsystem=elytron/mechanism-provider-filtering-sasl-server-factory=elytron:add(filters=[{\"provider-name\" => \"WildFlyElytron\"}],sasl-server-factory=global)");
            cli.sendLine("/subsystem=elytron/configurable-sasl-server-factory=configured:add(properties={\"wildfly.sasl.local-user.default-user\" => \"$local\"},sasl-server-factory=elytron)");
            cli.sendLine("/subsystem=elytron/constant-role-mapper=super-user-mapper:add(roles=[\"SuperUser\"])");
            cli.sendLine("/subsystem=elytron/permission-set=login-permission:add(permissions=[(\"class-name\" => \"org.wildfly.security.auth.permission.LoginPermission\")])");
            cli.sendLine("/subsystem=elytron/permission-set=default-permissions:add(permissions=[])");
            cli.sendLine("/subsystem=elytron/properties-realm=ApplicationRealm:add(users-properties={"
                    + "\"path\" => \"application-users.properties\","
                    + "\"relative-to\" => \"jboss.domain.config.dir\","
                    + "\"digest-realm-name\" => \"ApplicationRealm\"})");
            cli.sendLine("/subsystem=elytron/security-domain=ApplicationDomain:add(default-realm=ApplicationRealm,permission-mapper=default-permission-mapper,realms=[{\"realm\" => \"ApplicationRealm\",\"role-decoder\" => \"groups-to-roles\"},{\"realm\" => \"local\"}])");
            cli.sendLine("/subsystem=elytron/sasl-authentication-factory=application-sasl-authentication:add(mechanism-configurations=[{\"mechanism-name\" => \"JBOSS-LOCAL-USER\",\"realm-mapper\" => \"local\"},{\"mechanism-name\" => \"DIGEST-MD5\",\"mechanism-realm-configurations\" => [{\"realm-name\" => \"ApplicationRealm\"}]}],sasl-server-factory=configured,security-domain=ApplicationDomain)");
            cli.sendLine("/subsystem=elytron/key-store=applicationKS:add(credential-reference={\"clear-text\" => \"password\"},path=application.keystore,relative-to=jboss.domain.config.dir,type=JKS)");
            cli.sendLine("/subsystem=elytron/key-manager=applicationKM:add(credential-reference={\"clear-text\" => \"password\"},generate-self-signed-certificate-host=localhost,key-store=applicationKS)");
            cli.sendLine("/subsystem=elytron/server-ssl-context=applicationSSC:add(key-manager=applicationKM)");
            cli.sendLine("run-batch");

            cli.sendLine("batch");
            cli.sendLine("/subsystem=io:add(default-worker=default)");
            cli.sendLine("/subsystem=io/worker=default:add()");
            cli.sendLine("run-batch");

            cli.sendLine("/subsystem=jmx:add()");
            cli.sendLine("/subsystem=jmx/expose-model=resolved:add()");
            cli.sendLine("/subsystem=jmx/expose-model=expression:add()");
            cli.sendLine("/subsystem=jmx/remoting-connector=jmx:add()");

            cli.sendLine("/subsystem=remoting:add()");
            cli.sendLine("/subsystem=remoting/connector=remoting-connector:add(socket-binding=remoting, sasl-authentication-factory=application-sasl-authentication)");

            cli.sendLine("/subsystem=request-controller:add()");

            cli.sendLine("/subsystem=security-manager:add()");
            cli.sendLine("/subsystem=security-manager/deployment-permissions=default:add(maximum-permissions=[{class=java.security.AllPermission}]");
            if(!propertyOne) {
                cli.sendLine("/interface=management:write-attribute(name=inet-address,value=\"${jboss.test.host.secondary.address}\")");
                cli.sendLine("/interface=public:write-attribute(name=inet-address,value=\"${jboss.test.host.secondary.address}\")");
            }
            cli.sendLine("/socket-binding-group=standard-sockets:write-attribute(name=port-offset, value=" + offset + ")");
            cli.quit();
        }
        Files.move(referenceConfiguration, new File("target").toPath().toAbsolutePath().resolve("test-classes").resolve(referenceConfiguration.getFileName()), REPLACE_EXISTING);
    }

    @Test
    public void testDomainReadConfigAsXml() throws Exception {
        ModelNode request = new ModelNode();
        request.get(OP).set("write-config");
        request.get(OP_ADDR).setEmptyList();
        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode response = domainClient.execute(request);
        validateResponse(response, true);
        Path expectedXmlPath = Paths.get(domainPrimaryLifecycleUtil.getConfiguration().getDomainDirectory()).resolve("configuration").resolve("testing-domain-standard.xml");

        request = new ModelNode();
        request.get(OP).set("read-config-as-xml");
        request.get(OP_ADDR).setEmptyList();
        response = domainClient.execute(request);
        String xml = validateResponse(response, true).asString().replace('\'', '\"');
        String expectedXml = loadXMLConfigurationFile(expectedXmlPath);
        Assert.assertEquals(expectedXml, xml);
    }

    @Test
    public void testHostReadConfigAsXml() throws Exception {

        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("write-config");
        request.get(OP_ADDR).setEmptyList().add(HOST, "primary");
        ModelNode response = domainClient.execute(request);
        validateResponse(response, true);
        Path expectedXmlPath = Paths.get(domainPrimaryLifecycleUtil.getConfiguration().getDomainDirectory()).resolve("configuration").resolve("testing-host-primary.xml");

        request = new ModelNode();
        request.get(OP).set("read-config-as-xml");
        request.get(OP_ADDR).setEmptyList().add(HOST, "primary");
        response = domainClient.execute(request);
        String xml = validateResponse(response, true).asString().replace('\'', '\"');
        String expectedXml = loadXMLConfigurationFile(expectedXmlPath);
        Assert.assertEquals(expectedXml, xml);

        request = new ModelNode();
        request.get(OP_ADDR).setEmptyList().add(HOST, "secondary");
        request.get(OP).set("write-config");
        response = domainClient.execute(request);
        validateResponse(response, true);
        expectedXmlPath = Paths.get(domainSecondaryLifecycleUtil.getConfiguration().getDomainDirectory()).resolve("configuration").resolve("testing-host-secondary.xml");

        request = new ModelNode();
        request.get(OP).set("read-config-as-xml");
        request.get(OP_ADDR).setEmptyList().add(HOST, "secondary");
        response = domainClient.execute(request);
        xml = validateResponse(response, true).asString().replace('\'', '\"');
        expectedXml = loadXMLConfigurationFile(expectedXmlPath);
        Assert.assertEquals(expectedXml, xml);
    }

    @Test
    public void testServerReadConfigAsXml() throws Exception {
        DomainClient domainClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode request = new ModelNode();
        request.get(OP).set("read-config-as-xml");
        request.get(OP_ADDR).setEmptyList().add(HOST, "primary").add(SERVER, "main-one");

        ModelNode response = domainClient.execute(request);
        String xml = validateResponse(response, true).asString().replace('\'', '\"');
        Path expected = new File("target").toPath().resolve("test-classes").resolve("expected-main-one.xml");
        String expectedXml = readFileAsString(expected).replaceAll(System.lineSeparator(), "\n") + "\n";
        Assert.assertEquals(expectedXml, xml + "\n");

        request = new ModelNode();
        request.get(OP).set("read-config-as-xml");
        request.get(OP_ADDR).setEmptyList().add(HOST, "secondary").add(SERVER, "main-three");
        response = domainClient.execute(request);
        xml = validateResponse(response, true).asString().replace('\'', '\"');
        expected = new File("target").toPath().resolve("test-classes").resolve("expected-main-three.xml");
        expectedXml = readFileAsString(expected).replaceAll(System.lineSeparator(), "\n");
        Assert.assertEquals(expectedXml, xml);
    }

    protected static String readFileAsString(Path file) throws Exception {
        return new String(Files.readAllBytes((file)));
    }

    protected static String loadXMLConfigurationFile(Path file) throws Exception {
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
}
