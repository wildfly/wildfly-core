/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.reflect;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.invocation.proxy.MethodIdentifier;
import org.wildfly.common.Assert;

/**
 * Utility methods for finding methods within a {@link ClassReflectionIndex} hierarchy.
 * <p/>
 * User: Jaikiran Pai
 */
public class ClassReflectionIndexUtil {

    /**
     * Finds and returns a method corresponding to the passed <code>methodIdentifier</code>.
     * The passed <code>classReflectionIndex</code> will be used to traverse the class hierarchy while finding the method.
     * <p/>
     * Returns null if no such method is found
     *
     * @param deploymentReflectionIndex The deployment reflection index
     * @param clazz      The class reflection index which will be used to traverse the class hierarchy to find the method
     * @param methodIdentifier          The method identifier of the method being searched for
     * @return
     */
    public static Method findMethod(final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> clazz, final MethodIdentifier methodIdentifier) {
        Class<?> c = clazz;
        List<Class<?>> interfaces = new ArrayList<>();
        while (c != null) {
            final ClassReflectionIndex index = deploymentReflectionIndex.getClassIndex(c);
            final Method method = index.getMethod(methodIdentifier);
            if(method != null) {
                return method;
            }
            addInterfaces(c, interfaces);
            c = c.getSuperclass();
        }
        for(Class<?> i : interfaces) {
            final ClassReflectionIndex index = deploymentReflectionIndex.getClassIndex(i);
            final Method method = index.getMethod(methodIdentifier);
            if(method != null) {
                return method;
            }
        }
        return null;
    }

    private static void addInterfaces(Class<?> c, List<Class<?>> interfaces) {
        for(Class<?> i : c.getInterfaces()) {
            if(!interfaces.contains(i)) {
                interfaces.add(i);
                addInterfaces(i, interfaces);
            }
        }
    }

    /**
     * Finds and returns a method corresponding to the passed <code>methodIdentifier</code>.
     * The passed <code>classReflectionIndex</code> will be used to traverse the class hierarchy while finding the method.
     * <p/>
     * Throws {@link org.jboss.as.server.deployment.DeploymentUnitProcessingException} if no such method is found.
     *
     * @param deploymentReflectionIndex The deployment reflection index
     * @param clazz                     The class to search
     * @param methodIdentifier          The method identifier of the method being searched for
     * @return
     * @throws org.jboss.as.server.deployment.DeploymentUnitProcessingException
     *          If no such method is found
     */
    public static Method findRequiredMethod(final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> clazz, final MethodIdentifier methodIdentifier) throws DeploymentUnitProcessingException {
        Method method = findMethod(deploymentReflectionIndex, clazz, methodIdentifier);
        if (method == null) {
            throw ServerLogger.ROOT_LOGGER.noMethodFound(methodIdentifier, clazz);
        }
        return method;
    }

    /**
     * Finds and returns a method corresponding to the passed <code>method</code>, which may be declared in the super class
     * of the passed <code>classReflectionIndex</code>.
     * <p/>
     * <p/>
     * Throws {@link org.jboss.as.server.deployment.DeploymentUnitProcessingException} if no such method is found.
     *
     * @param deploymentReflectionIndex The deployment reflection index
     * @param clazz                     The class
     * @param method                    The method being searched for
     * @return
     * @throws org.jboss.as.server.deployment.DeploymentUnitProcessingException
     *          If no such method is found
     */
    public static Method findRequiredMethod(final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> clazz, final Method method) throws DeploymentUnitProcessingException {
        Assert.checkNotNullParam("method", method);
        final MethodIdentifier methodIdentifier = MethodIdentifier.getIdentifierForMethod(method);
        return findRequiredMethod(deploymentReflectionIndex, clazz, methodIdentifier);
    }

    /**
     * Finds and returns a method corresponding to the passed <code>method</code>, which may be declared in the super class
     * of the passed <code>classReflectionIndex</code>.
     * <p/>
     * <p/>
     *
     * @param deploymentReflectionIndex The deployment reflection index
     * @param clazz                     The class
     * @param method                    The method being searched for
     * @return
     */
    public static Method findMethod(final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> clazz, final Method method) {
        Assert.checkNotNullParam("method", method);
        MethodIdentifier methodIdentifier = MethodIdentifier.getIdentifierForMethod(method);
        return findMethod(deploymentReflectionIndex, clazz, methodIdentifier);
    }

    /**
     * Finds and returns methods corresponding to the passed method <code>name</code> and method <code>paramTypes</code>.
     * The passed <code>classReflectionIndex</code> will be used to traverse the class hierarchy while finding the method.
     * <p/>
     * Returns empty collection if no such method is found
     *
     * @param deploymentReflectionIndex The deployment reflection index
     * @param classReflectionIndex      The class reflection index which will be used to traverse the class hierarchy to find the method
     * @param methodName                The name of the method
     * @param paramTypes                The param types accepted by the method
     * @return
     */
    public static Collection<Method> findMethods(final DeploymentReflectionIndex deploymentReflectionIndex, final ClassReflectionIndex classReflectionIndex, final String methodName, final String... paramTypes) {
        final Collection<Method> methods = classReflectionIndex.getMethods(methodName, paramTypes);
        if (!methods.isEmpty()) {
            return methods;
        }
        // find on super class
        Class<?> superClass = classReflectionIndex.getIndexedClass().getSuperclass();
        if (superClass != null) {
            ClassReflectionIndex superClassIndex = deploymentReflectionIndex.getClassIndex(superClass);
            if (superClassIndex != null) {
                return findMethods(deploymentReflectionIndex, superClassIndex, methodName, paramTypes);
            }

        }
        return methods;
    }

    /**
     * Finds and returns all methods corresponding to the passed method <code>name</code> and method <code>paramCount</code>.
     * The passed <code>classReflectionIndex</code> will be used to traverse the class hierarchy while finding the method.
     * <p/>
     * Returns empty collection if no such method is found
     *
     * @param deploymentReflectionIndex The deployment reflection index
     * @param classReflectionIndex      The class reflection index which will be used to traverse the class hierarchy to find the method
     * @param methodName                The name of the method
     * @param paramCount                The number of params accepted by the method
     * @return
     */
    public static Collection<Method> findAllMethods(final DeploymentReflectionIndex deploymentReflectionIndex, final ClassReflectionIndex classReflectionIndex, final String methodName, int paramCount) {
        Collection<Method> methods = classReflectionIndex.getAllMethods(methodName, paramCount);
        if (!methods.isEmpty()) {
            return methods;
        }
        // find on super class
        Class<?> superClass = classReflectionIndex.getIndexedClass().getSuperclass();
        if (superClass != null) {
            ClassReflectionIndex superClassIndex = deploymentReflectionIndex.getClassIndex(superClass);
            if (superClassIndex != null) {
                return findAllMethods(deploymentReflectionIndex, superClassIndex, methodName, paramCount);
            }

        }
        return methods;
    }

    /**
     * Finds and returns all methods corresponding to the passed method <code>name</code>.
     * The passed <code>classReflectionIndex</code> will be used to traverse the class hierarchy while finding the method.
     * <p/>
     * Returns empty collection if no such method is found
     *
     * @param deploymentReflectionIndex The deployment reflection index
     * @param classReflectionIndex      The class reflection index which will be used to traverse the class hierarchy to find the method
     * @param methodName                The name of the method
     * @return
     */
    public static Collection<Method> findAllMethodsByName(final DeploymentReflectionIndex deploymentReflectionIndex, final ClassReflectionIndex classReflectionIndex, final String methodName) {
        Collection<Method> methods = classReflectionIndex.getAllMethods(methodName);
        if (!methods.isEmpty()) {
            return methods;
        }
        // find on super class
        Class<?> superClass = classReflectionIndex.getIndexedClass().getSuperclass();
        if (superClass != null) {
            ClassReflectionIndex superClassIndex = deploymentReflectionIndex.getClassIndex(superClass);
            if (superClassIndex != null) {
                return findAllMethodsByName(deploymentReflectionIndex, superClassIndex, methodName);
            }

        }
        return methods;
    }
}
