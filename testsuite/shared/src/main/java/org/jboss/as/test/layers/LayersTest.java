/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.layers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

    private static final boolean VALIDATE_INPUTS = Boolean.parseBoolean(System.getProperty("org.wildfly.layers.test.validate-inputs", "true"));

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
     * Checks that expected modules were provisioned with the @{code test-standalone-reference} and @{test-all-layers}
     * installations in the root.
     *
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
        assertNotNull("No installations found in " + root, installations);
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

        assertNotNull("No " + REFERENCE + " installation found in " + root, reference);
        assertNotNull("No " + ALL_LAYERS + " installation found in " + root, allLayers);

        StringBuilder exceptionBuilder = new StringBuilder();
        AtomicBoolean empty = new AtomicBoolean(true);

        // Check that the reference has no more un-referenced modules than the expected ones.
        Set<String> allUnReferenced = new HashSet<>();
        allUnReferenced.addAll(unused);
        allUnReferenced.addAll(unreferenced);
        final Set<String> refUnreferenced = reference.getNotReferenced();
        String invalidUnref = listModules(refUnreferenced, m -> !allUnReferenced.contains(m));
        if (!invalidUnref.isEmpty()) {
            appendExceptionMsg(exceptionBuilder, "Some unreferenced modules are unexpected " + invalidUnref, empty);
        }

        StringBuilder builder = new StringBuilder();
        appendResult("REFERENCE", reference, builder, null);
        // Format "all layers" result and compute the set of modules that have not been provisioned
        // against the reference.
        Set<String> deltaModules = appendResult("ALL LAYERS", allLayers, builder, reference);

        for (String k : layers.keySet()) {
            appendResult(k, layers.get(k), builder, reference);
        }

        // The only modules that are expected to be not provisioned are the un-used ones.
        // If more are not provisioned, then we are missing some modules.
        String missingRequired = listModules(deltaModules, m -> !unused.contains(m));
        if (!missingRequired.isEmpty()) {
            String error = "Some expected modules have not been provisioned in " + ALL_LAYERS;
            builder.append("#!!!!!ERROR ").append(error).append("\n");
            builder.append("error_missing_modules=").append(missingRequired).append("\n");
            appendExceptionMsg(exceptionBuilder, error + ": " + missingRequired, empty);
        }

        if (VALIDATE_INPUTS) {

            // Confirm that 'unused' and 'unreferenced' modules actually exist in the reference installation
            final Set<String> allRefModules = reference.getModules();
            String missingDeclared = listModules(allUnReferenced, m -> !allRefModules.contains(m));
            if (!missingDeclared.isEmpty()) {
                String error = "Some expected modules have not been provisioned in " + REFERENCE;
                builder.append("#!!!!!ERROR ").append(error).append("\n");
                builder.append("error_missing_modules=").append(missingDeclared).append("\n");
                appendExceptionMsg(exceptionBuilder, error + ": " + missingDeclared, empty);
            }

            // Confirm that 'unused' modules do not exist in the all-layers installation
            final Set<String> allLayersModules = allLayers.getModules();
            String wrongUnused = listModules(unused, allLayersModules::contains);
            if (!wrongUnused.isEmpty()) {
                String error = "Some expected to be unused modules have been provisioned in " + ALL_LAYERS;
                builder.append("#!!!!!ERROR ").append(error).append("\n");
                builder.append("error_missing_modules=").append(wrongUnused).append("\n");
                appendExceptionMsg(exceptionBuilder, error + ": " + wrongUnused, empty);
            }

            // Confirm that 'unreferenced' modules are not referenced in the reference installation
            String wrongUnreferenced = listModules(unreferenced, m -> !refUnreferenced.contains(m));
            if (!wrongUnreferenced.isEmpty()) {
                String error = "Some expected to be unreferenced modules are referenced in " + REFERENCE;
                builder.append("#!!!!!ERROR ").append(error).append("\n");
                builder.append("error_missing_modules=").append(wrongUnreferenced).append("\n");
                appendExceptionMsg(exceptionBuilder, error + ": " + wrongUnreferenced, empty);
            }
        }

        File resFile = new File(root, "results.properties");
        Files.write(resFile.toPath(), builder.toString().getBytes());

        String exception = exceptionBuilder.toString();
        if (!exception.isEmpty()) {
            fail(exception);
        }
    }

    /**
     * Checks the installations found in the given {@code root} directory can all be started without errors, i.e.
     * with the {@code WFLYSRV0025} log message in the server's stdout stream.
     *
     * The @{code test-standalone-reference} installation is not tested as that kind of installation is heavily
     * tested elsewhere.
     *
     * @param root Installations root directory
     * @throws Exception on failure
     */
    public static void testExecution(String root) throws Exception {
        File[] installations = new File(root).listFiles(File::isDirectory);
        assertNotNull("No installations found in " + root, installations);
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

    private static void appendExceptionMsg(StringBuilder existing, String toAppend, AtomicBoolean empty) {
        if (!empty.getAndSet(false)) {
            existing.append('\n');
        }
        existing.append(toAppend);
    }

    private static String listModules(Set<String> set, Predicate<String> predicate) {
        return set.stream()
                .filter(predicate)
                .collect(Collectors.joining(","));
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
