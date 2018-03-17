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
package org.phenotips.matchingnotification.notification.internal;

import org.phenotips.matchingnotification.match.PatientInMatch;
import org.phenotips.matchingnotification.match.PatientMatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;

/**
 * An email that is supposedly by an admin to a local user to notifies about one or more matches.
 *
 * @version $Id$
 */
public class DefaultAdminPatientMatchEmail extends AbstractPatientMatchEmail
{
    /** Name of document containing template for email notification. */
    public static final String EMAIL_TEMPLATE = "AdminMatchNotificationEmailTemplate";

    /**
     * Build a new email object for a list of matches. {@code matches} is expected to be non empty, and one of the
     * patients in every match should have same id as {@code subjectPatientId}.
     *
     * @param subjectPatientId id of patient who is the subject of this email (always local)
     * @param matches list of matches that the email notifies of.
     */
    public DefaultAdminPatientMatchEmail(String subjectPatientId, Collection<PatientMatch> matches)
    {
        super(subjectPatientId, null, matches, null, null);
    }

    @Override
    protected String getEmailTemplate()
    {
        return EMAIL_TEMPLATE;
    }

    @Override
    protected Map<String, Object> createVelocityVariablesMap()
    {
        Map<String, Object> velocityVariables = new HashMap<>();
        velocityVariables.put("subjectPatient", this.subjectPatient);

        List<Map<String, Object>> matchesForEmail = new ArrayList<>(this.matches.size());
        for (PatientMatch match : this.matches) {
            Map<String, Object> matchMap = new HashMap<>();

            // Feature matching
            JSONArray featureMatchesJSON = getFeatureMatchesJSON(match);
            if (featureMatchesJSON.length() > 0) {
                matchMap.put("featureMatches", featureMatchesJSON);
            }
            PatientInMatch otherPatient;
            if (match.isReference(this.subjectPatient.getPatientId(), null)) {
                otherPatient = match.getMatched();
            } else {
                otherPatient = match.getReference();
            }
            // NOTE: "subjectMatchedPatient" can be reference or match patient inside the match!
            // Here  "subjectPatient" means the patient this email will be about,
            //       "subjectMatchedPatient" is one of found matches to the "subjectPatient"
            matchMap.put("subjectMatchedPatient", otherPatient);
            matchMap.put("subjectMatchedPatientEmails", otherPatient.getEmails());
            matchMap.put("match", match);

            matchesForEmail.add(matchMap);
        }
        velocityVariables.put("matches", matchesForEmail);

        return velocityVariables;
    }
}
