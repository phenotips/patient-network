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
package org.phenotips.matchingnotification.match.internal;

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;
import org.phenotips.matchingnotification.match.PatientInMatch;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.notification.PatientMatchNotifier;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;

import java.util.Collection;
import java.util.LinkedList;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
public abstract class AbstractPatientInMatch implements PatientInMatch
{
    private static final DefaultPatientMatchUtils UTILS;

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPatientInMatch.class);

    private static final PatientMatchNotifier NOTIFIER;

    private static final PatientRepository PATIENT_REPOSITORY;

    protected PatientMatch match;

    protected Patient patient;

    static {
        DefaultPatientMatchUtils utils = null;
        PatientMatchNotifier notifier = null;
        PatientRepository patientRepository = null;
        try {
            ComponentManager ccm = ComponentManagerRegistry.getContextComponentManager();
            utils = ccm.getInstance(DefaultPatientMatchUtils.class);
            notifier = ccm.getInstance(PatientMatchNotifier.class);
            patientRepository = ccm.getInstance(PatientRepository.class);
        } catch (ComponentLookupException e) {
            LOGGER.error("Error loading static components: {}", e.getMessage(), e);
        }
        UTILS = utils;
        NOTIFIER = notifier;
        PATIENT_REPOSITORY = patientRepository;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("patientId", this.getPatientId());
        json.put("serverId", this.getServerId());
        json.put("genes", UTILS.setToJSONArray(this.getCandidateGenes()));
        json.put("phenotypes", this.getPhenotypes().toJSON());
        json.put("emails", new JSONArray(this.getEmails()));
        return json;
    }

    @Override
    public boolean isLocal()
    {
        return this.getServerId() == null;
    }

    @Override
    public Collection<String> getEmails()
    {
        Collection<String> emails = new LinkedList<>();
        if (this.isLocal()) {
            emails.addAll(NOTIFIER.getNotificationEmailsForPatient(this.getPatient()));
        } else {
            String href = this.match.getHref();
            if (StringUtils.isNotEmpty(href)) {
                emails.add(href);
            }
        }
        return emails;
    }

    private Patient getPatient()
    {
        if (this.patient == null) {
            this.patient = PATIENT_REPOSITORY.getPatientById(this.getPatientId());
        }
        return this.patient;
    }
}
