/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.mgmt;

import org.jboss.as.controller.transform.Transformers;
import org.jboss.remoting3.Attachments;
import org.jboss.remoting3.Channel;

import java.util.Set;

/**
 * Manages attachments on the domain controller for each slave host controller channel.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class SlaveChannelAttachments {

    private static final Attachments.Key<HostChannelInfo> HOST_CHANNEL_INFO = new Attachments.Key<HostChannelInfo>(HostChannelInfo.class);

    static void attachSlaveInfo(Channel channel, String hostName, Transformers transformers, Set<String> domainIgnoredExtensions) {
        channel.getAttachments().attach(HOST_CHANNEL_INFO, new HostChannelInfo(hostName, transformers, domainIgnoredExtensions));
    }

    static String getHostName(Channel channel) {
        return channel.getAttachments().getAttachment(HOST_CHANNEL_INFO).hostName;
    }

    static Transformers getTransformers(Channel channel) {
        return channel.getAttachments().getAttachment(HOST_CHANNEL_INFO).transformers;
    }

    static Set<String> getDomainIgnoredExtensions(Channel channel) {
        return channel.getAttachments().getAttachment(HOST_CHANNEL_INFO).domainIgnoredExtensions;
    }

    private static class HostChannelInfo {
        final String hostName;
        final Transformers transformers;
        final Set<String> domainIgnoredExtensions;

        public HostChannelInfo(String hostName, Transformers transformers, Set<String> domainIgnoredExtensions) {
            this.hostName = hostName;
            this.transformers = transformers;
            this.domainIgnoredExtensions = domainIgnoredExtensions;
        }
    }

}
