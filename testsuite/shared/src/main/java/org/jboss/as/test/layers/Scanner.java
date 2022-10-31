/*
 * Copyright 2016-2018 Red Hat, Inc. and/or its affiliates
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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author jdenise@redhat.com
 */
public class Scanner {

    public static Result scan(Path homePath, Path configFile) throws Exception {
        //Scan the modules present in an installation
        Path modulePath = Paths.get(homePath.toString(), "modules/system/layers/base");
        if (!Files.exists(modulePath)) {
            throw new Exception("Invalid JBOSS Home");
        }
        Map<String, Set<String>> optionalDependencies = new TreeMap<>();
        List<Long> size = new ArrayList<>();
        size.add((long) 0);
        Files.walkFileTree(homePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                size.set(0, size.get(0) + attrs.size());
                return FileVisitResult.CONTINUE;
            }
        });
        Map<String, Set<String>> modulesReference = new HashMap<>();
        Set<String> modules = new HashSet<>();
        Files.walkFileTree(modulePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {

                if (file.getFileName().toString().equals("module.xml")) {
                    try {
                        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
                                .newInstance();
                        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                        Document document = documentBuilder.parse(file.toFile());
                        Element elemAlias = (Element) document.getElementsByTagName("module-alias").item(0);
                        if (elemAlias != null) {
                            String moduleName = elemAlias.getAttribute("name");
                            String target = elemAlias.getAttribute("target-name");
                            Set<String> referencing = modulesReference.get(target);
                            if (referencing == null) {
                                referencing = new HashSet<>();
                                modulesReference.put(target, referencing);
                            }
                            referencing.add(moduleName);
                            return FileVisitResult.CONTINUE;
                        }
                        Element elem = (Element) document.getElementsByTagName("module").item(0);
                        if (elem == null) {
                            return FileVisitResult.CONTINUE;
                        }
                        String moduleName = elem.getAttribute("name");
                        modules.add(moduleName);
                        Node n = document.getElementsByTagName("dependencies").item(0);
                        Set<String> optionals = new TreeSet<>();
                        if (n != null) {
                            NodeList deps = n.getChildNodes();
                            for (int i = 0; i < deps.getLength(); i++) {
                                if (deps.item(i).getNodeType() == Node.ELEMENT_NODE) {
                                    Element element = (Element) deps.item(i);
                                    if (element.getNodeName().equals("module")) {
                                        String mod = element.getAttribute("name");
                                        if (element.hasAttribute("optional")) {
                                            if (element.getAttribute("optional").equals("true")) {
                                                optionals.add(mod);
                                            }
                                        }
                                        Set<String> referencing = modulesReference.get(mod);
                                        if (referencing == null) {
                                            referencing = new HashSet<>();
                                            modulesReference.put(mod, referencing);
                                        }
                                        referencing.add(moduleName);
                                    }
                                }
                            }
                        }
                        optionalDependencies.put(moduleName, optionals);
                    } catch (Exception ex) {
                        throw new IOException(ex);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e)
                    throws IOException {
                return FileVisitResult.CONTINUE;
            }
        }
        );
        Map<String, Set<String>> missing = new TreeMap<>();
        // Retrieve all optional dependencies that have not been provisioned.
        for (Map.Entry<String, Set<String>> entry : optionalDependencies.entrySet()) {
            for (String opt : entry.getValue()) {
                if (!modules.contains(opt)) {
                    Set<String> roots = missing.get(opt);
                    if (roots == null) {
                        roots = new TreeSet<>();
                        missing.put(opt, roots);
                    }
                    roots.add(entry.getKey());
                }
            }
        }

        // Compute the set of modules that are in a chain that is not referenced
        // eg: modA is unreferenced but references modB. modB must be marked as unreferenced too
        // because its referent is not referenced.
        Set<String> allNotReferenced = new TreeSet<>();
        Set<String> mods = new HashSet<>();
        mods.addAll(modules);
        // remove all the extension modules
        Set<String> extensions = retrieveExtensionModules(configFile);
        mods.removeAll(extensions);
        while (true) {
            Set<String> notReferenced = new TreeSet<>();
            // Identify the modules that are not referenced from other modules.
            for (String m : mods) {
                Set<String> referencers = modulesReference.get(m);
                if (referencers == null || referencers.isEmpty()) {
                    notReferenced.add(m);
                    allNotReferenced.add(m);
                }
            }
            // No more unreferenced, break.
            if (notReferenced.isEmpty()) {
                break;
            }
            // Remove the unreferenced Modules from the set of references of other modules.
            // For example, removes modA from the set of references to modB.
            // In the next iteration modB will be seen as unreferenced (empty set of referencers)
            // and in turn will be marked as un-referenced.
            for (Map.Entry<String, Set<String>> entry : modulesReference.entrySet()) {
                for (String orphan : notReferenced) {
                    if (entry.getValue().contains(orphan)) {
                        entry.getValue().remove(orphan);
                    }
                }
            }
            // the un-referenced modules are removed before iterating ungain all modules.
            mods.removeAll(notReferenced);
        }
        // Compute all modules (size and names) reachable from each extension
        List<Result.ExtensionResult> extensionResults = new ArrayList<>();
        for (String ex : extensions) {
            Set<String> deps = new TreeSet<>();
            Set<String> seen = new HashSet<>();
            List<Long> extSize = new ArrayList<>();
            extSize.add((long) 0);
            getDependencies(modulePath, ex, deps, seen, extSize);
            deps.add(ex);
            Set<String> unresolved = new TreeSet<>();

            // Compute unresolved dependencies per extension.
            for (String dep : deps) {
                if (!modules.contains(dep)) {
                    unresolved.add(dep);
                }
            }
            extensionResults.add(new Result.ExtensionResult(ex, extSize.get(0) / 1024, deps, unresolved));
        }

        return new Result(size.get(0) / 1024, modules, missing, allNotReferenced, extensionResults);
    }

    private static Set<String> retrieveExtensionModules(Path conf) throws Exception {
        Set<String> extensions = new TreeSet<>();
        if (!Files.exists(conf)) {
            // installation with only packages.
            return extensions;
        }
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
                .newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(conf.toFile());
        Element elem = (Element) document.getElementsByTagName("extensions").item(0);
        if (elem != null) {
            NodeList deps = elem.getChildNodes();
            for (int i = 0; i < deps.getLength(); i++) {
                if (deps.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) deps.item(i);
                    String mod = element.getAttribute("module");
                    extensions.add(mod);
                }
            }
        }
        return extensions;
    }

    private static void getDependencies(Path modulePath, String module, Set<String> dependencies,
            Set<String> seen, List<Long> size) throws Exception {
        if (seen.contains(module)) {
            return;
        }
        seen.add(module);

        String suffix = module.indexOf(':') > 0 ? "/module.xml" : "main/module.xml";
        String moduleIdPath = module.replaceAll("\\.", "/").replace(':', '/');
        Path path = Paths.get(modulePath.toString(), moduleIdPath, suffix);
        if (!Files.exists(path)) {
            // pseudo module
            return;
        }
        // Add content size.
        Files.walkFileTree(path.getParent(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                size.set(0, size.get(0) + attrs.size());
                return FileVisitResult.CONTINUE;
            }
        });
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
                .newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(path.toFile());
        Element elemAlias = (Element) document.getElementsByTagName("module-alias").item(0);
        if (elemAlias != null) {
            String target = elemAlias.getAttribute("target-name");
            dependencies.add(target);
            getDependencies(modulePath, target, dependencies, seen, size);
        } else {
            Node n = document.getElementsByTagName("dependencies").item(0);
            if (n != null) {
                NodeList deps = n.getChildNodes();
                for (int i = 0; i < deps.getLength(); i++) {
                    if (deps.item(i).getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) deps.item(i);
                        String mod = element.getAttribute("name");
                        if (!mod.isEmpty() && !mod.startsWith("java.") && !mod.startsWith("jdk.") && !mod.equals("org.jboss.modules")) {
                            dependencies.add(mod);
                            getDependencies(modulePath, mod, dependencies, seen, size);
                        }
                    }
                }
            }
        }
    }
}
