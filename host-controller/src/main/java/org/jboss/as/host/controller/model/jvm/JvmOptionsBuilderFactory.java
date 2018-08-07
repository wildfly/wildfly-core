/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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
package org.jboss.as.host.controller.model.jvm;

import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.wildfly.common.Assert;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class JvmOptionsBuilderFactory {

    private static final JvmOptionsBuilderFactory INSTANCE = new JvmOptionsBuilderFactory();

    private static final Map<JvmType, JvmOptionsBuilder> BUILDERS;
    static {
        Map<JvmType, JvmOptionsBuilder> map = new HashMap<>();
        map.put(JvmType.ORACLE, new OracleJvmOptionsBuilder(JvmType.ORACLE));
        //map.put(JvmType.IBM, new IbmJvmOptionsBuilder(JvmType.IBM));
        map.put(JvmType.IBM, new OracleJvmOptionsBuilder(JvmType.IBM)); //for now the same, as only thing we do is filter out openjdk 9 related params.
        map.put(JvmType.SUN, new OracleJvmOptionsBuilder(JvmType.SUN));
        BUILDERS = Collections.unmodifiableMap(map);
    }

    private JvmOptionsBuilderFactory() {
    }

    public static JvmOptionsBuilderFactory getInstance() {
        return INSTANCE;
    }

    public void addOptions(JvmElement jvmElement, List<String> command){
        Assert.checkNotNullParam("jvmElement", jvmElement);
        Assert.checkNotNullParam("command", command);
        JvmOptionsBuilder builder = BUILDERS.get(jvmElement.getJvmType());
        if (builder == null) {
            throw HostControllerLogger.ROOT_LOGGER.unknown("jvm", jvmElement.getJvmType());
        }
        builder.addToOptions(jvmElement, command);
    }

    private abstract static class JvmOptionsBuilder{
        final JvmType type;

        JvmOptionsBuilder(JvmType type) {
            this.type = type;
        }

        void addToOptions(JvmElement jvmElement, List<String> command){
            String heap = jvmElement.getHeapSize();
            String maxHeap = jvmElement.getMaxHeap();

            // FIXME not the correct place to establish defaults
            if (maxHeap == null && heap != null) {
                maxHeap = heap;
            }
            if (heap == null && maxHeap != null) {
                heap = maxHeap;
            }

            addPermGen(jvmElement, command);

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
            if (options.size() > 0) {
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

        void addPermGen(JvmElement jvmElement, List<String> command) {
            if (jvmElement.getPermgenSize() != null || jvmElement.getMaxPermgen() != null) {
                ROOT_LOGGER.ignoringPermGen(type, jvmElement.getName());
            }
        }
        boolean checkAdditionalJvmOption(String option){
            return true;
        }
    }

    private static class OracleJvmOptionsBuilder extends JvmOptionsBuilder {
        private static final int JVM_MAJOR_VERSION = JvmElement.getJVMMajorVersion();
        private static final List<String> ALLOWED_MODULAR_JDK_PARAMS = Arrays.asList("--add-exports", "--add-opens", "--add-modules", "--add-reads", "--illegal-access");

        private OracleJvmOptionsBuilder(JvmType type) {
            super(type);
        }

        @Override
        boolean checkAdditionalJvmOption(String option) {
            if (JVM_MAJOR_VERSION >= 9 ) {
                return true; //all params are fine
            } else if (ALLOWED_MODULAR_JDK_PARAMS.contains(option.contains("=") ? option.substring(0, option.indexOf("=")) : option)){ //drop jdk9 specific params on jdk 8
                return false;
            }
            return true;
        }
// This builder doesn't override anything currently, since the addPermgen behavior
        // became standard. But we keep the concept in case we do vm-specific stuff
        // in the future
    }

    private static class IbmJvmOptionsBuilder extends JvmOptionsBuilder {
        private IbmJvmOptionsBuilder(JvmType type) {
            super(type);
        }

        // This builder doesn't override anything currently, since the addPermgen behavior
        // became standard. But we keep the concept in case we do vm-specific stuff
        // in the future
    }
}
