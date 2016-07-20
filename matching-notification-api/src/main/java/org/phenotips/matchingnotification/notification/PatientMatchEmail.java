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
package org.phenotips.matchingnotification.notification;

import org.phenotips.matchingnotification.match.PatientMatch;

import org.xwiki.mail.MailStatus;

import java.util.Collection;

/**
 * @version $Id$
 */
public interface PatientMatchEmail
{
    /**
     * @return the id of the patient about whom the email was sent. It is always local.
     */
    String getSubjectPatientId();

    /**
     * @return the matches this email notifies of.
     */
    Collection<PatientMatch> getMatches();

    /**
     * @return true if email was sent.
     */
    boolean wasSent();

    /**
     * @return status result of email sending. Null if email was not sent.
     */
    MailStatus getStatus();

    /**
     * Send the email.
     */
    void send();
}
