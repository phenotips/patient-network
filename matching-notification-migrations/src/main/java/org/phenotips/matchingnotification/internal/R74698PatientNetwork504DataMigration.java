/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */

package org.phenotips.matchingnotification.internal;

import org.xwiki.component.annotation.Component;

import java.math.BigInteger;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.store.hibernate.HibernateSessionFactory;
import com.xpn.xwiki.store.migration.DataMigrationException;
import com.xpn.xwiki.store.migration.XWikiDBVersion;
import com.xpn.xwiki.store.migration.hibernate.AbstractHibernateDataMigration;

/**
 * Migration for PatientNetwork issue PN-504: migrates JSON represenation of phenotypes
 * in a match to the new format introduced in PN-504.
 *
 * @version $Id$
 * @since 1.3m1
 */
@Component
@Named("R74698PatientNetwork504")
@Singleton
public class R74698PatientNetwork504DataMigration extends AbstractHibernateDataMigration
{
    private static final String SQL_ALL_MATCHES =
            "select id, referenceDetails, matchedDetails from patient_matching where score >= 0.1";

    private static final String SQL_UPDATE_DETAILS =
            "update patient_matching set referenceDetails = :refDet, matchedDetails =  :matchDet where id = :id";

    private static final String JSON_KEY_ID = "id";

    private static final String JSON_OLD_KEY_NAME = "name";

    private static final String JSON_NEW_KEY_LABEL = "label";

    /** Logging helper object. */
    @Inject
    private Logger logger;

    /** Handles persistence. */
    @Inject
    private HibernateSessionFactory sessionFactory;

    @Override
    public String getDescription()
    {
        return "Convert match phenotype JSON to new format";
    }

    @Override
    public XWikiDBVersion getVersion()
    {
        return new XWikiDBVersion(74698);
    }

    @Override
    public void hibernateMigrate() throws DataMigrationException, XWikiException
    {
        Session session = this.sessionFactory.getSessionFactory().openSession();
        Transaction t = session.beginTransaction();

        try {
            // using raw SQL query here because the "comment" field that the query depends on
            // is longer mapped to the corresponding JAVA class (and thus HQL can not be used)
            SQLQuery qMatchesWithComments = session.createSQLQuery(SQL_ALL_MATCHES);
            @SuppressWarnings("unchecked")
            List<Object[]> matches = qMatchesWithComments.list();

            for (Object[] fields : matches) {
                BigInteger id = (BigInteger) fields[0];
                String detailsReference = (String) fields[1];
                String detailsMatched = (String) fields[2];

                JSONObject refDetails = new JSONObject(detailsReference);
                JSONObject matchDetails = new JSONObject(detailsMatched);

                this.convertPhenotypeDetailsJSON(refDetails);
                this.convertPhenotypeDetailsJSON(matchDetails);

                SQLQuery qUpdate = session.createSQLQuery(SQL_UPDATE_DETAILS);
                qUpdate.setParameter("id", id.intValue());
                qUpdate.setParameter("refDet", refDetails.toString());
                qUpdate.setParameter("matchDet", matchDetails.toString());
                qUpdate.executeUpdate();
            }

            t.commit();
        } catch (Exception ex) {
            this.logger.error("Failed to migrate phenotype JSONs: [{}]", ex.getMessage());
            if (t != null) {
                t.rollback();
            }
        } finally {
            session.close();
        }
    }

    private void convertPhenotypeDetailsJSON(JSONObject patientDetails)
    {
        JSONObject oldPhenotypesJSON = patientDetails.optJSONObject("phenotypes");

        if (oldPhenotypesJSON != null) {
            JSONArray newPhenotypesJSON = new JSONArray();

            this.addAllPhenotypes(newPhenotypesJSON, oldPhenotypesJSON.optJSONArray("predefined"));
            this.addAllPhenotypes(newPhenotypesJSON, oldPhenotypesJSON.optJSONArray("freeText"));

            patientDetails.put("phenotypes", newPhenotypesJSON);
        }
    }

    private void addAllPhenotypes(JSONArray newPhenotypesArray, JSONArray oldPhenotypesArray)
    {
        for (int i = 0; i < oldPhenotypesArray.length(); i++) {
            try {
                JSONObject phenotype = oldPhenotypesArray.getJSONObject(i);
                JSONObject newPhenotype = new JSONObject();

                if (!phenotype.has(JSON_KEY_ID) && !phenotype.has(JSON_OLD_KEY_NAME)) {
                    continue;
                }

                if (phenotype.has(JSON_KEY_ID)) {
                    newPhenotype.put(JSON_KEY_ID, phenotype.getString(JSON_KEY_ID));
                }
                if (phenotype.has(JSON_OLD_KEY_NAME)) {
                    newPhenotype.put(JSON_NEW_KEY_LABEL, phenotype.getString(JSON_OLD_KEY_NAME));
                }
                String observedValue = phenotype.has("observed") ? phenotype.getString("observed") : "yes";
                newPhenotype.put("observed", observedValue);
                newPhenotype.put("type", "phenotype");

                newPhenotypesArray.put(newPhenotype);
            } catch (Exception ex) {
                // ignore the phenotype that can't be converted
                this.logger.error("Error converting phenotype: [{}]", ex.getMessage());
            }
        }
    }
}
