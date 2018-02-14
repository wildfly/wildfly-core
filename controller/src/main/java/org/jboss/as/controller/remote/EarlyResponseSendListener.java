/*
Copyright 2018 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.controller.remote;

import org.jboss.as.controller.OperationContext;

/**
 * Callback that can be provided to operation step handlers for operations like 'reload' and 'shutdown'
 * where the response needs to be sent to the caller before the operation completes. The handler will
 * invoke the callback when the operation has reached the point where it is safe to send the response.
 * <p>
 * No callback should be attached before the operation is committed.
 *
 * @author Brian Stansberry
 */
public interface EarlyResponseSendListener {
    /**
     * Key under which a listener would be
     * {@link OperationContext#attach(OperationContext.AttachmentKey, Object) attached to an operation context}
     * if notification that it's safe
     */
    OperationContext.AttachmentKey<EarlyResponseSendListener> ATTACHMENT_KEY = OperationContext.AttachmentKey.create(EarlyResponseSendListener.class);

    /**
     * Informs the management kernel that it is ok to send an early response to the operation.
     * <strong>Note:</strong> It is valid for this method to be invoked multiple times for the same
     * listener. It is the responsibility of the listener implementation to ensure that only one
     * response is sent to the caller.
     *
     * @param resultAction the result of the operation for which an early response is being sent
     */
    void sendEarlyResponse(OperationContext.ResultAction resultAction);
}
