/*
 * Copyright 2016-2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.layers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jboss.as.test.shared.TimeoutUtil;
import org.w3c.dom.Document;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.xml.sax.SAXException;

/**
 *
 * @author jdenise@redhat.com
 */
public class LayersTest {

    private static final String MAVEN_REPO_LOCAL = "maven.repo.local";
    private static final String REFERENCE = "test-standalone-reference";
    private static final String ALL_LAYERS = "test-all-layers";
    private static final String END_LOG_SUCCESS = "WFLYSRV0025";
    private static final String END_LOG_FAILURE = "WFLYSRV0026";

    /**
     * Scan and check an installation.
     * @param root Path to installation
     * @param unreferenced The set of modules that are present in a default installation (with all modules
     * installed) but are not referenced from the module graph. They are not referenced because they are not used,
     * or they are only injected at runtime into deployment unit or are part of extension not present in the
     * default configuration (eg: deployment-scanner in core standalone.xml configuration). We are checking that
     * the default configuration (that contains all modules) doesn't have more unreferenced modules than this set. If
     * there are more it means that some new modules have been introduced and we must understand why (eg: a subsystem inject
     * a new module into a Deployment Unit, the subsystem must advertise it and the test must be updated with this new unreferenced module).
     * @param unused The set of modules that are OK to not be provisioned when all layers are provisioned.
     * If more modules than this set are not provisioned, it means that we are missing some modules and
     * an error occurs.
     * @throws Exception on failure
     */
    @Deprecated
    public static void test(String root, Set<String> unreferenced, Set<String> unused) throws Exception {
        testExecution(root);
        testDeployedModules(root, unreferenced, unused);
    }

    /**
     * Checks only allowed modules were loaded with the installation
     * @param root Path to installation
     * @param unreferenced The set of modules that are present in a default installation (with all modules
     * installed) but are not referenced from the module graph. They are not referenced because they are not used,
     * or they are only injected at runtime into deployment unit or are part of extension not present in the
     * default configuration (eg: deployment-scanner in core standalone.xml configuration). We are checking that
     * the default configuration (that contains all modules) doesn't have more unreferenced modules than this set. If
     * there are more it means that some new modules have been introduced and we must understand why (eg: a subsystem inject
     * a new module into a Deployment Unit, the subsystem must advertise it and the test must be updated with this new unreferenced module).
     * @param unused The set of modules that are OK to not be provisioned when all layers are provisioned.
     * If more modules than this set are not provisioned, it means that we are missing some modules and
     * an error occurs.
     * @throws Exception on failure
     */
    public static void testDeployedModules(String root, Set<String> unreferenced, Set<String> unused) throws Exception {
        File[] installations = new File(root).listFiles(File::isDirectory);
        Result reference = null;
        Result allLayers = null;
        Map<String, Result> layers = new TreeMap<>();
        for (File f : installations) {
            Path installation = f.toPath();
            Result res = Scanner.scan(installation, getConf(installation));
            if (f.getName().equals(REFERENCE)) {
                reference = res;
            } else if (f.getName().equals(ALL_LAYERS)) {
                allLayers = res;
            } else {
                layers.put(f.getName(), res);
            }
        }

        // Check that the reference has no more un-referenced modules than the expected ones.
        Set<String> invalidUnref = new HashSet<>();
        Set<String> allUnReferenced = new HashSet<>();
        allUnReferenced.addAll(unused);
        allUnReferenced.addAll(unreferenced);
        for (String unref : reference.getNotReferenced()) {
            if (!allUnReferenced.contains(unref)) {
                invalidUnref.add(unref);
            }
        }
        if (!invalidUnref.isEmpty()) {
            throw new Exception("Some unreferenced modules are un-expected " + invalidUnref);
        }
        StringBuilder builder = new StringBuilder();
        appendResult("REFERENCE", reference, builder, null);
        // Format "all layers" result and compute the set of modules that have not been provisioned
        // against the reference.
        Set<String> deltaModules = appendResult("ALL LAYERS", allLayers, builder, reference);

        for (String k : layers.keySet()) {
            appendResult(k, layers.get(k), builder, reference);
        }
        Exception exception = null;
        // The only modules that are expected to be not provisioned are the un-used ones.
        // If more are not provisioned, then we are missing some modules.
        if (!unused.containsAll(deltaModules)) {
            builder.append("#!!!!!ERROR, some required modules have not been provisioned\n");
            StringBuilder b = new StringBuilder();
            for (String m : deltaModules) {
                if (!unused.contains(m)) {
                    b.append(m).append(",");
                }
            }
            builder.append("error_missing_modules=" + b).append("\n");
            exception = new Exception("ERROR, some modules have not been provisioned: " + b);
        }
        File resFile = new File(root, "results.properties");
        Files.write(resFile.toPath(), builder.toString().getBytes());
        if (exception != null) {
            throw exception;
        }
    }

    /**
     * Checks the expected modules were provisioned with the @{code test-standalone-reference} and @{test-all-layers} installations in the root.
     * @param root Installations root directory
     * @throws Exception
     */
    public static void testExecution(String root) throws Exception {
        File[] installations = new File(root).listFiles(File::isDirectory);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            for (File f : installations) {
                Path installation = f.toPath();
                if (!f.getName().equals(REFERENCE)) {
                    checkExecution(executor, installation);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void checkExecution(ExecutorService executor,
            Path installation) throws Exception {
        StringBuilder str = new StringBuilder();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    StandaloneCommandBuilder builder = StandaloneCommandBuilder.of(installation);
                    String localRepo = System.getProperty(MAVEN_REPO_LOCAL);
                    if (localRepo != null) {
                        builder.addJavaOption("-D" + MAVEN_REPO_LOCAL + "=" + localRepo);
                    } else {
                        System.out.println("Warning, no Maven local repository set.");
                    }
                    ProcessBuilder p = new ProcessBuilder(builder.build());
                    p.redirectErrorStream(true);
                    Process process = p.start();
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        String line;
                        boolean ended = false;
                        while ((line = reader.readLine()) != null) {
                            str.append(line).append("\n");
                            // We are only checking for server view on errors.
                            // Some other errors could occur (eg: multicast load-balancer on some platforms)
                            // but these errors are not caused by server configuration.
                            if (line.contains(END_LOG_FAILURE)) {
                                throw new Exception("Process for " + installation.getFileName() +
                                    " started with errors.");
                            } else {
                                if (line.contains(END_LOG_SUCCESS)) {
                                    ended = true;
                                    break;
                                }
                            }
                        }
                        if (!ended) {
                            throw new Exception("Process for " + installation.getFileName() +
                                    " not terminated properly.");
                        }
                    } finally {
                        process.destroyForcibly();
                        process.waitFor();
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
        try {
            executor.submit(r).get(TimeoutUtil.adjust(1), TimeUnit.MINUTES);
        } catch (Exception ex) {
            throw new Exception("Exception checking " + installation.getFileName().toString()
                    + "\n Server log \n" + str.toString(), ex);
        }
    }

    public static void recursiveDelete(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    try {
                        Files.delete(file);
                    } catch (IOException ex) {
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e)
                        throws IOException {
                    if (e == null) {
                        try {
                            Files.delete(dir);
                        } catch (IOException ex) {
                        }
                        return FileVisitResult.CONTINUE;
                    } else {
                        // directory iteration failed
                        throw e;
                    }
                }
            });
        } catch (IOException e) {
        }
    }

    private static Path getConf(Path home) {
        return Paths.get(home.toString(), "standalone", "configuration", "standalone.xml");
    }

    private static Set<String> appendResult(String title, Result result, StringBuilder builder, Result reference) {
        long sizeDelta = -1;
        int numModulesdelta = -1;
        Map<String, Set<String>> optionals = new TreeMap<>();
        builder.append("# " + title).append("\n");
        StringBuilder extensions = new StringBuilder();
        for (Result.ExtensionResult r : result.getExtensions()) {
            extensions.append(r.getModule() + ",");
        }
        builder.append("extensions=" + extensions + "\n");
        Set<String> deltaModules = new TreeSet<>();
        if (reference != null) { // Compare against reference.
            sizeDelta = result.getSize() - reference.getSize();
            numModulesdelta = result.getModules().size() - reference.getModules().size();
            // Compute the set of unreferenced optionals that are only present in the checked installation.
            for (Map.Entry<String, Set<String>> entry : result.getUnresolvedOptional().entrySet()) {
                if (!reference.getUnresolvedOptional().containsKey(entry.getKey())) {
                    optionals.put(entry.getKey(), entry.getValue());
                }
            }

            // Compute the set of modules that have not been provisioned.
            for (String m : reference.getModules()) {
                if (!result.getModules().contains(m)) {
                    deltaModules.add(m);
                }
            }
            builder.append("size=" + result.getSize()).append("\n");
            builder.append("size_delta=" + sizeDelta).append("\n");
            builder.append("num_modules=" + result.getModules().size()).append("\n");
            builder.append("num_modules_delta=" + numModulesdelta).append("\n");
            builder.append("num_new_unresolved=" + optionals.size()).append("\n");
        } else {
            optionals = result.getUnresolvedOptional();
            builder.append("size=" + result.getSize()).append("\n");
            builder.append("num_modules=" + result.getModules().size()).append("\n");
            builder.append("num_modules_not_referenced=" + result.getNotReferenced().size()).append("\n");
            StringBuilder notReferenced = new StringBuilder();
            for (String s : result.getNotReferenced()) {
                notReferenced.append(s).append(",");
            }
            builder.append("unreferenced_modules=" + notReferenced + "\n");
            builder.append("num_unresolved=" + optionals.size()).append("\n");
        }
        for (Map.Entry<String, Set<String>> entry : optionals.entrySet()) {
            StringBuilder roots = new StringBuilder();
            for (String s : entry.getValue()) {
                roots.append(s).append(",");
            }
            builder.append(entry.getKey() + "=" + roots.toString() + "\n");
        }

        builder.append("num_not_provisioned_modules=" + deltaModules.size()).append("\n");

        if (!deltaModules.isEmpty()) {
            StringBuilder mods = new StringBuilder();
            for (String s : deltaModules) {
                mods.append(s).append(",");
            }
            builder.append("not_provisioned_modules=" + mods.toString()).append("\n");
        }

        builder.append("\n");
        return deltaModules;
    }

    /**
     * Walks the modules directory of each installation getting as result the list of banned modules found.
     *
     * @param root             The root path of the installations.
     * @param bannedModuleConf A HashMap with the banned module as a key and an optional list with the installation names
     *                         that should be ignored if contains the banned module name.
     *
     * @return An empty Hash map if no banned modules were found or a hash map containing as keys the path where the
     * banned module was found and as values the banned module name.
     * @throws ParserConfigurationException if a DocumentBuilder cannot be created.
     * @throws IOException                  if an I/O error occurs or if the module.xml cannot be parsed.
     */
    public static HashMap<String, String> checkBannedModules(String root, HashMap<String, List<String>> bannedModuleConf) throws ParserConfigurationException, IOException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

        HashMap<String, String> results = new HashMap<>();
        File[] installations = new File(root).listFiles(File::isDirectory);
        for (File installation : installations) {
            Files.walkFileTree(installation.toPath().resolve("modules"), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().equals("module.xml")) {
                        Document doc;
                        try {
                            doc = dBuilder.parse(file.toFile());
                            doc.getDocumentElement().normalize();
                            String moduleName = doc.getDocumentElement().getAttribute("name");
                            List<String> ignoredInstallations = bannedModuleConf.get(moduleName);
                            if (ignoredInstallations != null && !ignoredInstallations.contains(installation.getName())) {
                                results.put(file.toString(), moduleName);
                            }
                        } catch (SAXException e) {
                            throw new IOException(e);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return results;
    }
}
