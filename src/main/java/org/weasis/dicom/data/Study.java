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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.dcm4che3.data.Tag;
import org.weasis.dicom.data.xml.TagUtil;
import org.weasis.dicom.data.xml.XmlDescription;

public class Study implements XmlDescription {

    private final String studyInstanceUID;
    private String studyID = null;
    private String studyDescription = null;
    private String studyDate = null;
    private String studyTime = null;
    private String accessionNumber = null;
    private String referringPhysicianName = null;
    private final List<Series> seriesList;

    public Study(String studyInstanceUID) {
        if (studyInstanceUID == null) {
            throw new IllegalArgumentException("studyInstanceUID cannot be null!");
        }
        this.studyInstanceUID = studyInstanceUID;
        seriesList = new ArrayList<>();
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public String getStudyDescription() {
        return studyDescription;
    }

    public String getStudyDate() {
        return studyDate;
    }

    public String getStudyID() {
        return studyID;
    }

    public void setStudyID(String studyID) {
        this.studyID = studyID;
    }

    public String getStudyTime() {
        return studyTime;
    }

    public void setStudyTime(String studyTime) {
        this.studyTime = studyTime;
    }

    public String getReferringPhysicianName() {
        return referringPhysicianName;
    }

    public void setReferringPhysicianName(String referringPhysicianName) {
        this.referringPhysicianName = referringPhysicianName;
    }

    public void setStudyDescription(String studyDesc) {
        this.studyDescription = studyDesc;
    }

    public void setStudyDate(String studyDate) {
        this.studyDate = studyDate;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public void addSeries(Series s) {
        if (!seriesList.contains(s)) {
            seriesList.add(s);
        }
    }

    @Override
    public String toXml() {
        StringBuilder result = new StringBuilder();
        if (studyInstanceUID != null) {
            result.append("\n<");
            result.append(TagUtil.Level.STUDY);
            result.append(" ");
            TagUtil.addXmlAttribute(Tag.StudyInstanceUID, studyInstanceUID, result);
            TagUtil.addXmlAttribute(Tag.StudyDescription, studyDescription, result);
            TagUtil.addXmlAttribute(Tag.StudyDate, studyDate, result);
            TagUtil.addXmlAttribute(Tag.StudyTime, studyTime, result);
            TagUtil.addXmlAttribute(Tag.AccessionNumber, accessionNumber, result);
            TagUtil.addXmlAttribute(Tag.StudyID, studyID, result);
            TagUtil.addXmlAttribute(Tag.ReferringPhysicianName, referringPhysicianName, result);
            result.append(">");
            Collections.sort(seriesList, new Comparator<Series>() {

                @Override
                public int compare(Series o1, Series o2) {
                    Integer val1 = Series.getInteger(o1.getSeriesNumber());
                    Integer val2 = Series.getInteger(o2.getSeriesNumber());

                    int c = -1;
                    if (val1 != null && val2 != null) {
                        c = val1.compareTo(val2);
                        if (c != 0) {
                            return c;
                        }
                    }

                    if (c == 0 || (val1 == null && val2 == null)) {
                        return o1.getSeriesInstanceUID().compareTo(o2.getSeriesInstanceUID());
                    } else {
                        if (val1 == null) {
                            // Add o1 after o2
                            return 1;
                        }
                        if (val2 == null) {
                            return -1;
                        }
                    }

                    return o1.getSeriesInstanceUID().compareTo(o2.getSeriesInstanceUID());
                }
            });
            for (Series s : seriesList) {
                result.append(s.toXml());
            }

            result.append("\n</");
            result.append(TagUtil.Level.STUDY);
            result.append(">");
        }
        return result.toString();
    }

    public boolean isEmpty() {
        for (Series s : seriesList) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public Series getSeries(String uid) {
        for (Series s : seriesList) {
            if (s.getSeriesInstanceUID().equals(uid)) {
                return s;
            }
        }
        return null;
    }

    public List<Series> getSeriesList() {
        return seriesList;
    }

}
