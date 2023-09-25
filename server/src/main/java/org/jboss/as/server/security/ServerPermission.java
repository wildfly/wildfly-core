/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.security;

import org.jboss.as.server.logging.ServerLogger;

import java.security.BasicPermission;

/**
 * <p>
 * This class is for WildFly Server's permissions. A permission
 * contains a name (also referred to as a "target name") but
 * no actions list; you either have the named permission
 * or you don't.
 * </p>
 *
 * <p>
 * The target name is the name of the permission. The following table lists all the possible permission target names,
 * and for each provides a description of what the permission allows.
 * </p>
 *
 * <p>
 * <table border=1 cellpadding=5 summary="permission target name,
 *  what the target allows">
 * <tr>
 * <th>Permission Target Name</th>
 * <th>What the Permission Allows</th>
 * </tr>
 *
 * <tr>
 *   <td>createDeploymentReflectionIndex</td>
 *   <td>Create a {@link org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex}</td>
 * </tr>
 *
 * <tr>
 *   <td>getCurrentServiceContainer</td>
 *   <td>Retrieve current {@link org.jboss.msc.service.ServiceContainer}</td>
 * </tr>
 *
 * <tr>
 *   <td>setCurrentServiceContainer</td>
 *   <td>Set current {@link org.jboss.msc.service.ServiceContainer}</td>
 * </tr>
 *
 * <tr>
 *   <td>useServiceRegistry</td>
 *   <td>Use {@link org.jboss.as.server.deployment.service.SecuredServiceRegistry}, i.e. invoke its methods</td>
 * </tr>
 *
 *  </table>
 * </p>
 *
 * <p>
 * The permission name may also be an asterisk, to signify a wildcard match.
 * </p>
 *
 * @author Eduardo Martins
 */
public class ServerPermission extends BasicPermission {

    // the valid permission names
    public static final String CREATE_DEPLOYMENT_REFLECTION_INDEX_NAME = "createDeploymentReflectionIndex";
    public static final String GET_CURRENT_SERVICE_CONTAINER_NAME = "getCurrentServiceContainer";
    public static final String SET_CURRENT_SERVICE_CONTAINER_NAME = "setCurrentServiceContainer";
    public static final String USE_SERVICE_REGISTRY_NAME = "useServiceRegistry";
    private static final String WILDCARD_NAME = "*";

    /**
     * The Server Permission named canAccessImmutableManagementResourceRegistration, which should be used to create a {@link org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex}.
     */
    public static final ServerPermission CREATE_DEPLOYMENT_REFLECTION_INDEX = new ServerPermission(CREATE_DEPLOYMENT_REFLECTION_INDEX_NAME);
    /**
     * The Server Permission named canAccessModelController, which should be used to retrieve current {@link org.jboss.msc.service.ServiceContainer}.
     */
    public static final ServerPermission GET_CURRENT_SERVICE_CONTAINER = new ServerPermission(GET_CURRENT_SERVICE_CONTAINER_NAME);
    /**
     * The Server Permission named createCaller, which should be used to set current {@link org.jboss.msc.service.ServiceContainer}.
     */
    public static final ServerPermission SET_CURRENT_SERVICE_CONTAINER = new ServerPermission(SET_CURRENT_SERVICE_CONTAINER_NAME);
    /**
     * The Server Permission named getCallerSubject, which should be used to use {@link org.jboss.as.server.deployment.service.SecuredServiceRegistry}, i.e. invoke its methods.
     */
    public static final ServerPermission USE_SERVICE_REGISTRY = new ServerPermission(USE_SERVICE_REGISTRY_NAME);

    private static String validatePermissionName(String name) throws IllegalArgumentException {
        switch (name) {
            case CREATE_DEPLOYMENT_REFLECTION_INDEX_NAME:
            case GET_CURRENT_SERVICE_CONTAINER_NAME:
            case SET_CURRENT_SERVICE_CONTAINER_NAME:
            case USE_SERVICE_REGISTRY_NAME:
            case WILDCARD_NAME:
                return name;
            default:
                throw ServerLogger.ROOT_LOGGER.illegalPermissionName(name);
        }
    }

    private static String validatePermissionActions(String actions) throws IllegalArgumentException {
        if (actions != null) {
            throw ServerLogger.ROOT_LOGGER.illegalPermissionActions(actions);
        }
        return actions;
    }

    /**
     * Creates a new permission with the specified name.
     * The name is the symbolic name of the permission, such as
     * "getCurrentServiceContainer".
     *
     * @param name the name of the permission.
     *
     * @throws NullPointerException if <code>name</code> is <code>null</code>.
     * @throws IllegalArgumentException if <code>name</code> is not valid.
     */
    public ServerPermission(String name) {
        super(validatePermissionName(name));
    }

    /**
     * Creates a new permission object with the specified name.
     * The name is the symbolic name of the permission, and the
     * actions String is currently unused and should be null.
     *
     * @param name the name of the permission.
     * @param actions should be null.
     *
     * @throws NullPointerException if <code>name</code> is <code>null</code>.
     * @throws IllegalArgumentException if <code>name</code> and/or <code>actions</code> are not valid.
     */
    public ServerPermission(String name, String actions) {
        super(validatePermissionName(name), validatePermissionActions(actions));
    }
}
