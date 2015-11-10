/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class AliasEntry {

    private final ManagementResourceRegistration target;
    private volatile PathAddress aliasAddress;

    protected AliasEntry(final ManagementResourceRegistration target) {
        this.target = target;
    }

    ManagementResourceRegistration getTarget() {
        return target;
    }

    void setAliasAddress(PathAddress aliasAddress) {
        this.aliasAddress = aliasAddress;
    }

    /**
     * Gets the address to which this alias is registered
     *
     * @return the alias address
     */
    protected PathAddress getAliasAddress() {
        return aliasAddress;
    }

    /**
     * Gets the address to which this alias should convert
     *
     * @return the target address
     */
    protected PathAddress getTargetAddress() {
        return target.getPathAddress();
    }

    /**
     * Convert the alias address to the target address
     * @param aliasAddress the alias address
     * @return the target address
     * @deprecated This will be removed in WildFly Core 3; override convertToTargetAddress(PathAddress, AliasContext) instead
     */
    @Deprecated
    public PathAddress convertToTargetAddress(PathAddress aliasAddress) {
        throw new UnsupportedOperationException("convertToTargetAddress");
    }

    /**
     * Convert the alias address to the target address.
     *
     * @param aliasAddress the alias address
     * @param aliasAddress the alias address
     * @return the target address
     */
    public PathAddress convertToTargetAddress(PathAddress aliasAddress, AliasContext aliasContext) {
        return convertToTargetAddress(aliasAddress);
    }

    /**
     * A wrapper around {@link OperationContext} for the requested alias address, allowing extra
     * contextual information when converting alias addresses.
     *
     */
    public static class AliasContext {
        public static final String RECURSIVE_GLOBAL_OP = "recursive-global-op";

        final OperationContext delegate;
        final ModelNode operation;

        private AliasContext(final ModelNode operation, final OperationContext delegate) {
            this.delegate = delegate;
            this.operation = operation.clone();
            this.operation.protect();
        }

        static AliasContext create(final ModelNode operation, final OperationContext delegate) {
            return new AliasContext(operation, delegate);
        }

        public static AliasContext create(final PathAddress address, final OperationContext delegate) {
            return new AliasContext(Util.createEmptyOperation(RECURSIVE_GLOBAL_OP, address), delegate);
        }

        /**
         * @see OperationContext#readResourceFromRoot(PathAddress)
         */
        public Resource readResourceFromRoot(final PathAddress address) {
            return delegate.readResourceFromRoot(address);
        }

        /**
         * @see OperationContext#readResource(PathAddress, boolean)
         */
        public Resource readResourceFromRoot(final PathAddress address, final boolean recursive) {
            return delegate.readResourceFromRoot(address, recursive);
        }

        /**
         * Gets the operation being called. The operation is protected, so you cannot modify it.
         * For the global operations when processing children recurively it will be a placeholder operation whose name is
         * {@link #RECURSIVE_GLOBAL_OP} and the address is the address of the requested aliased resource.
         *
         * @return the operation
         */
        public ModelNode getOperation() {
            return operation;
        }
    }
}
