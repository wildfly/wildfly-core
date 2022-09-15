/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.as.test.integration.domain.management.cli;

import static java.nio.file.Files.readAllLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 * Test for providing profile cloning ability (runtime (CLI)) to create
 * new profiles based on existing JBoss profiles.
 *
 * Test clone "default" profile to "clone-profile-test-case-default" profile.
 *
 * https://issues.jboss.org/browse/WFCORE-797
 *
 * @author Marek Kopecky <mkopecky@redhat.com>
 */
public class CloneProfileTestCase extends AbstractCliTestBase {

    private static Logger log = Logger.getLogger(CloneProfileTestCase.class);

    private static final String ORIGINAL_PROFILE = "default";

    private static final String NEW_PROFILE = "clone-profile-test-case-default";

    /**
     * Domain configuration
     */
    private static File domainCfg;

    @BeforeClass
    public static void beforeClass() throws Exception {
        DomainTestSupport domainSupport = CLITestSuite.createSupport(CloneProfileTestCase.class.getSimpleName());
        AbstractCliTestBase.initCLI(DomainTestSupport.primaryAddress);

        File primaryDir = new File(domainSupport.getDomainPrimaryConfiguration().getDomainDirectory());
        domainCfg = new File(primaryDir, "configuration"
                + File.separator + "testing-domain-standard.xml");
    }

    @AfterClass
    public static void afterClass() throws Exception {
        AbstractCliTestBase.closeCLI();
        CLITestSuite.stopSupport();
    }

    /**
     * Sends command line to CLI, validate and return output.
     *
     * @param line command line
     * @return CLI output
     */
    private String cliRequest(String line, boolean successRequired) {
        log.info(line);
        cli.sendLine(line);
        String output = cli.readOutput();
        if (successRequired) {
            assertTrue("CLI command \"" + line + " doesn't contain \"success\"", output.contains("success"));
        }
        return output;
    }

    private String readFileToString(File file) throws IOException {
        StringBuilder builder = new StringBuilder();
        for(String line : readAllLines(file.toPath())){
            builder.append(line);
        }
        return builder.toString();
    }

    @Test
    public void testProfile() throws IOException, SAXException, ParserConfigurationException {
        // get domain configuration
        String domainCfgContent = readFileToString(domainCfg);
        assertFalse("Domain configuration is not initialized correctly.", domainCfgContent.contains(NEW_PROFILE));

        // clone profile
        cliRequest("/profile=" + ORIGINAL_PROFILE + ":clone(to-profile=" + NEW_PROFILE + ")", true);

        // get and check submodules
        String originSubmodules = cliRequest("ls /profile=" + ORIGINAL_PROFILE + "/subsystem", false);
        String newSubmodules = cliRequest("ls /profile=" + NEW_PROFILE + "/subsystem", false);
        assertEquals("New profile has different submodules than origin profile.", originSubmodules, newSubmodules);

        // check domain configuration
        domainCfgContent = readFileToString(domainCfg);
        assertTrue("Domain configuration doesn't contain " + NEW_PROFILE + " profile.", domainCfgContent.contains(NEW_PROFILE));

        //check xml
        checkXML();

        // Remove the new profile (WFCORE-808 test)
        cliRequest("/profile=" + NEW_PROFILE + ":remove", true);

        // check domain configuration
        domainCfgContent = readFileToString(domainCfg);
        assertFalse("Domain configuration still contains " + NEW_PROFILE + " profile.", domainCfgContent.contains(NEW_PROFILE));

    }

    /**
     * Check subsystems from original and new profile
     */
    private void checkXML() throws ParserConfigurationException, IOException, SAXException {
        // parse xml
        DocumentBuilder dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = dBuilder.parse(domainCfg);

        // get subsystems
        List<String> originalSubsystemsInXML = findProfileSubsystemsInXML(doc, ORIGINAL_PROFILE);
        List<String> newSubsystemsInXML = findProfileSubsystemsInXML(doc, NEW_PROFILE);

        // basic check
        if (originalSubsystemsInXML == null || newSubsystemsInXML == null) {
            Assert.fail("Error during parsing domain.xml file");
        }
        if (originalSubsystemsInXML.size() != newSubsystemsInXML.size()) {
            Assert.fail("Different subsystems in original and new profile in domain.xml file");
        }

        // log subsystems
        log.info("Subsystems in original profile in xml: " + originalSubsystemsInXML);
        log.info("Subsystems in new profile in xml: " + newSubsystemsInXML);

        // check order
        Iterator<String> itOriginal = originalSubsystemsInXML.iterator();
        Iterator<String> itNew = newSubsystemsInXML.iterator();
        while (itOriginal.hasNext()) {
            String originalSubsystem = itOriginal.next();
            String newSubsystem = itNew.next();
            if (!originalSubsystem.equals(newSubsystem)) {
                Assert.fail("Different order of subsystems in cloned profile.");
            }
        }
    }

    /**
     * Parse root node
     */
    private List<String> findProfileSubsystemsInXML(Document doc, String profileName) {
        List<String> output = null;
        if (!doc.hasChildNodes()) {
            return output;
        }
        NodeList nodeList = doc.getChildNodes();
        return findProfileSubsystems(nodeList, profileName);
    }

    /**
     * Find profile node in XML
     */
    private List<String> findProfileSubsystems(NodeList nodeList, String profileName) {
        List<String> output = null;
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node tempNode = nodeList.item(i);
            if (tempNode.getNodeType() == Node.ELEMENT_NODE) {
                String nodeName = tempNode.getNodeName();
                if (!nodeName.contains("domain") && !nodeName.contains("profiles") && !nodeName.contains("profile")) {
                    continue;
                }
                if (nodeName.equals("profile") && tempNode.hasAttributes()) {
                    NamedNodeMap nodeMap = tempNode.getAttributes();
                    for (int j = 0; j < nodeMap.getLength(); j++) {
                        Node node = nodeMap.item(j);
                        if (node.getNodeName().equals("name") && node.getNodeValue().equals(profileName)) {
                            return findSubsystemsInProfile(tempNode.getChildNodes());
                        }
                    }
                }
                if (!nodeName.equals("profile") && tempNode.hasChildNodes()) {
                    return findProfileSubsystems(tempNode.getChildNodes(), profileName);
                }
            }
        }
        return output;
    }

    /**
     * Find subsystems from profile node
     */
    private List<String> findSubsystemsInProfile(NodeList nodeList) {
        List<String> output = new LinkedList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node tempNode = nodeList.item(i);
            if (tempNode.getNodeType() == Node.ELEMENT_NODE) {
                String nodeName = tempNode.getNodeName();
                if (!nodeName.contains("subsystem")) {
                    continue;
                }
                if (tempNode.hasAttributes()) {
                    NamedNodeMap nodeMap = tempNode.getAttributes();
                    for (int j = 0; j < nodeMap.getLength(); j++) {
                        Node node = nodeMap.item(j);
                        if (node.getNodeName().equals("xmlns")) {
                            output.add(node.getNodeValue());
                        }
                    }
                }
            }
        }
        return output;
    }

}
