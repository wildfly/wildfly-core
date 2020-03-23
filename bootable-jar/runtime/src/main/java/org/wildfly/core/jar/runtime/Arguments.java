/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.core.jar.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jboss.as.process.CommandLineConstants;
import static org.wildfly.core.jar.runtime.Constants.DEPLOYMENT_ARG;
import org.wildfly.core.jar.runtime._private.BootableJarLogger;

/**
 *
 * @author jdenise
 */
final class Arguments {

    private Arguments() {

    }

    private Boolean isHelp;
    private Boolean isVersion;
    private final List<String> serverArguments = new ArrayList<>();
    private Path deployment;

    public static Arguments parseArguments(List<String> args) throws Exception {
        Objects.requireNonNull(args);
        Arguments arguments = new Arguments();
        arguments.handleArguments(args);
        return arguments;
    }

    private void handleArguments(List<String> args) throws Exception {
        for (String a : args) {
            if (a.startsWith(DEPLOYMENT_ARG)) {
                deployment = checkPath(getValue(a));
            } else if (a.startsWith(CommandLineConstants.PUBLIC_BIND_ADDRESS)) {
                serverArguments.add(a);
            } else if (CommandLineConstants.PROPERTIES.equals(a)) {
                serverArguments.add(a);
            } else if (a.startsWith(CommandLineConstants.SECURITY_PROP)) {
                serverArguments.add(a);
            } else if (a.startsWith(CommandLineConstants.SYS_PROP)) {
                serverArguments.add(a);
            } else if (a.startsWith(CommandLineConstants.START_MODE)) {
                serverArguments.add(a);
            } else if (a.startsWith(CommandLineConstants.DEFAULT_MULTICAST_ADDRESS)) {
                serverArguments.add(a);
            } else if (CommandLineConstants.VERSION.equals(a)) {
                isVersion = true;
                serverArguments.add(a);
            } else if (CommandLineConstants.HELP.equals(a)) {
                isHelp = true;
            } else {
                throw BootableJarLogger.ROOT_LOGGER.unknownArgument(a);
            }
        }
    }

    private Path checkPath(String path) {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            throw BootableJarLogger.ROOT_LOGGER.notExistingFile(path);
        }
        return filePath;
    }

    private static String getValue(String arg) {
        int sep = arg.indexOf("=");
        if (sep == -1 || sep == arg.length() - 1) {
            throw BootableJarLogger.ROOT_LOGGER.invalidArgument(arg);
        }
        return arg.substring(sep + 1);
    }

    /**
     * @return the isHelp
     */
    public Boolean isHelp() {
        return isHelp == null ? false : isHelp;
    }

    /**
     * @return the isVersion
     */
    public Boolean isVersion() {
        return isVersion == null ? false : isVersion;
    }

    /**
     * @return the serverArguments
     */
    public List<String> getServerArguments() {
        return Collections.unmodifiableList(serverArguments);
    }

    /**
     * @return the deployment
     */
    public Path getDeployment() {
        return deployment;
    }

}
