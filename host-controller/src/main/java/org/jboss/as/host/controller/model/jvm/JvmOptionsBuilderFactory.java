/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.model.jvm;

import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;

import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.host.controller.jvm.JvmType;
import org.wildfly.common.Assert;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="ropalka@redhat.com">Richard Opalka</a>
 */
public final class JvmOptionsBuilderFactory {

    private static final List<String> MODULAR_JVM_PARAMS = Arrays.asList("--add-exports", "--add-opens", "--add-modules", "--add-reads", "--illegal-access");
    private final JvmType jvmType;

    private JvmOptionsBuilderFactory(final JvmType jvmType) {
        this.jvmType = jvmType;
    }

    public static JvmOptionsBuilderFactory getInstance(final JvmType jvmType) {
        return new JvmOptionsBuilderFactory(jvmType);
    }

    public void addOptions(final JvmElement jvmElement, final List<String> command){
        Assert.checkNotNullParam("jvmElement", jvmElement);
        Assert.checkNotNullParam("command", command);
        String heap = jvmElement.getHeapSize();
        String maxHeap = jvmElement.getMaxHeap();

        // FIXME not the correct place to establish defaults
        if (maxHeap == null && heap != null) {
            maxHeap = heap;
        }
        if (heap == null && maxHeap != null) {
            heap = maxHeap;
        }

        //Add to command
        if (heap != null) {
            command.add("-Xms"+ heap);
        }
        if (maxHeap != null) {
            command.add("-Xmx"+ maxHeap);
        }
        if (jvmElement.getStack() != null) {
            command.add("-Xss" + jvmElement.getStack());
        }
        if (jvmElement.getAgentPath() != null) {
            command.add("-agentpath:" + jvmElement.getAgentPath());
        }
        if (jvmElement.getAgentLib() != null) {
            command.add("-agentlib:" + jvmElement.getAgentLib());
        }
        if (jvmElement.getJavaagent() != null) {
            command.add("-javaagent:" + jvmElement.getJavaagent());
        }
        if (jvmElement.isDebugEnabled() != null && jvmElement.isDebugEnabled() && jvmElement.getDebugOptions() != null) {
            command.add(jvmElement.getDebugOptions());
        }
        List<String> options = jvmElement.getJvmOptions().getOptions();
        if (!options.isEmpty()) {
            String jvmName = jvmElement.getName();
            for (String option : options) {

                if (!checkOption(heap != null && option.startsWith("-Xms"), jvmName, option, Element.HEAP.toString())) {
                    continue;
                }
                if (!checkOption(maxHeap != null && option.startsWith("-Xmx"), jvmName, option, Element.HEAP.toString())) {
                    continue;
                }
                if (!checkOption(jvmElement.getStack() != null && option.startsWith("-Xss"), jvmName, option, Element.STACK.toString())) {
                    continue;
                }
                if (!checkOption(jvmElement.isDebugEnabled() != null && jvmElement.isDebugEnabled() && jvmElement.getDebugOptions() != null &&
                                (option.startsWith("-Xrunjdwp") || option.startsWith("-agentlib:jdwp")),
                        jvmName, option, Attribute.DEBUG_OPTIONS.toString())) {
                    continue;
                }
                if (!checkOption(jvmElement.getAgentPath() != null && option.startsWith("-agentpath:"), jvmName, option, Element.AGENT_PATH.toString())) {
                    continue;
                }
                if (!checkOption(jvmElement.getAgentLib() != null && option.startsWith("-agentlib:"), jvmName, option, Element.AGENT_LIB.toString())) {
                    continue;
                }
                if (!checkOption(jvmElement.getAgentLib() != null && option.startsWith("-javaagent:"), jvmName, option, Element.AGENT_LIB.toString())) {
                    continue;
                }
                if (!checkOption(jvmElement.getJavaagent() != null && option.startsWith("-Xmx"), jvmName, option, Element.JAVA_AGENT.toString())) {
                    continue;
                }
                if (!checkOption(jvmElement.getJavaagent() != null && option.startsWith("-XX:PermSize"), jvmName, option, Element.PERMGEN.toString())) {
                    continue;
                }
                if (!checkOption(jvmElement.getJavaagent() != null && option.startsWith("-XX:MaxPermSize"), jvmName, option, Element.PERMGEN.toString())) {
                    continue;
                }
                if (checkAdditionalJvmOption(option)) {
                    command.add(option);
                }
            }
        }
    }

    boolean checkOption(boolean condition, String jvm, String option, String schemaElement) {
        if (condition) {
            ROOT_LOGGER.optionAlreadySet(option, jvm, schemaElement);
            return false;
        }
        return true;
    }

    boolean checkAdditionalJvmOption(final String option) {
        if (!jvmType.isForLaunch() || jvmType.isModularJvm()) return true; // on modular jdk all options are fine
        if (MODULAR_JVM_PARAMS.contains(option.contains("=") ? option.substring(0, option.indexOf("=")) : option)) {
            //drop jdk9 specific params on jdk 8
            return false;
        }
        return true;
    }
}
