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
package com.suse.impl.channel.software;

import com.redhat.rhn.domain.errata.Errata;

import java.util.Set;

/**
 * Context for passing data between SyncFromSource sync operations.
 */
public class SyncFromSourceContext {
    // All erratas from source that match the search criteria, regardless of whether they already exist in target.
    // Used to determine which packages to sync in ERRATA_AND_PACKAGES mode.
    private Set<Errata> matchingErratas;

    // Subset of matchingErratas that are NOT already in target.
    // These are the erratas that will actually be cloned.
    private Set<Errata> erratasToSync;

    public Set<Errata> getMatchingErratas() {
        return matchingErratas;
    }

    public void setMatchingErratas(Set<Errata> matchingErratasIn) {
        matchingErratas = matchingErratasIn;
    }

    public Set<Errata> getErratasToSync() {
        return erratasToSync;
    }

    public void setErratasToSync(Set<Errata> erratasToSyncIn) {
        erratasToSync = erratasToSyncIn;
    }
}
