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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.mail.Address;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;

import org.apache.commons.lang3.StringUtils;

import com.xpn.xwiki.XWikiContext;

/**
 * An email that is supposedly sent by the current user to notify about a match involving the
 * user's patient. The recipient is the owner(s) of the other patient in the match, and may
 * be either local or remote.
 *
 * @version $Id$
 */
public class DefaultUserPatientMatchEmail extends AbstractPatientMatchEmail
{
    /** Name of document containing template for email notification. */
    public static final String EMAIL_TEMPLATE = "UserMatchNotificationEmailTemplate";

    /** Name of the XWiki User Document field that contains user email. */
    public static final String USER_PROPERTY_EMAIL = "email";

    private PatientInMatch myPatient;

    /**
     * Build a new email object for the given match. One of the patients in the match should
     * have same id and server id as {@code subjectPatientId}.
     *
     * @param match the match that the email notifies of
     * @param subjectPatientId id of patient who is the subject of this email.
     *                         Owner of this patient will be notified
     * @param subjectServerId id of the server that holds the subjectPatientId
     */
    public DefaultUserPatientMatchEmail(PatientMatch match, String subjectPatientId, String subjectServerId)
    {
        super(subjectPatientId, subjectServerId, Collections.singletonList(match), null, null);
    }

    /**
     * Same as above, but allows using custom emial texts.
     *
     * @param match the match that the email notifies of
     * @param subjectPatientId id of patient who is the subject of this email.
     *                         Owner of this patient will be notified
     * @param subjectServerId id of the server that holds the subjectPatientId
     * @param customEmailText (optional) custom text to be used for the email
     * @param customEmailSubject (optional) custom subject to be used for the email
     */
    public DefaultUserPatientMatchEmail(PatientMatch match, String subjectPatientId, String subjectServerId,
        String customEmailText, String customEmailSubject)
    {
        super(subjectPatientId, subjectServerId, Collections.singletonList(match),
            customEmailText, customEmailSubject);
    }

    @Override
    protected void init(String subjectPatientId, String subjectServerId)
    {
        super.init(subjectPatientId, subjectServerId);

        PatientMatch match = this.matches.iterator().next();

        if (match.getReference() == this.subjectPatient) {
            this.myPatient = match.getMatched();
        } else {
            this.myPatient = match.getReference();
        }
    }

    @Override
    protected String getEmailTemplate()
    {
        return EMAIL_TEMPLATE;
    }

    @Override
    protected Map<String, Object> createVelocityVariablesMap()
    {
        // TODO: use the same set of variables that DefaultAdminPatientMatchEmail uses
        // to be able to draw a nice HTML table with matches
        Map<String, Object> velocityVariables = new HashMap<>();
        velocityVariables.put("subjectPatient", this.subjectPatient);
        if (this.subjectPatient.getServerId() == null) {
            // current user may have no access to the other patient, so velocity can't be used
            // to get the external URl of the other patient
            try {
                XWikiContext context = CONTEXT_PROVIDER.get();
                String linkURL = context.getWiki().getDocument(
                    this.subjectPatient.getPatient().getDocumentReference(), context).getExternalURL("view", context);
                velocityVariables.put("subjectPatientLink", linkURL);
            } catch (Exception ex) {
                velocityVariables.put("subjectPatientLink", this.subjectPatient.getPatient().getId());
            }
        }
        velocityVariables.put("myPatient", this.myPatient);
        return velocityVariables;
    }

    @Override
    protected void setTo()
    {
        super.setTo();
        try {
            Set<Address> userEmails = getUserEmails();
            for (Address email : userEmails) {
                this.mimeMessage.addRecipient(RecipientType.CC, email);
            }
        } catch (Exception ex) {
            // do nothing
        }
    }

    @Override
    protected void setFrom()
    {
        super.setFrom();
        try {
            Set<Address> userEmails = getUserEmails();
            Address[] address = new Address[userEmails.size()];
            this.mimeMessage.setReplyTo(userEmails.toArray(address));
        } catch (Exception ex) {
            // do nothing
        }
    }

    private Set<Address> getUserEmails()
    {
        String userEmail = USERMANAGER.getCurrentUser().getAttribute(USER_PROPERTY_EMAIL).toString();
        Set<Address> emails = new HashSet<>();

        for (String parsedEmail : StringUtils.split(userEmail, ",; ")) {
            if (StringUtils.isNotBlank(parsedEmail)) {
                try {
                    InternetAddress email = new InternetAddress(parsedEmail.trim());
                    emails.add(email);
                } catch (Exception ex) {
                    LOGGER.error("Error parsing email [{}]: {}", parsedEmail, ex.getMessage(), ex);
                }
            }
        }
        return emails;
    }
}
