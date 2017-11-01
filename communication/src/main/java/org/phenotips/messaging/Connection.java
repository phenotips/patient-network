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
package org.phenotips.messaging;

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.Patient;
import org.phenotips.data.permissions.EntityAccess;
import org.phenotips.data.permissions.EntityPermissionsManager;
import org.phenotips.data.similarity.PatientSimilarityView;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.users.UserManager;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

/**
 * Stores a connection between the owners of two matched patients, anonymously, to be used for email communication. The
 * identities of the two parties are kept private, since mails are sent behind the scenes, while the users only see the
 * {@link #id identifier of the connection}.
 *
 * @version $Id$
 * @since 1.0M1
 */
@Entity
public class Connection
{
    /** @see #getToken() */
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "uuid", columnDefinition = "BINARY(16)")
    private UUID token;

    /** @see #getId() */
    private long id;

    /** @see #getInitiatingUser() */
    @Type(type = "org.phenotips.messaging.DocumentReferenceType")
    private DocumentReference initiatingUser;

    /** @see #getContactedUser() */
    @Type(type = "org.phenotips.messaging.DocumentReferenceType")
    private DocumentReference contactedUser;

    /** @see #getReferencePatient() */
    @Type(type = "org.phenotips.messaging.PatientReferenceType")
    private Patient referencePatient;

    /** @see #getTargetPatient() */
    @Type(type = "org.phenotips.messaging.PatientReferenceType")
    private Patient targetPatient;

    /** Default constructor used by Hibernate. */
    public Connection()
    {
        // Nothing to do, Hibernate will populate all the fields from the database
    }

    /**
     * Constructor that copies the data from a patient pair.
     *
     * @param patientPair the paired patient to get the data from
     */
    public Connection(PatientSimilarityView patientPair)
    {
        this.targetPatient = patientPair;
        this.referencePatient = patientPair.getReference();
        EntityAccess pa = getAccess(patientPair.getReference());
        this.initiatingUser = this.getUserManager().getCurrentUser().getProfileDocument();
        pa = getAccess(patientPair);
        this.contactedUser = new DocumentReference(pa.getOwner().getUser());
    }

    /**
     * The identifier of this connection can be exposed, and is the only thing needed to
     * {@link ConnectionManager#getConnectionById(Long) retrieve back the full connection}.
     *
     * @return a numerical identifier
     * @deprecated use {@link #getToken()} instead
     */
    @Deprecated
    public Long getId()
    {
        return this.id;
    }

    /**
     * The unique, secure connection token is exposed, and is the only thing needed to
     * {@link ConnectionManager#getConnectionByToken(String) retrieve back the full connection}.
     *
     * @return a string token
     */
    public String getToken()
    {
        return String.valueOf(this.token);
    }

    /**
     * The user that initiated the communication, one of the owners of the reference patient.
     *
     * @return a user reference
     */
    public DocumentReference getInitiatingUser()
    {
        return this.initiatingUser;
    }

    /**
     * Sets the initiating user.
     *
     * @param userReference the user reference to store
     */
    public void setInitiatingUser(DocumentReference userReference)
    {
        this.initiatingUser = userReference;
    }

    /**
     * The target of the communication, the owner of the matched patient.
     *
     * @return a user reference
     */
    public DocumentReference getContactedUser()
    {
        return this.contactedUser;
    }

    /**
     * Sets the contacted user.
     *
     * @param userReference the user reference to store
     */
    public void setContactedUser(DocumentReference userReference)
    {
        this.contactedUser = userReference;
    }

    /**
     * The reference patient, owned by the {@link #getInitiatingUser() initiating user}.
     *
     * @return a patient
     */
    public Patient getReferencePatient()
    {
        return this.referencePatient;
    }

    /**
     * Sets the reference patient.
     *
     * @param patient the reference patient to store
     */
    public void setReferencePatient(Patient patient)
    {
        this.referencePatient = patient;
    }

    /**
     * The matched patient, owned by the {@link #getContactedUser() contacted user}.
     *
     * @return a patient, usually with only partial information exposed
     */
    public Patient getTargetPatient()
    {
        return this.targetPatient;
    }

    /**
     * Sets the matched patient.
     *
     * @param patient the matched patient to store
     */
    public void setTargetPatient(Patient patient)
    {
        this.targetPatient = patient;
    }

    @Override
    public String toString()
    {
        return this.getToken();
    }

    /**
     * Helper method for computing the access level granted for the current user to the target patient.
     *
     * @param p the patient on which the access level is checked
     * @return the access type the current user has on the target patient
     */
    private EntityAccess getAccess(Patient p)
    {
        EntityPermissionsManager pm = getComponent(EntityPermissionsManager.class);
        return pm.getEntityAccess(p);
    }

    /**
     * Helper method for retrieving components from the component manager.
     *
     * @param type the component type to retrieve
     * @return an instance implementing the requested component type, or {@code null} if the requested component failed
     *         to be instantiated
     */
    private <T> T getComponent(java.lang.reflect.Type type)
    {
        try {
            return ComponentManagerRegistry.getContextComponentManager().getInstance(type);
        } catch (ComponentLookupException e) {
            return null;
        }
    }

    private UserManager getUserManager()
    {
        try {
            return ComponentManagerRegistry.getContextComponentManager().getInstance(UserManager.class);
        } catch (ComponentLookupException e) {
            return null;
        }
    }
}
