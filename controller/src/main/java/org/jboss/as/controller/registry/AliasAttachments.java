/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller.registry;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;

/**
 * This class is private api
 *
 * @author Kabir Khan
 */
public class AliasAttachments {

    /**
     * When an operation is called on an alias, the controller invokes AliasStepHandler which creates a clone
     * of the operation with the 'real' address. Some handlers like r-r-d need the original address, so we put it
     * into an attachment.
     * <br/>
     * Note: This should be developed further by scoping the attachments for the various steps, but
     * for now since only r-r-d is interested in this, we HACK it by having the r-r-d handler remove it. This avoids
     * the attachment being kept around in a composite of r-r-d's where a 'real' address follows some alias operations.
     */
    public static final OperationContext.AttachmentKey<PathAddress> ALIAS_ORIGINAL_ADDRESS =
            OperationContext.AttachmentKey.create(PathAddress.class);
}
