/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.model.jvm;


/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class JvmElementTestUtils {

    public static JvmElement create(JvmType type) {
        JvmElement element = new JvmElement("test");
        element.setJvmType(type);
        return element;
    }

    public static void setDebugEnabled(JvmElement element, boolean value) {
        element.setDebugEnabled(value);
    }

    public static void setDebugOptions(JvmElement element, String value) {
        element.setDebugOptions(value);
    }

    public static void setHeapSize(JvmElement element, String value) {
        element.setHeapSize(value);
    }

    public static void setMaxHeap(JvmElement element, String value) {
        element.setMaxHeap(value);
    }

    public static void setPermgenSize(JvmElement element, String value) {
        element.setPermgenSize(value);
    }

    public static void setMaxPermgen(JvmElement element, String value) {
        element.setMaxPermgen(value);
    }

    public static void setStack(JvmElement element, String value) {
        element.setStack(value);
    }

    public static void setAgentLib(JvmElement element, String value) {
        element.setAgentLib(value);
    }

    public static void setAgentPath(JvmElement element, String value) {
        element.setAgentPath(value);
    }

    public static void setJavaagent(JvmElement element, String value) {
        element.setJavaagent(value);
    }

    public static void addJvmOption(JvmElement element, String value) {
        element.getJvmOptions().addOption(value);
    }

}
