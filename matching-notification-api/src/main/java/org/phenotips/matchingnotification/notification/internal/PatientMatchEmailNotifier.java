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
import org.phenotips.matchingnotification.notification.PatientMatchNotifier;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.context.Execution;
import org.xwiki.mail.MailStatus;
import org.xwiki.mail.MailStatusResult;
import org.xwiki.mail.script.MailSenderScriptService;
import org.xwiki.mail.script.ScriptMailResult;
import org.xwiki.mail.script.ScriptMimeMessage;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.script.service.ScriptService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.mail.Address;
import javax.mail.Message.RecipientType;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.slf4j.Logger;

import com.xpn.xwiki.XWikiContext;

/**
 * Notifies about patient matches.
 *
 * @version $Id$
 */
@Component
@Singleton
public class PatientMatchEmailNotifier implements PatientMatchNotifier
{
    /** Name of document containing template for email notification. */
    public static final String EMAIL_TEMPLATE = "PatientMatchNotificationEmailTemplate";

    @Inject
    @Named("context")
    protected Provider<ComponentManager> componentManagerProvider;

    @Inject
    @Named("mailsender")
    private ScriptService mailScriptService;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> referenceResolver;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private Execution execution;

    @Inject
    private Logger logger;

    @Override
    public List<PatientMatchNotificationResponse> notify(List<PatientMatch> matches) {

        Map<String, List<PatientMatch>> matchesByPatient = groupByPatient(matches);
        List<ScriptMimeMessage> emails = this.createEmailsList(matchesByPatient);

        MailSenderScriptService mailService = (MailSenderScriptService) this.mailScriptService;
        ScriptMailResult mailResult = mailService.send(emails);

        List<PatientMatchNotificationResponse> allResponses = processEmailResult(mailResult, matchesByPatient);

        return allResponses;
    }

    private List<PatientMatchNotificationResponse> processEmailResult(
            ScriptMailResult mailResult, Map<String, List<PatientMatch>> matchesByPatient) {

        // The MailStatus-es from mailResult are in the natural order of patient ids from matchesByPatient.
        // That is, the first MailStatus is the status for the email notifying about the first patient in
        // matches by patient (in the natural order of String-s). See createEmailsList.
        List<String> patientIds = new ArrayList<>(matchesByPatient.keySet());
        Collections.sort(patientIds);
        Iterator<String> patientIdIterator = patientIds.iterator();

        MailStatusResult statusResult = mailResult.getStatusResult();
        List<PatientMatchNotificationResponse> responses =
                new ArrayList<PatientMatchNotificationResponse>(patientIds.size());

        Iterator<MailStatus> all = statusResult.getAll();
        while (all.hasNext()) {
            MailStatus mailStatus = all.next();

            if (!patientIdIterator.hasNext()) {
                this.logger.error("Error creating email reponses list.");
                return null;
            }
            String patientId = patientIdIterator.next();
            List<PatientMatch> matchesForPatient = matchesByPatient.get(patientId);

            for (PatientMatch match : matchesForPatient) {
                PatientMatchEmailNotificationResponse response =
                        new PatientMatchEmailNotificationResponse(mailStatus, match);
                responses.add(response);
            }
        }

        return responses;
    }

    /*
     * For every patient (key in parameter), create one email. Returns a map with a key of patient id and the email
     * created for it as a value.
     */
    private List<ScriptMimeMessage> createEmailsList(Map<String, List<PatientMatch>> matchesByPatient)
    {
        List<ScriptMimeMessage> emails = new ArrayList<>(matchesByPatient.size());

        // For every patient id there will be one email, in the same order: the first email is a notification
        // for the first patient, and so on. This is why it's needed to go over this collection in the same
        // order here and in processing the responses for the emails.
        List<String> patientIds = new ArrayList<>(matchesByPatient.keySet());
        Collections.sort(patientIds);

        for (String patientId : patientIds) {
            List<PatientMatch> matchesList = matchesByPatient.get(patientId);
            ScriptMimeMessage email = createEmail(patientId, matchesList);
            if (email == null) {
                continue;
            }
            emails.add(email);
        }

        return emails;
    }

    private ScriptMimeMessage createEmail(String patientId, List<PatientMatch> matchesList) {

        MailSenderScriptService mailService = (MailSenderScriptService) this.mailScriptService;

        Map<String, Object> emailParameters = new HashMap<String, Object>();
        String language = this.contextProvider.get().getLocale().getLanguage();
        emailParameters.put("language", language);

        Map<String, String> velocityVariables = new HashMap<String, String>();
        velocityVariables.put("p1", "v1");
        emailParameters.put("velocityVariables", velocityVariables);

        DocumentReference emailTemplateReference = referenceResolver.resolve(EMAIL_TEMPLATE, PatientMatch.DATA_SPACE);
        ScriptMimeMessage email = mailService.createMessage("template",
                emailTemplateReference, emailParameters);

        Object property = this.execution.getContext().getProperty(
                "scriptservice.mailsender.error");

        Address from = null;
        try {
            from = new InternetAddress("itaig.phenotips@gmail.com");
        } catch (AddressException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        email.setFrom(from);
        email.addRecipient(RecipientType.TO, from);

        return email;
    }

    /*
     * Takes a list of PatientMatch objects and returns them in a map that
     * groups all matches with same patient id.
     */
    private Map<String, List<PatientMatch>> groupByPatient(List<PatientMatch> matches) {
        Map<String, List<PatientMatch>> matchesMap = new HashMap<String, List<PatientMatch>>();

        for (PatientMatch match : matches) {
            String patientId = match.getPatientId();
            List<PatientMatch> matchesList = matchesMap.get(patientId);
            if (matchesList == null) {
                matchesList = new LinkedList<PatientMatch>();
                matchesMap.put(patientId, matchesList);
            }

            matchesList.add(match);
        }

        return matchesMap;
    }
}
