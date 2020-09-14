/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

/**
 *
 */
package org.jboss.as.host.controller.model.jvm;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * A Java Virtual Machine configuration.
 *
 * @author Brian Stansberry
 */
public class JvmElement {

    private static final long serialVersionUID = 4963103173530602991L;

    //Attributes
    private final String name;
    private JvmType type = JvmType.ORACLE;
    private String javaHome;
    private Boolean debugEnabled;
    private String debugOptions;
    private Boolean envClasspathIgnored;

    //Elements
    private String heapSize;
    private String maxHeap;
    private String permgenSize;
    private String maxPermgen;
    private String agentPath;
    private String agentLib;
    private String javaagent;
    private String stack;
    private String launchCommand;
    private final JvmOptionsElement jvmOptionsElement = new JvmOptionsElement();
    private final JvmOptionsElement moduleOptionsElement = new JvmOptionsElement();
    private Map<String, String> environmentVariables = new HashMap<String, String>();

    public JvmElement(final String name) {
        this.name = name;
    }

    public JvmElement(final String name, ModelNode ... toCombine) {
        determinateJVMParams();
        this.name = name;
        if(name == null) {
            heapSize = "64m";
            maxHeap = "256m";
        }
        for(final ModelNode node : toCombine) {
            if(node == null) {
                continue;
            }

            if(node.hasDefined(JvmAttributes.JVM_AGENT_LIB)) {
                agentLib = node.get(JvmAttributes.JVM_AGENT_LIB).asString();
            }
            if(node.hasDefined(JvmAttributes.JVM_AGENT_PATH)) {
                agentPath = node.get(JvmAttributes.JVM_AGENT_PATH).asString();
            }
            if(node.hasDefined(JvmAttributes.JVM_DEBUG_ENABLED)) {
                debugEnabled = node.get(JvmAttributes.JVM_DEBUG_ENABLED).asBoolean();
            }
            if(node.hasDefined(JvmAttributes.JVM_DEBUG_OPTIONS)) {
                debugOptions = node.get(JvmAttributes.JVM_DEBUG_OPTIONS).asString();
            }
            if(node.hasDefined(JvmAttributes.JVM_ENV_CLASSPATH_IGNORED)) {
                envClasspathIgnored = node.get(JvmAttributes.JVM_ENV_CLASSPATH_IGNORED).asBoolean();
            }
            if(node.hasDefined(JvmAttributes.JVM_ENV_VARIABLES)) {
                for(Property property : node.get(JvmAttributes.JVM_ENV_VARIABLES).asPropertyList()) {
                    environmentVariables.put(property.getName(), property.getValue().asString());
                }
            }
            if(node.hasDefined(JvmAttributes.JVM_LAUNCH_COMMAND)) {
                launchCommand = node.get(JvmAttributes.JVM_LAUNCH_COMMAND).asString();
            }
            if(node.hasDefined(JvmAttributes.MODULE_OPTIONS.getName())) {
                for(final ModelNode option : node.get(JvmAttributes.MODULE_OPTIONS.getName()).asList()) {
                    moduleOptionsElement.addOption(option.asString());
                }
            }
            if(node.hasDefined(JvmAttributes.JVM_HEAP)) {
                heapSize = node.get(JvmAttributes.JVM_HEAP).asString();
            }
            if(node.hasDefined(JvmAttributes.JVM_MAX_HEAP)) {
                maxHeap = node.get(JvmAttributes.JVM_MAX_HEAP).asString();
            }
            if(node.hasDefined(JvmAttributes.JVM_JAVA_AGENT)) {
                javaagent = node.get(JvmAttributes.JVM_JAVA_AGENT).asString();
            }
            if(node.hasDefined(JvmAttributes.JVM_JAVA_HOME)) {
                javaHome = node.get(JvmAttributes.JVM_JAVA_HOME).asString();
            }
            if(node.hasDefined(JvmAttributes.JVM_OPTIONS)) {
                for(final ModelNode option : node.get(JvmAttributes.JVM_OPTIONS).asList()) {
                    jvmOptionsElement.addOption(option.asString());
                }
            }
            if(node.hasDefined(JvmAttributes.JVM_PERMGEN)) {
                permgenSize = node.get(JvmAttributes.JVM_PERMGEN).asString();
            }
            if(node.hasDefined(JvmAttributes.JVM_MAX_PERMGEN)) {
                maxPermgen = node.get(JvmAttributes.JVM_MAX_PERMGEN).asString();
            }
            if(node.hasDefined(JvmAttributes.JVM_STACK)) {
                stack = node.get(JvmAttributes.JVM_STACK).asString();
            }
        }

    }

    public String getJavaHome() {
        return javaHome;
    }

    void setJavaHome(String javaHome) {
        this.javaHome = javaHome;
    }

    public JvmType getJvmType() {
        return type;
    }

    void setJvmType(JvmType type) {
        this.type = type;
    }

    public String getPermgenSize() {
        return permgenSize;
    }

    void setPermgenSize(String permgenSize) {
        this.permgenSize = permgenSize;
    }

    public String getMaxPermgen() {
        return maxPermgen;
    }

    void setMaxPermgen(String maxPermgen) {
        this.maxPermgen = maxPermgen;
    }

    public String getHeapSize() {
        return heapSize;
    }

    void setHeapSize(String heapSize) {
        this.heapSize = heapSize;
    }

    public String getMaxHeap() {
        return maxHeap;
    }

    void setMaxHeap(String maxHeap) {
        this.maxHeap = maxHeap;
    }

    public String getName() {
        return name;
    }

    public Boolean isDebugEnabled() {
        return debugEnabled;
    }

    void setDebugEnabled(Boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public String getDebugOptions() {
        return debugOptions;
    }

    void setDebugOptions(String debugOptions) {
        this.debugOptions = debugOptions;
    }

    public String getStack() {
        return stack;
    }

    void setStack(String stack) {
        this.stack = stack;
    }

    public Boolean isEnvClasspathIgnored() {
        return envClasspathIgnored;
    }

    void setEnvClasspathIgnored(Boolean envClasspathIgnored) {
        this.envClasspathIgnored = envClasspathIgnored;
    }

    public JvmOptionsElement getJvmOptions() {
        return jvmOptionsElement;
    }

    public JvmOptionsElement getModuleOptions() {
        return moduleOptionsElement;
    }

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public String getAgentPath() {
        return agentPath;
    }

    void setAgentPath(String agentPath) {
        if (agentLib != null) {
            throw HostControllerLogger.ROOT_LOGGER.attemptingToSet("agent-path", "agent-lib");
        }
        this.agentPath = agentPath;
    }

    public String getAgentLib() {
        return agentLib;
    }

    void setAgentLib(String agentLib) {
        if (agentPath != null) {
            throw HostControllerLogger.ROOT_LOGGER.attemptingToSet("agent-lib", "agent-path");
        }
        this.agentLib = agentLib;
    }

    public String getLaunchCommand() {
        return launchCommand;
    }

    void setLaunchCommand(String launchCommand) {
        this.launchCommand = launchCommand;
    }

    public String getJavaagent() {
        return javaagent;
    }

    void setJavaagent(String javaagent) {
        this.javaagent = javaagent;
    }

    private void determinateJVMParams() {
        String vendor = WildFlySecurityManager.getPropertyPrivileged("java.vendor", "oracle").toLowerCase();
        if (vendor.contains("ibm")) {
            type = JvmType.IBM;
        } else {
            type = JvmType.ORACLE; //default to oracle
        }
    }

    public static int getJVMMajorVersion() {
        try {
            String vmVersionStr = WildFlySecurityManager.getPropertyPrivileged("java.specification.version", null);
            Matcher matcher = Pattern.compile("^(?:1\\.)?(\\d+)$").matcher(vmVersionStr); //match 1.<number> or <number>
            if (matcher.find()) {
                return Integer.valueOf(matcher.group(1));
            } else {
                throw new RuntimeException("Unknown version of jvm " + vmVersionStr);
            }
        } catch (Exception e) {
            return 8;
        }
    }

}
