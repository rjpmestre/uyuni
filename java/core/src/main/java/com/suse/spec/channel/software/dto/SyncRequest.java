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

import java.util.Date;
import java.util.List;

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

    /**
     * Create a builder for constructing SyncRequest instances.
     * @param operation the sync operation type
     * @return a new builder instance
     */
    public static Builder builder(SyncOperation operation) {
        return new Builder(operation);
    }

    /**
     * Builder for SyncRequest.
     */
    public static class Builder {
        private final SyncOperation operation;
        private ErrataCriteria criteria;
        private boolean async = false;
        private boolean alignModules = false;
        private boolean forceRefresh = false;

        private Builder(SyncOperation operation) {
            this.operation = operation;
        }

        /**
         * Set errata criteria by advisory names.
         * @param advisoryNames list of advisory names to filter by
         * @return this builder
         */
        public Builder withAdvisoryNames(List<String> advisoryNames) {
            this.criteria = new ErrataCriteria(advisoryNames, null, null);
            return this;
        }

        /**
         * Set errata criteria by date range.
         * @param startDate start date for filtering
         * @param endDate end date for filtering
         * @return this builder
         */
        public Builder withDateRange(Date startDate, Date endDate) {
            this.criteria = new ErrataCriteria(null, startDate, endDate);
            return this;
        }

        /**
         * Set full errata criteria.
         * @param criteriaIn the criteria to use
         * @return this builder
         */
        public Builder withCriteria(ErrataCriteria criteriaIn) {
            this.criteria = criteriaIn;
            return this;
        }

        /**
         * Execute the sync asynchronously.
         * @return this builder
         */
        public Builder async() {
            this.async = true;
            return this;
        }

        /**
         * Align modular metadata (RHEL 8+).
         * @return this builder
         */
        public Builder alignModules() {
            this.alignModules = true;
            return this;
        }

        /**
         * Force refresh of cached data.
         * @return this builder
         */
        public Builder forceRefresh() {
            this.forceRefresh = true;
            return this;
        }

        /**
         * Build the SyncRequest.
         * @return the constructed SyncRequest
         */
        public SyncRequest build() {
            return new SyncRequest(criteria, operation, async, alignModules, forceRefresh);
        }
    }
}
