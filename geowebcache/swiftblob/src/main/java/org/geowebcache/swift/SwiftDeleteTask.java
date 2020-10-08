/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Matthew Northcott, Catalyst IT Ltd NZ, Copyright 2020
 */
package org.geowebcache.swift;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.jclouds.openstack.swift.v1.blobstore.RegionScopedSwiftBlobStore;

class SwiftDeleteTask implements Runnable {
    static final Log log = LogFactory.getLog(SwiftDeleteTask.class);
    static final String logStr = "%s, %s, %dms";

    private static final int RETRIES = 5;

    private final RegionScopedSwiftBlobStore blobStore;
    private final String path;
    private final String container;
    private final IBlobStoreListenerNotifier notifier;

    SwiftDeleteTask(
            RegionScopedSwiftBlobStore blobStore,
            String path,
            String container,
            IBlobStoreListenerNotifier notifier) {
        this.blobStore = blobStore;
        this.path = path;
        this.container = container;
        this.notifier = notifier;
    }

    @Override
    public void run() {
        final ListContainerOptions options = new ListContainerOptions().prefix(path).recursive();

        int retry = 0;
        int delayMs = 1000;
        boolean deleted = false;

        for (; retry < RETRIES && !deleted; retry++) {
            blobStore.clearContainer(container, options);

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                log.debug(e.getMessage());
            }
            delayMs *= 2;

            // NOTE: this is messy but it seems to work.
            // there might be a more effecient way of doing this.
            deleted = blobStore.list(container, options).isEmpty();
        }

        if (!deleted) {
            log.error(
                    String.format(
                            "Failed to delete Swift tile cache at %s/%s after %d retries.",
                            container, path, RETRIES));
        } else if (notifier != null) {
            notifier.notifyListeners();
        }
    }
}
