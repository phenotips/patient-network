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
import org.phenotips.matchingnotification.notification.PatientMatchNotifier;
import org.phenotips.translation.TranslationManager;

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
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

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
    private TranslationManager translationManager;

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

    @Override
    public boolean notify(List<PatientMatch> matches) {
        Map<String, List<PatientMatch>> matchesByPatient = groupByPatient(matches);
        for (String patientId : matchesByPatient.keySet()) {

            List<PatientMatch> matchesList = matchesByPatient.get(patientId);
            try {
                notifyByEmail(patientId, matchesList);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return false;
    }

    private void notifyByEmail(String patientId, List<PatientMatch> matchesList)
            throws MessagingException {

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

        Address from = new InternetAddress("itaig.phenotips@gmail.com");
        email.setFrom(from);
        email.addRecipient(RecipientType.TO, from);
        ScriptMailResult send = mailService.send(email);
        MailStatusResult statusResult = send.getStatusResult();
        Iterator<MailStatus> all = statusResult.getAll();
        while (all.hasNext()) {
            MailStatus status = all.next();
            System.out.println(status);
        }

    }

    /*
     * Takes a list of PatientMatch objects and returns them in a map that
     * groups all matches with same patient id. That is, for every item {@code
     * item} in the list {@code returnedMap.get(id)}, it is true that {@code
     * item.getPatientId().equals(id)}.
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
