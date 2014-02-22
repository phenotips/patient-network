/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.phenotips.messaging;

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;

import org.xwiki.component.manager.ComponentLookupException;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.HibernateException;
import org.hibernate.usertype.UserType;

/**
 * Custom Hibernate type used for storing patient references in a database.
 * 
 * @version $Id$
 * @since 1.0M1
 */
public class PatientReferenceType implements UserType
{
    @Override
    public int[] sqlTypes()
    {
        return new int[] {Types.VARCHAR};
    }

    @Override
    public Class<?> returnedClass()
    {
        return Patient.class;
    }

    @Override
    public boolean equals(Object x, Object y) throws HibernateException
    {
        if (x == y) {
            return true;
        }
        if (Patient.class.isInstance(x) && Patient.class.isInstance(y)) {
            return x.equals(y);
        }
        return false;
    }

    @Override
    public int hashCode(Object x) throws HibernateException
    {
        if (Patient.class.isInstance(x)) {
            return x.hashCode();
        }
        return 0;
    }

    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, Object owner) throws HibernateException, SQLException
    {
        String serializedReference = rs.getString(names[0]);
        if (StringUtils.isNotEmpty(serializedReference)) {
            return resolvePatient(serializedReference);
        }
        return null;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index) throws HibernateException, SQLException
    {
        if (Patient.class.isInstance(value)) {
            st.setString(index, ((Patient) value).getDocument().toString());
        } else {
            st.setNull(index, Types.VARCHAR);
        }
    }

    @Override
    public Object deepCopy(Object value) throws HibernateException
    {
        return value;
    }

    @Override
    public boolean isMutable()
    {
        return false;
    }

    @Override
    public Serializable disassemble(Object value) throws HibernateException
    {
        if (Patient.class.isInstance(value)) {
            return ((Patient) value).getDocument().toString();
        }
        return null;
    }

    @Override
    public Object assemble(Serializable cached, Object owner) throws HibernateException
    {
        if (String.class.isInstance(cached)) {
            return resolvePatient((String) cached);
        }
        return null;
    }

    @Override
    public Object replace(Object original, Object target, Object owner) throws HibernateException
    {
        return original;
    }

    private Patient resolvePatient(String serializedReference)
    {
        try {
            PatientRepository resolver =
                ComponentManagerRegistry.getContextComponentManager().getInstance(PatientRepository.class);
            return resolver.getPatientById(serializedReference);
        } catch (ComponentLookupException ex) {
            // This really shouldn't happen...
        }
        return null;
    }
}
