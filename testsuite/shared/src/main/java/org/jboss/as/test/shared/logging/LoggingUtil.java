/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.shared.logging;


import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;

/**
 * Utility class for logging repetitive tasks
 * @author tmiyar
 *
 */
public class LoggingUtil {

    /**
     * Will return the full log path for the handler in the name parameter,
     * to be used when using @RunAsClient annotation
     * Depending on how you run it you might need some permissions:
     * new PropertyPermission("node0", "read"),
     * new RemotingPermission("connect"),
     * new SocketPermission(Utils.getDefaultHost(true), "accept,connect,listen,resolve"),
     * new RuntimePermission("getClassLoader")
     * @param managementClient
     * @param name of handler
     * @param handlerType i.e. periodic-rotating-file-handler
     * @return
     * @throws Exception
     */
    public static Path getLogPath(ModelControllerClient managementClient, String handlerType, String name) throws Exception {
        final ModelNode address = Operations.createAddress("subsystem", "logging", handlerType, name);
        final ModelNode op = Operations.createOperation("resolve-path", address);
        final ModelNode result = managementClient.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            throw new Exception("Can't get log file");
        }
        return Paths.get(Operations.readResult(result).asString());
    }

    /**
     * Will return the full log path for the given log file relative to the the jboss.server.log.dir.
     * Meant for use by test code that runs in the server VM. Tests that use this should add
     * the following permission to allow the call to succeed in a testsuite run with the security manager enabled:
     * new PropertyPermission("jboss.server.log.dir", "read")
     *
     * @param logFile name of the log file, relative to the server log directory
     * @return the path
     */
    public static Path getInServerLogPath(String logFile) {
        return Paths.get(System.getProperty("jboss.server.log.dir")).resolve(logFile);
    }


    @SafeVarargs
    public static boolean hasLogMessage(String logFileName, String logMessage, Predicate<String>... filters) throws Exception {

        Path logPath = LoggingUtil.getInServerLogPath(logFileName);
        return isMessageInLogFile(logPath, logMessage, 0, filters);
    }

    @SafeVarargs
    public static boolean hasLogMessage(ModelControllerClient managementClient, String handlerName, String logMessage, Predicate<String>... filters) throws Exception {

        Path logPath = LoggingUtil.getLogPath(managementClient, "file-handler", handlerName);
        return isMessageInLogFile(logPath, logMessage, 0, filters);
    }

    @SafeVarargs
    public static boolean hasLogMessage(ModelControllerClient managementClient, String handlerName, String logMessage, long offset, Predicate<String>... filters) throws Exception {

        Path logPath = LoggingUtil.getLogPath(managementClient, "file-handler", handlerName);
        return isMessageInLogFile(logPath, logMessage, offset, filters);
    }


    @SafeVarargs
    public static int countLogMessage(String logFileName, String logMessage, Predicate<String>... filters) throws Exception {

        Path logPath = LoggingUtil.getInServerLogPath(logFileName);
        return countMessageInLogFile(logPath, logMessage, 0, false, filters);
    }

    @SafeVarargs
    public static int countLogMessage(ModelControllerClient managementClient, String handlerName, String logMessage, Predicate<String>... filters) throws Exception {

        Path logPath = LoggingUtil.getLogPath(managementClient, "file-handler", handlerName);
        return countMessageInLogFile(logPath, logMessage, 0, false, filters);
    }

    @SafeVarargs
    public static int countLogMessage(ModelControllerClient managementClient, String handlerName, String logMessage, long offset, Predicate<String>... filters) throws Exception {

        Path logPath = LoggingUtil.getLogPath(managementClient, "file-handler", handlerName);
        return countMessageInLogFile(logPath, logMessage, offset, false, filters);
    }

    @SafeVarargs
    private static boolean isMessageInLogFile(Path logPath, String logMessage, long offset, Predicate<String>... filters) throws Exception{
        return countMessageInLogFile(logPath, logMessage, offset, true, filters) > 0;
    }

    @SafeVarargs
    private static int countMessageInLogFile(Path logPath, String logMessage, long offset, boolean stopAtFirst, Predicate<String>... filters) throws Exception{
        int found = 0;
        try (BufferedReader fileReader = Files.newBufferedReader(logPath)) {
            String line = "";
            long count = 0;
            while ((line = fileReader.readLine()) != null) {
                if (count++ < offset) {
                    continue;
                }
                if (line.contains(logMessage)) {
                    boolean contains = true;
                    for (int i = 0; contains && filters != null && i < filters.length; i++) {
                        contains = filters[i].test(line);
                    }
                    if (contains) {
                        found++;
                        if (stopAtFirst) {
                            break;
                        }
                    }
                }
            }
        }
        return found;
    }


    /**
     * Helper method to dump the contents of a log to stdout.
     * @param logFileName the name of the log file
     */
    public static void dumpTestLog(String logFileName) throws IOException {

        Path logPath = LoggingUtil.getInServerLogPath(logFileName);
        dumpTestLog(logPath);
    }

    /**
     * Helper method to dump the contents of a log to stdout.
     * @param managementClient client to use the name of the log file used by a handler
     * @param handlerName name of the handler that writes to the file
     */
    public static void dumpTestLog(ModelControllerClient managementClient, String handlerName) throws Exception {

        Path logPath = LoggingUtil.getLogPath(managementClient, "file-handler", handlerName);
        dumpTestLog(logPath);
    }

    private static void dumpTestLog(Path logPath) throws IOException {
        try (BufferedReader fileReader = Files.newBufferedReader(logPath)) {
            String line = "";
            while ((line = fileReader.readLine()) != null) {
                System.out.println(line);
            }
        }

    }

    public static long countLines(Path logPath) throws Exception {
        try(Stream<String> lines = Files.lines(logPath)) {
            return lines.count();
        }
    }
}
