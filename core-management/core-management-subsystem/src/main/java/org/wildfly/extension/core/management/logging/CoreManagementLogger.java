/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management.logging;

import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.INFO;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import java.util.Set;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
@MessageLogger(projectCode = "WFLYCM", length = 4)
public interface CoreManagementLogger extends BasicLogger {

    /**
     * A logger with a category of the package name.
     */
    CoreManagementLogger ROOT_LOGGER = Logger.getMessageLogger(CoreManagementLogger.class, "org.wildfly.extension.core.management");

    CoreManagementLogger UNSUPPORTED_ANNOTATION_LOGGER = Logger.getMessageLogger(CoreManagementLogger.class, "org.wildfly.annotation.unsupported");

//    @Message(id = 1, value = "The resource %s wasn't working properly and has been removed.")
//    String removedOutOfOrderResource(final String address);

    @Message(id = 2, value = "Error initializing the process state listener %s")
    String processStateInitError(@Cause Throwable t, final String name);

    @LogMessage(level = ERROR)
    @Message(id = 3, value = "Error invoking the process state listener %s")
    void processStateInvokationError(@Cause Throwable t, final String name);

    @LogMessage(level = ERROR)
    @Message(id = 4, value = "The process state listener %s took to much time to complete.")
    void processStateTimeoutError(@Cause Throwable t, final String name);

    @LogMessage(level = ERROR)
    @Message(id = 5, value = "Error cleaning up for the process state listener %s")
    void processStateCleanupError(@Cause Throwable t, final String name);

    @Message(id = 6, value = "Error to load module %s")
    OperationFailedException errorToLoadModule(String moduleID);

    @Message(id = 7, value = "Error to load class %s from module %s")
    OperationFailedException errorToLoadModuleClass(String className, String moduleID);

    @Message(id = 8, value = "Error to instantiate instance of class %s from module %s")
    OperationFailedException errorToInstantiateClassInstanceFromModule(String className, String moduleID);

    @Message(id = 9, value = "%s contains usage of annotations which indicate unstable API.")
    String deploymentContainsUnstableApiAnnotations(String deployment);

    @Message(id = 10, value = "%s extends %s which has been annotated with %s")
    String classExtendsClassWithUnstableApiAnnotations(String sourceClass, String superClass, Set<String> annotations);

    @Message(id = 11, value = "%s implements %s which has been annotated with %s")
    String classImplementsInterfaceWithUnstableApiAnnotations(String sourceClass, String superClass, Set<String> annotations);

    @Message(id = 12, value = "%s references field %s.%s which has been annotated with %s")
    String classReferencesFieldWithUnstableApiAnnotations(String sourceClass, String fieldClass, String fieldName, Set<String> annotations);

    @Message(id = 13, value = "%s references method %s.%s%s which has been annotated with %s")
    String classReferencesMethodWithUnstableApiAnnotations(String sourceClass, String methodClass, String methodName, String methodSignature, Set<String> annotations);

    @Message(id = 14, value = "%s references class %s which has been annotated with %s")
    String classReferencesClassWithUnstableApiAnnotations(String sourceClass, String referencedClass, Set<String> annotations);

    @Message(id = 15, value = "Class %s is annotated with one or more annotations which in turn have been annotated with annotations indicating unstable api: %s")
    String classUsesAnnotatedAnnotations(String clazz, Set<String> annotations);

    // For testing only
    @LogMessage(level = INFO)
    @Message(id = 16, value = "%d")
    void testOutputNumberOfClassesScanned(int number);

}
