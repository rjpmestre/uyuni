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

import static com.redhat.rhn.common.ExceptionMessage.NOT_INSTANTIABLE;
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
     * @param org the organization to filter by (mutually exclusive with channel)
     * @param channel the channel to filter by (mutually exclusive with org)
     * @param advisories list of advisory names to filter by (optional)
     * @param startDate filter errata issued on or after this date (optional)
     * @param endDate filter errata issued on or before this date (optional)
     * @return Set of matching errata
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
     * Lookup errata in a specific channel.
     *
     * @param channel the channel
     * @param advisories list of advisory names to filter by (optional)
     * @param startDate filter errata issued on or after this date (optional)
     * @param endDate filter errata issued on or before this date (optional)
     * @return Set of errata in the channel
     */
    public static Set<Errata> lookupErrataByChannel(
            Channel channel, List<String> advisories, Date startDate, Date endDate
    ) {
        return lookupErrataByChannelOrOrg(null, channel, advisories, startDate, endDate);
    }

    /**
     * Lookup errata belonging to a specific organization.
     *
     * @param org the organization
     * @param advisories list of advisory names to filter by (optional)
     * @param startDate filter errata issued on or after this date (optional)
     * @param endDate filter errata issued on or before this date (optional)
     * @return Set of errata owned by the org
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
     * Lookup vendor errata (org is null).
     *
     * @param advisories list of advisory names to filter by (optional)
     * @param startDate filter errata issued on or after this date (optional)
     * @param endDate filter errata issued on or before this date (optional)
     * @return Set of vendor errata
     */
    public static Set<Errata> lookupErrataFromVendor(
            List<String> advisories, Date startDate, Date endDate
    ) {
        return lookupErrataByChannelOrOrg(null, null, advisories, startDate, endDate);
    }

    private ErrataRepository() {
        throw new UnsupportedOperationException(NOT_INSTANTIABLE);
    }
}
