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
package com.suse.persistence.dao;

import static com.redhat.rhn.common.hibernate.HibernateFactory.getSession;
import static com.suse.utils.Predicates.isProvided;

import com.redhat.rhn.domain.channel.Channel;
import com.redhat.rhn.domain.errata.Errata;
import com.redhat.rhn.domain.org.Org;

import org.hibernate.query.Query;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ErrataRepository {

    /**
     * Lookup erratas by channel OR by org/vendor with optional filters.
     * If channel is provided: returns all erratas in that channel (org parameter is ignored).<br>
     * If channel is null: uses mutually exclusive org filter:
     *   - If org is provided: returns ONLY erratas where org_id = org
     *   - If org is null: returns ONLY vendor erratas (org_id IS NULL)
     *
     * @param org the organization (ignored if channel is provided; mutually exclusive filter otherwise)
     * @param channel the channel (if provided, defines scope and org filter is ignored)
     * @param advisories optional list of advisory names to filter by (null to skip filter)
     * @param startDate optional start date filter (lastModified &gt; startDate, null to skip)
     * @param endDate optional end date filter (lastModified &lt; endDate, null to skip)
     * @return set of erratas matching the criteria
     */
    public static Set<Errata> lookupErrataByChannelOrOrg(
            Org org,
            Channel channel,
            List<String> advisories,
            Date startDate,
            Date endDate
    ) {

        StringBuilder hql = new StringBuilder("SELECT DISTINCT e FROM Errata e");
        Map<String, Object> params = new HashMap<>();

        // Mutually exclusive channel / org filter
        if (channel != null) {
            hql.append(" WHERE :channel IN elements(e.channels) ");
            params.put("channel", channel);
        }
        else {
            // When channel is not provided
            // Either look for matching org or vendor, mutually exclusive
            if (org != null) {
                hql.append(" WHERE e.org = :org ");
                params.put("org", org);
            }
            else {
                hql.append(" WHERE e.org is null ");
            }
        }

        if (isProvided(advisories)) {
            hql.append(" AND e.advisoryName IN (:advisories) ");
            params.put("advisories", advisories);
        }

        if (startDate != null) {
            hql.append(" AND e.lastModified > :startDate ");
            params.put("startDate", startDate);
        }

        if (endDate != null) {
            hql.append(" AND e.lastModified < :endDate ");
            params.put("endDate", endDate);
        }

        Query<Errata> query = getSession().createQuery(hql.toString(), Errata.class);
        params.forEach(query::setParameter);

        return new HashSet<>(query.list());
    }

    /**
     * Lookup erratas published in a specific channel.
     * Optionally filter by advisory names and/or date range (lastModified).
     * Returns all erratas in the channel regardless of org ownership.
     *
     * @param channel the channel to search in
     * @param advisories optional list of advisory names to filter by (null to skip filter)
     * @param startDate optional start date filter (lastModified &gt; startDate, null to skip)
     * @param endDate optional end date filter (lastModified &lt; endDate, null to skip)
     * @return set of erratas in the channel matching the criteria
     */
    public static Set<Errata> lookupErrataByChannel(
            Channel channel, List<String> advisories, Date startDate, Date endDate
    ) {
        return lookupErrataByChannelOrOrg(null, channel, advisories, startDate, endDate);
    }

    /**
     * Lookup erratas belonging to a specific organization.
     * Optionally filter by advisory names and/or date range (lastModified).
     * Returns ONLY erratas where org_id matches the specified organization.
     * Does NOT include vendor erratas (org_id IS NULL).
     *
     * @param org the organization (must not be null)
     * @param advisories optional list of advisory names to filter by (null to skip filter)
     * @param startDate optional start date filter (lastModified &gt; startDate, null to skip)
     * @param endDate optional end date filter (lastModified &lt; endDate, null to skip)
     * @return set of erratas belonging to the org, or empty set if org is null
     */
    public static Set<Errata> lookupErrataByOrg(
            Org org, List<String> advisories, Date startDate, Date endDate
    ) {
        if (org == null) {
            return new HashSet<>();
        }
        return lookupErrataByChannelOrOrg(org, null, advisories, startDate, endDate);
    }

    /**
     * Lookup vendor erratas (org_id IS NULL).
     * Optionally filter by advisory names and/or date range (lastModified).
     * Returns ONLY vendor erratas, NOT organization-specific erratas.
     *
     * @param advisories optional list of advisory names to filter by (null to skip filter)
     * @param startDate optional start date filter (lastModified &gt; startDate, null to skip)
     * @param endDate optional end date filter (lastModified &lt; endDate, null to skip)
     * @return set of vendor erratas matching the criteria
     */
    public static Set<Errata> lookupErrataFromVendor(
            List<String> advisories, Date startDate, Date endDate
    ) {
        return lookupErrataByChannelOrOrg(null, null, advisories, startDate, endDate);
    }
}
