/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.as.cli.impl.aesh.commands.security;

import org.aesh.command.Command;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
public interface ControlledCommand extends Command<CLICommandInvocation> {

    String getRequiredType();

    AccessRequirement getAccessRequirement();

    boolean isDependsOnProfile();

    OperationRequestAddress getRequiredAddress();
}
