/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller.security;

import org.jboss.as.controller.logging.ControllerLogger;
import org.wildfly.security.auth.server.SecurityIdentity;

import java.security.BasicPermission;

/**
 * <p>
 * This class is for WildFly Controller's permissions. A permission
 * contains a name (also referred to as a "target name") but
 * no actions list; you either have the named permission
 * or you don't.
 * </p>
 *
 * <p>
 * The target name is the name of the permission. The following table lists all the possible {@link org.jboss.as.controller.security.ControllerPermission} target names,
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
 *   <td>canAccessImmutableManagementResourceRegistration</td>
 *   <td>Creation of {@link org.jboss.as.controller.registry.ImmutableManagementResourceRegistration}, or invoke one
 * of its methods</td>
 * </tr>
 *
 * <tr>
 *   <td>canAccessModelController</td>
 *   <td>Access a {@link org.jboss.as.controller.ModelController}, or to invoke its methods</td>
 * </tr>
 *
 * <tr>
 *   <td>createCaller</td>
 *   <td>Create a {@link org.jboss.as.controller.access.Caller} with respect to access control decision</td>
 * </tr>
 *
 * <tr>
 *   <td>getCallerSubject</td>
 *   <td>Retrieve the {@link javax.security.auth.Subject} associated with a {@link org.jboss.as.controller.access.Caller}</td>
 * </tr>
 *
 * <tr>
 *   <td>getCurrentAccessAuditContext</td>
 *   <td>Retrieves current {@link org.jboss.as.controller.AccessAuditContext}</td>
 * </tr>
 *
 * </table>
 * </p>
 *
 * <p>
 * The permission name may also be an asterisk, to signify a wildcard match.
 * </p>
 * @author Eduardo Martins
 */
public class ControllerPermission extends BasicPermission {

    // the valid permission names
    public static final String CAN_ACCESS_IMMUTABLE_MANAGEMENT_RESOURCE_REGISTRATION_NAME = "canAccessImmutableManagementResourceRegistration";
    public static final String CAN_ACCESS_MODEL_CONTROLLER_NAME = "canAccessModelController";
    public static final String CREATE_CALLER_NAME = "createCaller";
    public static final String GET_CALLER_SUBJECT_NAME = "getCallerSubject";
    public static final String GET_CALLER_SECURITY_IDENTITY_NAME = "getCallerSecurityIdentity";
    public static final String GET_CURRENT_ACCESS_AUDIT_CONTEXT_NAME = "getCurrentAccessAuditContext";
    public static final String GET_IN_VM_CALL_STATE_NAME = "getInVmCallStateName";
    public static final String INFLOW_SECURITY_IDENTITY_NAME = "inflowSecurityIdentity";
    public static final String PERFORM_IN_VM_CALL_NAME = "performInVmCall";
    private static final String WILDCARD_NAME = "*";

    /**
     * The Controller Permission named canAccessImmutableManagementResourceRegistration, which should be used to create a {@link org.jboss.as.controller.registry.ImmutableManagementResourceRegistration}, or invoke one
     * of its methods
     */
    public static final ControllerPermission CAN_ACCESS_IMMUTABLE_MANAGEMENT_RESOURCE_REGISTRATION = new ControllerPermission(CAN_ACCESS_IMMUTABLE_MANAGEMENT_RESOURCE_REGISTRATION_NAME);
    /**
     * The Controller Permission named canAccessModelController, which should be used to access a {@link org.jboss.as.controller.ModelController}, or to invoke its methods.
     */
    public static final ControllerPermission CAN_ACCESS_MODEL_CONTROLLER = new ControllerPermission(CAN_ACCESS_MODEL_CONTROLLER_NAME);
    /**
     * The Controller Permission named createCaller, which should be used to create a {@link org.jboss.as.controller.access.Caller}, with respect to access control decision.
     */
    public static final ControllerPermission CREATE_CALLER = new ControllerPermission(CREATE_CALLER_NAME);
    /**
     * The Controller Permission named getCallerSubject, which should be used to retrieve the {@link javax.security.auth.Subject} associated with a {@link org.jboss.as.controller.access.Caller}.
     */
    public static final ControllerPermission GET_CALLER_SUBJECT = new ControllerPermission(GET_CALLER_SUBJECT_NAME);
    /**
     * The Controller Permission named getCallerSubject, which should be used to retrieve the {@link SecurityIdentity} associated with a {@link org.jboss.as.controller.access.Caller}.
     */
    public static final ControllerPermission GET_CALLER_SECURITY_IDENTITY = new ControllerPermission(GET_CALLER_SECURITY_IDENTITY_NAME);
    /**
     * The Controller Permission named getCurrentAccessAuditContext, which should be used to retrieve current {@link org.jboss.as.controller.AccessAuditContext}.
     */
    public static final ControllerPermission GET_CURRENT_ACCESS_AUDIT_CONTEXT = new ControllerPermission(GET_CURRENT_ACCESS_AUDIT_CONTEXT_NAME);
    /**
     * The Controller Permission named getInVmCallStateName, which should be used to retrieve in-vm call state.
     */
    public static final ControllerPermission GET_IN_VM_CALL_STATE = new ControllerPermission(GET_IN_VM_CALL_STATE_NAME);
    /**
     * The Controller Permission named inflowSecurityIdentity, which is required where a SecurityIdentity is inflowed as-is bypassing local security.
     */
    public static final ControllerPermission INFLOW_SECURITY_IDENTITY = new ControllerPermission(INFLOW_SECURITY_IDENTITY_NAME);
    /**
     * The Controller Permission named performInVmCall, which should be used to perform an in-vm call.
     */
    public static final ControllerPermission PERFORM_IN_VM_CALL = new ControllerPermission(PERFORM_IN_VM_CALL_NAME);


    private static String validatePermissionName(String name) throws IllegalArgumentException {
        switch (name) {
            case CAN_ACCESS_IMMUTABLE_MANAGEMENT_RESOURCE_REGISTRATION_NAME:
            case CAN_ACCESS_MODEL_CONTROLLER_NAME:
            case CREATE_CALLER_NAME:
            case GET_CALLER_SUBJECT_NAME:
            case GET_CURRENT_ACCESS_AUDIT_CONTEXT_NAME:
            case GET_CALLER_SECURITY_IDENTITY_NAME:
            case GET_IN_VM_CALL_STATE_NAME:
            case INFLOW_SECURITY_IDENTITY_NAME:
            case PERFORM_IN_VM_CALL_NAME:
            case WILDCARD_NAME:
                return name;
            default:
                throw ControllerLogger.ACCESS_LOGGER.illegalPermissionName(name);
        }
    }

    private static String validatePermissionActions(String actions) throws IllegalArgumentException {
        if (actions != null) {
            throw ControllerLogger.ACCESS_LOGGER.illegalPermissionActions(actions);
        }
        return actions;
    }

    /**
     * Creates a new permission with the specified name.
     * The name is the symbolic name of the permission, such as
     * "createCaller", "getCurrentAccessAuditContext", etc.
     *
     * @param name the name of the permission.
     *
     * @throws NullPointerException if <code>name</code> is <code>null</code>.
     * @throws IllegalArgumentException if <code>name</code> is not valid.
     */
    public ControllerPermission(String name) {
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
    public ControllerPermission(String name, String actions) {
        super(validatePermissionName(name), validatePermissionActions(actions));
    }
}
