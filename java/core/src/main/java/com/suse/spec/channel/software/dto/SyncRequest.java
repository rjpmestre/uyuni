/*
 * Copyright (c) 2026 SUSE LLC
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 */
package com.suse.spec.channel.software.dto;

/**
 * Request object for channel sync operations.
 *
 * @param criteria the errata criteria for filtering
 * @param operation the sync operation type
 * @param async whether to execute asynchronously
 * @param alignModules whether to align modules during sync
 * @param forceRefresh whether to force a refresh of the data
 */
public record SyncRequest(
    ErrataCriteria criteria,
    SyncOperation operation,
    boolean async,
    boolean alignModules,
    boolean forceRefresh
) {

}
