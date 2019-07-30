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

import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.notification.PatientMatchNotificationResponse;

import org.xwiki.mail.MailStatus;

import java.util.Collection;

import org.apache.commons.lang3.StringUtils;

/**
 * @version $Id$
 */
public class PatientMatchEmailNotificationResponse implements PatientMatchNotificationResponse
{
    private MailStatus mailStatus;

    private Collection<PatientMatch> patientMatches;

    /**
     * Default constructor.
     *
     * @param mailStatus mail status object
     * @param patientMatches the matches list this response is associated with
     */
    public PatientMatchEmailNotificationResponse(MailStatus mailStatus, Collection<PatientMatch> patientMatches)
    {
        this.mailStatus = mailStatus;
        this.patientMatches = patientMatches;
    }

    @Override
    public boolean isSuccessul()
    {
        return StringUtils.isEmpty(this.getErrorMessage());
    }

    @Override
    public String getErrorMessage()
    {
        return this.mailStatus.getErrorDescription();
    }

    @Override
    public Collection<PatientMatch> getPatientMatches()
    {
        return this.patientMatches;
    }

    @Override
    public void setPatientMatches(Collection<PatientMatch> matches)
    {
        this.patientMatches = matches;
    }
}
