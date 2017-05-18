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
package org.phenotips.matchingnotification.events;

import org.phenotips.data.events.PatientDeletingEvent;
import org.phenotips.matchingnotification.internal.DefaultMatchingNotificationManager;
import org.phenotips.matchingnotification.match.PatientMatch;
import org.phenotips.matchingnotification.storage.MatchStorageManager;

import org.xwiki.component.annotation.Component;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Removes local matches from database for the patient that was deleted.
 *
 * @version $Id$
 */
@Component
@Named("patient-matches-remover")
@Singleton
public class PatientMatchesRemover extends AbstractEventListener
{
    private Logger logger = LoggerFactory.getLogger(DefaultMatchingNotificationManager.class);

    @Inject
    private MatchStorageManager matchStorageManager;

    /** Default constructor, sets up the listener name and the list of events to subscribe to. */
    public PatientMatchesRemover()
    {
        super("patient-matches-remover", new PatientDeletingEvent());
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        XWikiDocument doc = (XWikiDocument) source;

        String patientId = doc.getDocumentReference().getName();
        List<PatientMatch> matchesForPatient = this.matchStorageManager.loadMatchesByReferencePatientId(patientId);
        Session session = this.matchStorageManager.beginNotificationMarkingTransaction();
        this.matchStorageManager.deleteMatches(session, matchesForPatient);
        boolean successful = this.matchStorageManager.endNotificationMarkingTransaction(session);

        if (!successful) {
            this.logger.error("Error while deleting matches for patient ID ", patientId);
        }
    }
}
