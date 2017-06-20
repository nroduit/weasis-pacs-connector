/*******************************************************************************
 * Copyright (c) 2014 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.data;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.data.xml.TagUtil;
import org.weasis.dicom.data.xml.XmlDescription;
import org.weasis.dicom.util.DateUtil;

public class Patient implements XmlDescription {

    private static final Logger LOGGER = LoggerFactory.getLogger(Patient.class);

    private final String patientID;
    private String issuerOfPatientID = null;
    private String patientName = null;
    private String patientBirthDate = null;
    private String patientBirthTime = null;
    private String patientSex = null;
    private final List<Study> studiesList;

    public Patient(String patientID) {
        this(patientID, null);
    }

    public Patient(String patientID, String issuerOfPatientID) {
        if (patientID == null) {
            throw new IllegalArgumentException("PaientID cannot be null!");
        }
        this.patientID = patientID;
        this.issuerOfPatientID = issuerOfPatientID;
        studiesList = new ArrayList<>();
    }

    public boolean hasSameUniqueID(String patientID, String issuerOfPatientID) {
        if (this.patientID.equals(patientID)) {
            if ((this.issuerOfPatientID == null && issuerOfPatientID == null)
                || (this.issuerOfPatientID != null && this.issuerOfPatientID.equals(issuerOfPatientID))) {
                return true;
            }
        }
        return false;
    }

    public String getPatientID() {
        return patientID;
    }

    public String getPatientName() {
        return patientName;
    }

    public List<Study> getStudies() {
        return studiesList;
    }

    public boolean isEmpty() {
        for (Study s : studiesList) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public String getPatientBirthTime() {
        return patientBirthTime;
    }

    public void setPatientBirthTime(String patientBirthTime) {
        this.patientBirthTime = patientBirthTime;
    }

    public String getPatientBirthDate() {
        return patientBirthDate;
    }

    public void setPatientBirthDate(String patientBirthDate) {
        this.patientBirthDate = patientBirthDate;
    }

    public String getPatientSex() {
        return patientSex;
    }

    public void setPatientSex(String patientSex) {
        if (patientSex == null) {
            this.patientSex = null;
        } else {
            String val = patientSex.toUpperCase(Locale.getDefault());
            this.patientSex = val.startsWith("M") ? "M" : val.startsWith("F") ? "F" : "O";
        }
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName == null ? "" : patientName.replace("^", " ").trim();
    }

    public void addStudy(Study study) {
        if (!studiesList.contains(study)) {
            studiesList.add(study);
        }
    }

    /**
     *
     * @return
     */
    @Override
    public String toXml() {
        StringBuilder result = new StringBuilder();
        if (patientID != null && patientName != null) {
            result.append("\n<");
            result.append(TagUtil.Level.PATIENT);
            result.append(" ");

            TagUtil.addXmlAttribute(Tag.PatientID, patientID, result);
            TagUtil.addXmlAttribute(Tag.IssuerOfPatientID, issuerOfPatientID, result);
            TagUtil.addXmlAttribute(Tag.PatientName, patientName, result);
            TagUtil.addXmlAttribute(Tag.PatientBirthDate, patientBirthDate, result);
            TagUtil.addXmlAttribute(Tag.PatientBirthTime, patientBirthTime, result);
            TagUtil.addXmlAttribute(Tag.PatientSex, patientSex, result);
            result.append(">");

            Collections.sort(studiesList, getStudyComparator());
            for (Study s : studiesList) {
                result.append(s.toXml());
            }
            result.append("\n</");
            result.append(TagUtil.Level.PATIENT);
            result.append(">");
        }

        String ptXml = result.toString();
        LOGGER.debug("Patient toXml [{}]", ptXml);
        return ptXml;
    }

    private static Comparator<Study> getStudyComparator() {
        return new Comparator<Study>() {

            @Override
            public int compare(Study o1, Study o2) {
                Date date1 = DateUtil.dateTime(DateUtil.getDicomDate(o1.getStudyDate()),
                    DateUtil.getDicomTime(o1.getStudyTime()));
                Date date2 = DateUtil.dateTime(DateUtil.getDicomDate(o2.getStudyDate()),
                    DateUtil.getDicomTime(o2.getStudyTime()));

                int c = -1;
                if (date1 != null && date2 != null) {
                    // inverse time
                    c = date2.compareTo(date1);
                    if (c != 0) {
                        return c;
                    }
                }

                if (c == 0 || (date1 == null && date2 == null)) {
                    String d1 = o1.getStudyDescription();
                    String d2 = o2.getStudyDescription();
                    if (d1 != null && d2 != null) {
                        c = Collator.getInstance(Locale.getDefault()).compare(d1, d2);
                        if (c != 0) {
                            return c;
                        }
                    }
                    if (d1 == null) {
                        // Add o1 after o2
                        return d2 == null ? 0 : 1;
                    }
                    // Add o2 after o1
                    return -1;
                } else {
                    if (date1 == null) {
                        // Add o1 after o2
                        return 1;
                    }
                    if (date2 == null) {
                        return -1;
                    }
                }
                return o1.getStudyInstanceUID().compareTo(o2.getStudyInstanceUID());
            }
        };
    }

    public Study getStudy(String uid) {
        for (Study s : studiesList) {
            if (s.getStudyInstanceUID().equals(uid)) {
                return s;
            }
        }
        return null;
    }

}
