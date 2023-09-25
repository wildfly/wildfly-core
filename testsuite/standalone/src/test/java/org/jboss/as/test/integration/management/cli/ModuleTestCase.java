/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.codehaus.plexus.util.FileUtils;
import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.xnio.IoUtils;

/**
 * This tests 'module add/remove' CLI command.
 *
 * @author Ivo Studensky
 */
@RunWith(WildFlyRunner.class)
public class ModuleTestCase extends AbstractCliTestBase {

    private static final String MODULE_NAME = "org.jboss.test.cli.climoduletest";

    private static File jarFile;
    private static File jarFile2;
    private static File customModulesDirectory;

    @BeforeClass
    public static void beforeClass() throws Exception {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "Dummy.jar");
        jar.addClass(ModuleTestCase.class);
        jarFile = new File(TestSuiteEnvironment.getTmpDir() + File.separator + "Dummy.jar");
        jarFile2 = new File(TestSuiteEnvironment.getTmpDir() + File.separator + "Dummy2.jar");
        new ZipExporterImpl(jar).exportTo(jarFile, true);
        new ZipExporterImpl(jar).exportTo(jarFile2, true);

        // Create an empty directory
        customModulesDirectory = new File(TestSuiteEnvironment.getTmpDir(),
                System.currentTimeMillis() + "-mymodules");
        customModulesDirectory.mkdir();

        AbstractCliTestBase.initCLI();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        jarFile.delete();
        jarFile2.delete();
        FileUtils.deleteDirectory(customModulesDirectory);
        AbstractCliTestBase.closeCLI();
    }

    @Test
    public void userModulesPath() throws Exception {
        testAddRemove("main", true, true, true, false, false, false);
    }

    @Test
    public void addRemoveModuleWithResources() throws Exception {
        testAddRemove("main", false, true, false, false, false, false);
    }

    @Test
    public void addRemoveModuleWithResourcesAndDependencies() throws Exception {
        testAddRemove("main", false, true, false, false, true, false);
    }

    @Test
    public void addRemoveModuleWithResourcesAndProperties() throws Exception {
        testAddRemove("main", false, true, false, true, false, false);
    }

    @Test
    public void addRemoveModuleWithResourcesAndMainClass() throws Exception {
        testAddRemove("main", false, true, false, false, false, true);
    }

    @Test
    public void addRemoveModuleWithResourcesAndPropertiesAndDependencies() throws Exception {
        testAddRemove("main", false, true, false, true, true, false);
    }

    @Test
    public void addRemoveModuleWithResourcesAndPropertiesAndMainClass() throws Exception {
        testAddRemove("main", false, true, false, true, false, true);
    }

    @Test
    public void addRemoveModuleWithResourcesAndDependenciesAndMainClass() throws Exception {
        testAddRemove("main", false, true, false, false, true, true);
    }

    @Test
    public void addRemoveModuleWithResourcesAndPropertiesAndDependenciesAndMainClass() throws Exception {
        testAddRemove("main", false, true, false, true, true, true);
    }

    @Test
    public void addRemoveModuleWithAbsoluteResources() throws Exception {
        testAddRemove("main", false, false, true, false, false, false);
    }

    @Test
    public void addRemoveModuleWithBothTypeResources() throws Exception {
        testAddRemove("main", false, true, true, false, false, false);
    }

    @Test
    public void addRemoveModuleNonDefaultSloteWithResources() throws Exception {
        testAddRemove("2.0", false, true, false, false, false, false);
    }

    @Test
    public void addRemoveModuleNonDefaultSloteWithAbsoluteResources() throws Exception {
        testAddRemove("2.0", false, false, true, false, false, false);
    }

    @Test
    public void addRemoveModuleNonDefaultSloteWithBothTypeResources() throws Exception {
        testAddRemove("2.0", false, true, true, false, false, false);
    }

    // test for WFLY-1896
    @Test
    public void addRemoveModuleMetaInf() throws Exception {
        final String slot = "main";
        testModuleAndSlotExist(false, slot, getModulePath());
        testAdd(slot, false, true, true, false, false, false);
        testModuleFiles(slot, getModulePath(), true, true, false, false, false);

        // create a META-INF directory inside the module
        final File metaInfDir = new File(getModulePath(), MODULE_NAME.replace('.', File.separatorChar) + File.separator + "main" + File.separator + "META-INF");
        if (!metaInfDir.mkdirs()) {
            fail("Could not create " + metaInfDir);
        }
        PrintWriter out = null;
        try {
            out = new PrintWriter(new File(metaInfDir, "version.txt"));
            out.println("main");
        } finally {
            IoUtils.safeClose(out);
        }

        testRemove(slot, false);
        testModuleAndSlotExist(false, slot, getModulePath());
    }

    @Test
    public void addModuleWithWhitespacesInDependencies() throws Exception {
        // should trim the surrounding whitespaces
        try {
            cli.sendLine("module add --name=" + MODULE_NAME
                    + " --resources=" + jarFile.getAbsolutePath()
                    + " --dependencies=[ org.jboss.logging ]"
                    + " --export-dependencies=[ org.jboss.logmanager ]");
            testModuleFiles("main", getModulePath(), false, false, false, true, false);
        } finally {
            testRemove("main", false);
        }
    }

    @Test
    public void addModuleWithEmptyDirectory() throws Exception {
        String dir = System.currentTimeMillis() + "dir";
        File dirFile = new File(TestSuiteEnvironment.getTmpDir() + File.separator + dir);
        try {
            // Check that must fail for empty directory.
            boolean success = cli.sendLine("module add --name=" + MODULE_NAME
                    + " --resources=" + dirFile, true);
            Assert.assertFalse(success);

            cli.sendLine("module add --name=" + MODULE_NAME
                    + " --resources=" + dirFile + " --allow-nonexistent-resources");
            File testModuleRoot = new File(getModulePath(), MODULE_NAME.replace('.', File.separatorChar));
            File moduleDir = new File(testModuleRoot, "main");

            // check that there is a single empty directory
            File[] dirs = moduleDir.listFiles(File::isDirectory);
            Assert.assertEquals(1, dirs.length);
            Assert.assertEquals(dir, dirs[0].getName());
            Assert.assertEquals(0, dirs[0].listFiles().length);
        } finally {
            FileUtils.deleteDirectory(dirFile);
            testRemove("main", false);
        }
    }

    @Test
    public void addModuleWithDirectory() throws Exception {
        String dir = System.currentTimeMillis() + "dir";
        File dirFile = new File(TestSuiteEnvironment.getTmpDir() + File.separator + dir);
        dirFile.mkdir();
        String content = "HELLO WORLD";
        String fileName = System.currentTimeMillis() + "file.txt";
        File f = new File(dirFile, fileName);
        f.createNewFile();
        Files.write(content.getBytes(StandardCharsets.UTF_8), f);
        try {
            cli.sendLine("module add --name=" + MODULE_NAME
                    + " --resources=" + dirFile.getAbsolutePath());
            File testModuleRoot = new File(getModulePath(), MODULE_NAME.replace('.', File.separatorChar));
            File moduleDir = new File(testModuleRoot, "main");

            // check that there is a single directory
            File[] dirs = moduleDir.listFiles(File::isDirectory);
            Assert.assertEquals(1, dirs.length);
            Assert.assertEquals(dir, dirs[0].getName());
            File[] files = dirs[0].listFiles();
            Assert.assertEquals(1, files.length);
            Assert.assertEquals(fileName, files[0].getName());
            Assert.assertEquals(content, Files.readFirstLine(files[0], Charsets.UTF_8));
        } finally {
            FileUtils.deleteDirectory(dirFile);
            testRemove("main", false);
        }
    }

    @Test
    public void addModuleWithDirectoryError() throws Exception {
        if (Util.isWindows()) {
            return;
        }
        String dir = System.currentTimeMillis() + "dir";
        File dirFile = new File(TestSuiteEnvironment.getTmpDir() + File.separator + dir);
        dirFile.mkdir();
        try {
            String content = "HELLO WORLD";
            String fileName = System.currentTimeMillis() + "file.txt";
            File f = new File(dirFile, fileName);
            f.createNewFile();
            Files.write(content.getBytes(StandardCharsets.UTF_8), f);
            f.setReadable(false);
            boolean ret = cli.sendLine("module add --name=" + MODULE_NAME
                    + " --resources=" + dirFile.getAbsolutePath(), true);
            Assert.assertFalse(ret);
            String output = cli.readOutput();
            Assert.assertTrue(output.contains("Module not added"));
            File testModuleRoot = new File(getModulePath(), MODULE_NAME.replace('.', File.separatorChar));
            Assert.assertFalse(testModuleRoot.exists());
        } finally {
            FileUtils.deleteDirectory(dirFile);
        }
    }

    @Test
    public void addModuleWithDirectoryAndValidLinks() throws Exception {
        if (Util.isWindows()) {
            return;
        }

        String dir = System.currentTimeMillis() + "dir";
        File dirFile = new File(TestSuiteEnvironment.getTmpDir(), dir);
        dirFile.mkdir();
        String content = "HELLO WORLD";
        String fileName = System.currentTimeMillis() + "file.txt";

        String dir2 = System.currentTimeMillis() + "dir2";
        File dirFile2 = new File(TestSuiteEnvironment.getTmpDir(), dir2);
        dirFile2.mkdir();
        File f2 = new File(dirFile2, fileName);
        f2.createNewFile();
        Files.write(content.getBytes(StandardCharsets.UTF_8), f2);

        File subDir = new File(dirFile, "subDirectory");
        subDir.mkdir();
        File f = new File(subDir, fileName);
        f.createNewFile();
        Files.write(content.getBytes(), f);

        File link1 = new File(dirFile, "linkToSub");
        java.nio.file.Files.createSymbolicLink(link1.toPath(), subDir.toPath());

        File link2 = new File(dirFile, "linkToExt");
        java.nio.file.Files.createSymbolicLink(link2.toPath(), dirFile2.toPath());

        File link3 = new File(dirFile, "linkToFile");
        java.nio.file.Files.createSymbolicLink(link3.toPath(), f2.toPath());

        try {
            cli.sendLine("module add --name=" + MODULE_NAME
                    + " --resources=" + dirFile.getAbsolutePath());
            File testModuleRoot = new File(getModulePath(), MODULE_NAME.replace('.', File.separatorChar));
            File moduleDir = new File(testModuleRoot, "main");

            // check that there is a single directory
            File[] dirs = moduleDir.listFiles(File::isDirectory);
            Assert.assertEquals(1, dirs.length);
            Assert.assertEquals(dir, dirs[0].getName());
            File[] files = dirs[0].listFiles();
            Assert.assertEquals(4, files.length);
            Map<String, File> map = new HashMap<>();
            for (File file : files) {
                map.put(file.getName(), file);
            }
            Assert.assertTrue(map.containsKey(subDir.getName()));
            Assert.assertTrue(map.containsKey(link1.getName()));
            Assert.assertTrue(map.containsKey(link2.getName()));
            Assert.assertTrue(map.containsKey(link3.getName()));
            File l = map.get(link1.getName());
            File[] subFiles = l.listFiles();
            Assert.assertEquals(1, subFiles.length);
            Assert.assertEquals(fileName, subFiles[0].getName());
            Assert.assertEquals(content, Files.readFirstLine(subFiles[0], Charsets.UTF_8));
            Assert.assertTrue(map.containsKey(link2.getName()));
            File l2 = map.get(link2.getName());
            File[] extFiles = l2.listFiles();
            Assert.assertEquals(1, extFiles.length);
            Assert.assertEquals(fileName, extFiles[0].getName());
            Assert.assertEquals(content, Files.readFirstLine(extFiles[0], Charsets.UTF_8));
            File l3 = map.get(link3.getName());
            Assert.assertEquals(content, Files.readFirstLine(l3, Charsets.UTF_8));
        } finally {
            FileUtils.deleteDirectory(dirFile);
            FileUtils.deleteDirectory(dirFile2);
            testRemove("main", false);
        }
    }

    @Test
    public void addModuleWithDirectoryAndInvalidLinks() throws Exception {
        if (Util.isWindows()) {
            return;
        }

        String dir = System.currentTimeMillis() + "dir";
        File dirFile = new File(TestSuiteEnvironment.getTmpDir(), dir);
        dirFile.mkdir();
        try {
            File subDir = new File(dirFile, "subDirectory");
            subDir.mkdir();
            File link1 = new File(subDir, "linkToRoot");
            java.nio.file.Files.createSymbolicLink(link1.toPath(), dirFile.toPath());

            boolean ret = cli.sendLine("module add --name=" + MODULE_NAME
                    + " --resources=" + dirFile.getAbsolutePath(), true);
            Assert.assertFalse(ret);
            String output = cli.readOutput();
            Assert.assertTrue(output.contains("Module not added"));
            File testModuleRoot = new File(getModulePath(), MODULE_NAME.replace('.', File.separatorChar));
            Assert.assertFalse(testModuleRoot.exists());
        } finally {
            FileUtils.deleteDirectory(dirFile);
        }
    }

    @Test
    public void addModuleWithDirectoryAndInvalidLinks2() throws Exception {
        if (Util.isWindows()) {
            return;
        }

        String dir1 = System.currentTimeMillis() + "dir1";
        File dirFile1 = new File(TestSuiteEnvironment.getTmpDir(), dir1);
        dirFile1.mkdir();

        String dir2 = System.currentTimeMillis() + "dir2";
        File dirFile2 = new File(TestSuiteEnvironment.getTmpDir(), dir2);
        dirFile2.mkdir();

        try {
            File link = new File(dirFile1, "linkToDir2");
            java.nio.file.Files.createSymbolicLink(link.toPath(), dirFile2.toPath());

            File link2 = new File(dirFile2, "linkToDir1");
            java.nio.file.Files.createSymbolicLink(link2.toPath(), dirFile1.toPath());

            boolean ret = cli.sendLine("module add --name=" + MODULE_NAME
                    + " --resources=" + dirFile1.getAbsolutePath(), true);
            Assert.assertFalse(ret);
            String output = cli.readOutput();
            Assert.assertTrue(output, output.contains("Module not added"));
            Assert.assertFalse(output, output.contains("File name too long"));
            File testModuleRoot = new File(getModulePath(), MODULE_NAME.replace('.', File.separatorChar));
            Assert.assertFalse(testModuleRoot.exists());
        } finally {
            FileUtils.deleteDirectory(dirFile1);
            FileUtils.deleteDirectory(dirFile2);
        }
    }

    @Test
    public void addModuleWithDirectoryAndInvalidLinks3() throws Exception {
        if (Util.isWindows()) {
            return;
        }
        // Deploy A
        // A/B/linkToC
        // C/D/linkToB

        String A = "A";
        File AFile = new File(TestSuiteEnvironment.getTmpDir(), A);
        AFile.mkdir();
        String B = "B";
        File BFile = new File(AFile, B);
        BFile.mkdir();

        String C = "C";
        File CFile = new File(TestSuiteEnvironment.getTmpDir(), C);
        CFile.mkdir();
        String D = "D";
        File DFile = new File(CFile, D);
        DFile.mkdir();
        try {
            File linkToC = new File(BFile, "C");
            java.nio.file.Files.createSymbolicLink(linkToC.toPath(), CFile.toPath());

            File linkToB = new File(DFile, "B");
            java.nio.file.Files.createSymbolicLink(linkToB.toPath(), BFile.toPath());

            boolean ret = cli.sendLine("module add --name=" + MODULE_NAME
                    + " --resources=" + AFile.getAbsolutePath(), true);
            Assert.assertFalse(ret);
            String output = cli.readOutput();
            Assert.assertTrue(output, output.contains("Module not added"));
            Assert.assertFalse(output, output.contains("File name too long"));
            File testModuleRoot = new File(getModulePath(), MODULE_NAME.replace('.', File.separatorChar));
            Assert.assertFalse(testModuleRoot.exists());
        } finally {
            FileUtils.deleteDirectory(AFile);
            FileUtils.deleteDirectory(CFile);
        }
    }

    private void testAddRemove(String slotName, boolean addModuleRootDir, boolean addResources,
                               boolean addAbsoluteResources, boolean addPropeties,
                               boolean addDependencies, boolean addMainClass) throws Exception {
        File modulePath = addModuleRootDir ? customModulesDirectory : getModulePath();
        testModuleAndSlotExist(false, slotName, modulePath);
        testAdd(slotName, addModuleRootDir, addResources, addAbsoluteResources, addPropeties, addDependencies, addMainClass);
        testModuleFiles(slotName, modulePath, addResources, addAbsoluteResources, addPropeties, addDependencies, addMainClass);
        testRemove(slotName, addModuleRootDir);
        testModuleAndSlotExist(false, slotName, modulePath);
    }

    private void testAdd(String slotName, boolean addModuleRootDir, boolean addResources, boolean addAbsoluteResources,
                         boolean addProperties, boolean addDependencies, boolean addMainClass) throws Exception {
        // create a module
        cli.sendLine("module add --name=" + MODULE_NAME
                + ("main".equals(slotName) ? "" : " --slot=" + slotName)
                + (addResources ? " --resources=" + jarFile.getAbsolutePath() : "")
                + (addAbsoluteResources ? " --absolute-resources= " + jarFile2.getAbsolutePath() : "")
                + (addModuleRootDir ? " --module-root-dir=" + customModulesDirectory.getAbsolutePath() : "")
                + (addProperties ? " --properties=bat=man" : "")
                + (addDependencies ? " --dependencies=org.jboss.logging" : "")
                + (addDependencies ? " --export-dependencies=org.jboss.logmanager" : "")
                + (addMainClass ? " --main-class=org.jboss.Test" : ""));
    }

    private void testRemove(String slotName, boolean addModuleRootDir) throws Exception {
        // remove the module
        cli.sendLine("module remove --name=" + MODULE_NAME
                + ("main".equals(slotName) ? "" : " --slot=" + slotName)
                + ( addModuleRootDir ? " --module-root-dir=" + customModulesDirectory.getAbsolutePath() : ""));
    }

    private void testModuleExist(boolean ifExists, File modulesPath) throws Exception{
        File testModuleRoot = new File(modulesPath, MODULE_NAME.replace('.', File.separatorChar));
        assertTrue("Invalid state of module directory: " + testModuleRoot.getAbsolutePath() +
                (ifExists ? " does not exist" : " exists"), ifExists == testModuleRoot.exists());
    }

    private void testSlotExist(boolean ifExists, String slotName, File modulesPath) throws Exception{
        File testModuleRoot = new File(modulesPath, MODULE_NAME.replace('.', File.separatorChar));
        File slot = new File(testModuleRoot, slotName);
        assertTrue("Invalid state of slot directory: " + slot.getAbsolutePath() +
                (ifExists ? " does not exist" : " exists"), ifExists == slot.exists());
    }

    private void testModuleAndSlotExist(boolean ifExists, String slotName, File modulesPath) throws Exception {
        testModuleExist(ifExists, modulesPath);
        testSlotExist(ifExists, slotName, modulesPath);
    }

    private void testModuleFiles(String slotName, File modulesPath, boolean verifyResources,
                                 boolean verifyAbsoluteResources, boolean verifyProperties,
                                 boolean verifyDependencies, boolean verifyMainClass) throws Exception {
        testModuleAndSlotExist(true, slotName, modulesPath);
        checkModuleFiles(slotName, modulesPath, verifyResources);
        checkModuleXml(slotName, modulesPath, verifyResources, verifyAbsoluteResources, verifyProperties,
                verifyDependencies, verifyMainClass);
    }

    private File getModulePath() {
        String modulePath = TestSuiteEnvironment.getSystemProperty("module.path", null);
        if (modulePath == null) {
            String jbossHome = TestSuiteEnvironment.getSystemProperty("jboss.dist", null);
            if (jbossHome == null) {
                throw new IllegalStateException(
                        "Neither -Dmodule.path nor -Djboss.home were set");
            }
            modulePath = jbossHome + File.separatorChar + "modules";
        } else {
            modulePath = modulePath.split(File.pathSeparator)[0];
        }
        File moduleDir = new File(modulePath);
        if (!moduleDir.exists()) {
            throw new IllegalStateException(
                    "Determined module path does not exist");
        }
        if (!moduleDir.isDirectory()) {
            throw new IllegalStateException(
                    "Determined module path is not a dir");
        }
        return moduleDir;
    }

    private void checkModuleFiles(String slot, File modulesPath, boolean checkResources) {
        File testModuleRoot = new File(modulesPath, MODULE_NAME.replace('.', File.separatorChar));
        File dir = new File(testModuleRoot, slot);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Cannot list files, " + dir.getAbsolutePath() + " is not directory");
        }

        // check that there is no directory
        File[] dirs = dir.listFiles(File::isDirectory);
        assertTrue("Module shouldn't contain any directory, but it contains " + dirs.length + " + directories.",
                dirs.length == 0);

        // check that module contains only module.xml and expected files
        List<String> files = Arrays.asList(dir.list((directory, name) -> {
            File f = new File(directory, name);
            return !f.isDirectory();

        }));
        assertTrue("Module should contain module.xml", files.contains("module.xml"));

        if(checkResources) {
            assertTrue("Module should contain module.xml", files.contains("Dummy.jar"));
            assertTrue("Module should contain only module.xml and Dummy.jar, but there is more files: " + files.toString(),
                    files.size() == 2);
        }
    }

    private void checkModuleXml(String slotName, File modulesPath, boolean checkResources,
                                boolean checkAbsoluteResources, boolean checkProperties,
                                boolean checkDependencies, boolean checkMainClass) throws Exception {
        File testModuleRoot = new File(modulesPath, MODULE_NAME.replace('.', File.separatorChar));
        File slot = new File(testModuleRoot, slotName);
        File moduleXml = new File(slot, "module.xml");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(moduleXml);

        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();

        // check resource-root
        XPathExpression pathAttrExpr = xpath.compile("/module/resources/resource-root/@path");
        NodeList nl = (NodeList) pathAttrExpr.evaluate(doc, XPathConstants.NODESET);
        List<String> paths = new ArrayList<>(2);
        for (int i = 0; i < nl.getLength(); i++) {
            paths.add(nl.item(i).getNodeValue());
        }
        if (checkResources && checkAbsoluteResources) {
            assertTrue("module.xml should contain two resource-root elements", nl.getLength() == 2);
            assertTrue("module.xml should contain resource-root element with atrribute path=\"Dummy.jar\"",
                    paths.contains("Dummy.jar"));
            assertTrue("module.xml should contain resource-root element with atrribute path=\"" + jarFile2.getCanonicalPath() +"\"",
                    paths.contains(jarFile2.getCanonicalPath()));
        } else if (checkResources && !checkAbsoluteResources) {
            assertTrue("module.xml should contain one resource-root elements", nl.getLength() == 1);
            assertTrue("module.xml should contain resource-root element with atrribute path=\"Dummy.jar\"",
                    paths.contains("Dummy.jar"));
        } else if (!checkResources && checkAbsoluteResources) {
            assertTrue("module.xml should contain one resource-root elements", nl.getLength() == 1);
            assertTrue("module.xml should contain resource-root element with atrribute path=\"" + jarFile2.getCanonicalPath() +"\"",
                    paths.contains(jarFile2.getCanonicalPath()));
        }

        // check properties
        XPathExpression propertiesExpr = xpath.compile("/module/properties/property");
        nl = (NodeList) propertiesExpr.evaluate(doc, XPathConstants.NODESET);
        if(checkProperties) {
            assertTrue("module.xml should contain one property but it has " + nl.getLength() + " properties", nl.getLength() == 1);
            NamedNodeMap attributes = nl.item(0).getAttributes();
            String name = attributes.getNamedItem("name").getNodeValue();
            String value = attributes.getNamedItem("value").getNodeValue();
            assertTrue("module.xml should contain property bat=man", name.equals("bat") && value.equals("man"));
        } else {
            assertTrue("module.xml shouldn't contain any properties but it has " + nl.getLength() + " properties", nl.getLength() == 0);
        }

        // check dependencies
        XPathExpression dependenciesNameAttrExpr = xpath.compile("/module/dependencies/module/@name");
        nl = (NodeList) dependenciesNameAttrExpr.evaluate(doc, XPathConstants.NODESET);
        if (checkDependencies) {
            assertTrue("module.xml should contain two resource-root elements", nl.getLength() == 2);
            assertTrue("module.xml should contain module element with atrribute name=\"org.jboss.logging\"",
                    nl.item(0).getNodeValue().equals("org.jboss.logging"));
            assertTrue("module.xml should contain module element with atrribute name=\"org.jboss.logmanager\"",
                    nl.item(1).getNodeValue().equals("org.jboss.logmanager"));
        } else {
            assertTrue("module.xml shouldn't contain any dependencies but it has " + nl.getLength() + " dependencies", nl.getLength() == 0);
        }

        XPathExpression exportDependenciesNameAttrExpr = xpath.compile("/module/dependencies/module/@export");
        nl = (NodeList) exportDependenciesNameAttrExpr.evaluate(doc, XPathConstants.NODESET);
        if (checkDependencies) {
            assertTrue("module.xml should contain one resource-root elements", nl.getLength() == 1);
            assertTrue("module.xml should contain module element with atrribute export=true",
                    nl.item(0).getNodeValue().equals("true"));
        } else {
            assertTrue("module.xml shouldn't contain any dependencies but it has " + nl.getLength() + " dependencies", nl.getLength() == 0);
        }

        // check main class
        XPathExpression mainClassNameAttrExpr = xpath.compile("/module/main-class/@name");
        nl = (NodeList) mainClassNameAttrExpr.evaluate(doc, XPathConstants.NODESET);
        if(checkMainClass) {
            assertTrue("module.xml should contain main-class element", nl.getLength() == 1);
            assertTrue("module.xml should contain main-class element with atrribute name=\"org.jboss.Test\"",
                    nl.item(0).getNodeValue().equals("org.jboss.Test"));
        } else {
            assertTrue("module.xml shouldn't contain main-class element", nl.getLength() == 0);
        }

    }

}
