package org.weasis.query.dicom;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.service.QueryRetrieveLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.data.Patient;
import org.weasis.dicom.data.SOPInstance;
import org.weasis.dicom.data.Series;
import org.weasis.dicom.data.Study;
import org.weasis.dicom.mf.ArcQuery.ViewerMessage;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.TlsOptions;
import org.weasis.dicom.util.DateUtil;
import org.weasis.dicom.util.StringUtil;
import org.weasis.query.AbstractQueryConfiguration;
import org.weasis.query.CommonQueryParams;

public class DicomQueryConfiguration extends AbstractQueryConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomQueryConfiguration.class);

    private final DicomNode callingNode;
    private final DicomNode calledNode;
    private final AdvancedParams advancedParams;

    public DicomQueryConfiguration(Properties properties, DicomNode callingNode) {
        super(properties);
        if (callingNode == null) {
            throw new IllegalArgumentException("callingNode cannot be null!");
        }
        this.callingNode = callingNode;
        this.calledNode = new DicomNode(properties.getProperty("arc.aet", "DCM4CHEE"),
            properties.getProperty("arc.host", "localhost"),
            Integer.parseInt(properties.getProperty("arc.port", "11112")));
        this.advancedParams = buildAdvancedParams();
    }

    private AdvancedParams buildAdvancedParams() {
        boolean tls = StringUtil.getNULLtoFalse(properties.getProperty("arc.tls.mode"));
        AdvancedParams params = null;
        if (tls) {
            TlsOptions tlsOptions =
                new TlsOptions(StringUtil.getNULLtoFalse(properties.getProperty("arc.tlsNeedClientAuth")),
                    properties.getProperty("arc.keystoreURL"), properties.getProperty("arc.keystoreType", "JKS"),
                    properties.getProperty("arc.keystorePass"),
                    properties.getProperty("arc.keyPass", properties.getProperty("arc.keystorePass")),
                    properties.getProperty("arc.truststoreURL"), properties.getProperty("arc.truststoreType", "JKS"),
                    properties.getProperty("arc.truststorePass"));
            params = new AdvancedParams();
            params.setTlsOptions(tlsOptions);
        }
        return params;
    }

    public DicomNode getCalledNode() {
        return calledNode;
    }

    public AdvancedParams getAdvancedParams() {
        return advancedParams;
    }

    public DicomNode getCallingNode() {
        return callingNode;
    }

    @Override
    public void buildFromPatientID(CommonQueryParams params, String... patientIDs) {
        for (String patientID : patientIDs) {
            if (!StringUtil.hasText(patientID)) {
                continue;
            }

            int beginIndex = patientID.indexOf("^^^");
            int offset = 3;
            // IssuerOfPatientID filter ( syntax like in HL7 with extension^^^root)
            if (beginIndex == -1) {
                // if patientID has been encrypted
                beginIndex = patientID.indexOf("%5E%5E%5E");
                offset = 9;
            }

            DicomParam[] keysStudies = {
                // Matching Keys
                new DicomParam(Tag.PatientID, beginIndex < 0 ? patientID : patientID.substring(0, beginIndex)),
                // Return Keys, IssuerOfPatientID is a return key except when passed as a extension of PatientID
                new DicomParam(Tag.IssuerOfPatientID, beginIndex < 0 ? null : patientID.substring(beginIndex + offset)),
                new DicomParam(Tag.PatientName, params.getPatientName()),
                new DicomParam(Tag.PatientBirthDate, params.getPatientBirthDate()), CFind.PatientSex,
                CFind.ReferringPhysicianName, CFind.StudyDescription, CFind.StudyDate, CFind.StudyTime,
                CFind.AccessionNumber, CFind.StudyInstanceUID, CFind.StudyID, new DicomParam(Tag.ModalitiesInStudy) };

            try {
                DicomState state =
                    CFind.process(advancedParams, callingNode, calledNode, 0, QueryRetrieveLevel.STUDY, keysStudies);

                List<Attributes> studies = state.getDicomRSP();
                if (studies != null && !studies.isEmpty()) {
                    Collections.sort(studies, getStudyComparator());
                    applyAllFilters(params, studies);
                }
            } catch (Exception t) {
                LOGGER.error("DICOM query Error of {}", getArchiveConfigName(), t);
            }
        }
    }

    private void applyAllFilters(CommonQueryParams params, List<Attributes> studies) {
        if (StringUtil.hasText(params.getLowerDateTime())) {
            Date lowerDateTime = null;
            try {
                lowerDateTime = javax.xml.bind.DatatypeConverter.parseDateTime(params.getLowerDateTime()).getTime();
            } catch (Exception e) {
                LOGGER.error("Cannot parse date: {}", params.getLowerDateTime(), e);
            }
            if (lowerDateTime != null) {
                for (int i = studies.size() - 1; i >= 0; i--) {
                    Attributes s = studies.get(i);
                    Date date = DateUtil.dateTime(s.getDate(Tag.StudyDate), s.getDate(Tag.StudyTime));
                    if (date != null) {
                        int rep = date.compareTo(lowerDateTime);
                        if (rep > 0) {
                            studies.remove(i);
                        }
                    }
                }
            }
        }

        if (StringUtil.hasText(params.getUpperDateTime())) {
            Date upperDateTime = null;
            try {
                upperDateTime = javax.xml.bind.DatatypeConverter.parseDateTime(params.getUpperDateTime()).getTime();
            } catch (Exception e) {
                LOGGER.error("Cannot parse date: {}", params.getUpperDateTime(), e);
            }
            if (upperDateTime != null) {
                for (int i = studies.size() - 1; i >= 0; i--) {
                    Attributes s = studies.get(i);
                    Date date = DateUtil.dateTime(s.getDate(Tag.StudyDate), s.getDate(Tag.StudyTime));
                    if (date != null) {
                        int rep = date.compareTo(upperDateTime);
                        if (rep < 0) {
                            studies.remove(i);
                        }
                    }
                }
            }
        }

        if (StringUtil.hasText(params.getMostRecentResults())) {
            int recent = StringUtil.getInteger(params.getMostRecentResults());
            if (recent > 0) {
                for (int i = studies.size() - 1; i >= recent; i--) {
                    studies.remove(i);
                }
            }
        }

        if (StringUtil.hasText(params.getModalitiesInStudy())) {
            for (int i = studies.size() - 1; i >= 0; i--) {
                Attributes s = studies.get(i);
                String m = s.getString(Tag.ModalitiesInStudy);
                if (StringUtil.hasText(m)) {
                    boolean remove = true;
                    for (String mod : params.getModalitiesInStudy().split(",")) {
                        if (m.indexOf(mod) != -1) {
                            remove = false;
                            break;
                        }
                    }

                    if (remove) {
                        studies.remove(i);
                    }
                }
            }

        }

        if (StringUtil.hasText(params.getKeywords())) {
            String[] keys = params.getKeywords().split(",");
            for (int i = 0; i < keys.length; i++) {
                keys[i] = StringUtil.deAccent(keys[i].trim().toUpperCase());
            }

            studyLabel: for (int i = studies.size() - 1; i >= 0; i--) {
                Attributes s = studies.get(i);
                String desc = StringUtil.deAccent(s.getString(Tag.StudyDescription, "").toUpperCase());

                for (int j = 0; j < keys.length; j++) {
                    if (desc.contains(keys[j])) {
                        continue studyLabel;
                    }
                }
                studies.remove(i);
            }
        }

        for (Attributes studyDataSet : studies) {
            fillSeries(studyDataSet);
        }
    }

    private static Comparator<Attributes> getStudyComparator() {
        return new Comparator<Attributes>() {

            @Override
            public int compare(Attributes o1, Attributes o2) {
                Date date1 = o1.getDate(Tag.StudyDate);
                Date date2 = o2.getDate(Tag.StudyDate);
                if (date1 != null && date2 != null) {
                    // inverse time
                    int rep = date2.compareTo(date1);
                    if (rep == 0) {
                        Date time1 = o1.getDate(Tag.StudyTime);
                        Date time2 = o2.getDate(Tag.StudyTime);
                        if (time1 != null && time2 != null) {
                            // inverse time
                            return time2.compareTo(time1);
                        }
                    } else {
                        return rep;
                    }
                }
                if (date1 == null && date2 == null) {
                    return o1.getString(Tag.StudyInstanceUID, "").compareTo(o2.getString(Tag.StudyInstanceUID, ""));
                } else {
                    if (date1 == null) {
                        return 1;
                    }
                    if (date2 == null) {
                        return -1;
                    }
                }
                return 0;
            }
        };
    }

    @Override
    public void buildFromStudyInstanceUID(CommonQueryParams params, String... studyInstanceUIDs) {
        for (String studyInstanceUID : studyInstanceUIDs) {
            if (!StringUtil.hasText(studyInstanceUID)) {
                continue;
            }
            DicomParam[] keysStudies = {
                // Matching Keys
                new DicomParam(Tag.StudyInstanceUID, studyInstanceUID),
                // Return Keys
                CFind.PatientID, CFind.IssuerOfPatientID, CFind.PatientName, CFind.PatientBirthDate, CFind.PatientSex,
                CFind.ReferringPhysicianName, CFind.StudyDescription, CFind.StudyDate, CFind.StudyTime,
                CFind.AccessionNumber, CFind.StudyID };

            fillStudy(keysStudies);
        }
    }

    @Override
    public void buildFromStudyAccessionNumber(CommonQueryParams params, String... accessionNumbers) {
        for (String accessionNumber : accessionNumbers) {
            if (!StringUtil.hasText(accessionNumber)) {
                continue;
            }
            DicomParam[] keysStudies = {
                // Matching Keys
                new DicomParam(Tag.AccessionNumber, accessionNumber),
                // Return Keys
                CFind.PatientID, CFind.IssuerOfPatientID, CFind.PatientName, CFind.PatientBirthDate, CFind.PatientSex,
                CFind.ReferringPhysicianName, CFind.StudyDescription, CFind.StudyDate, CFind.StudyTime,
                CFind.StudyInstanceUID, CFind.StudyID };

            fillStudy(keysStudies);
        }
    }

    @Override
    public void buildFromSeriesInstanceUID(CommonQueryParams params, String... seriesInstanceUIDs) {
        for (String seriesInstanceUID : seriesInstanceUIDs) {
            if (!StringUtil.hasText(seriesInstanceUID)) {
                continue;
            }

            DicomParam[] keysSeries = {
                // Matching Keys
                new DicomParam(Tag.SeriesInstanceUID, seriesInstanceUID),
                // Return Keys
                CFind.PatientID, CFind.IssuerOfPatientID, CFind.PatientName, CFind.PatientBirthDate, CFind.PatientSex,
                CFind.ReferringPhysicianName, CFind.StudyDescription, CFind.StudyDate, CFind.StudyTime,
                CFind.AccessionNumber, CFind.StudyInstanceUID, CFind.StudyID, CFind.Modality, CFind.SeriesNumber,
                CFind.SeriesDescription };

            try {
                DicomState state =
                    CFind.process(advancedParams, callingNode, calledNode, 0, QueryRetrieveLevel.SERIES, keysSeries);

                List<Attributes> series = state.getDicomRSP();
                if (series != null && !series.isEmpty()) {
                    Attributes dataset = series.get(0);
                    Patient patient = getPatient(dataset);
                    Study study = getStudy(patient, dataset);
                    for (Attributes seriesDataset : series) {
                        fillInstance(seriesDataset, study);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("DICOM query Error of {}", getArchiveConfigName(), e);
            }
        }
    }

    @Override
    public void buildFromSopInstanceUID(CommonQueryParams params, String... sopInstanceUIDs) {
        for (String sopInstanceUID : sopInstanceUIDs) {
            if (!StringUtil.hasText(sopInstanceUID)) {
                continue;
            }

            DicomParam[] keysInstance = {
                // Matching Keys
                new DicomParam(Tag.SOPInstanceUID, sopInstanceUID),
                // Return Keys
                CFind.PatientID, CFind.IssuerOfPatientID, CFind.PatientName, CFind.PatientBirthDate, CFind.PatientSex,
                CFind.ReferringPhysicianName, CFind.StudyDescription, CFind.StudyDate, CFind.StudyTime,
                CFind.AccessionNumber, CFind.StudyInstanceUID, CFind.StudyID, CFind.SeriesInstanceUID, CFind.Modality,
                CFind.SeriesNumber, CFind.SeriesDescription };

            try {
                DicomState state =
                    CFind.process(advancedParams, callingNode, calledNode, 0, QueryRetrieveLevel.IMAGE, keysInstance);

                List<Attributes> instances = state.getDicomRSP();
                if (instances != null && !instances.isEmpty()) {
                    Attributes dataset = instances.get(0);
                    Patient patient = getPatient(dataset);
                    Study study = getStudy(patient, dataset);
                    Series s = getSeries(study, dataset);
                    for (Attributes instanceDataSet : instances) {
                        String sopUID = instanceDataSet.getString(Tag.SOPInstanceUID);
                        if (sopUID != null) {
                            SOPInstance sop = new SOPInstance(sopUID);
                            sop.setInstanceNumber(instanceDataSet.getString(Tag.InstanceNumber));
                            s.addSOPInstance(sop);
                        }
                    }
                }
            } catch (Exception e) {
                String msg = "DICOM query Error of {}" + getArchiveConfigName();
                LOGGER.error(msg, e);
                setViewerMessage(new ViewerMessage(msg, e.getMessage(), ViewerMessage.eLevel.ERROR));
            }
        }
    }

    private void fillStudy(DicomParam[] keysStudies) {
        try {
            DicomState state =
                CFind.process(advancedParams, callingNode, calledNode, 0, QueryRetrieveLevel.STUDY, keysStudies);

            List<Attributes> studies = state.getDicomRSP();
            if (studies != null) {
                for (Attributes studyDataSet : studies) {
                    fillSeries(studyDataSet);
                }
            }
        } catch (Exception e) {
            LOGGER.error("DICOM query Error of {}", getArchiveConfigName(), e);
        }
    }

    private void fillSeries(Attributes studyDataSet) {
        String studyInstanceUID = studyDataSet.getString(Tag.StudyInstanceUID);
        if (StringUtil.hasText(studyInstanceUID)) {

            DicomParam[] keysSeries = {
                // Matching Keys
                new DicomParam(Tag.StudyInstanceUID, studyInstanceUID),
                // Return Keys
                CFind.SeriesInstanceUID, CFind.Modality, CFind.SeriesNumber, CFind.SeriesDescription };

            DicomState state =
                CFind.process(advancedParams, callingNode, calledNode, 0, QueryRetrieveLevel.SERIES, keysSeries);

            List<Attributes> series = state.getDicomRSP();
            if (series != null && !series.isEmpty()) {
                // Get patient from each study in case IssuerOfPatientID is different
                Patient patient = getPatient(studyDataSet);
                Study study = getStudy(patient, studyDataSet);
                for (Attributes seriesDataset : series) {
                    fillInstance(seriesDataset, study);
                }
            }
        }
    }

    private void fillInstance(Attributes seriesDataset, Study study) {
        String serieInstanceUID = seriesDataset.getString(Tag.SeriesInstanceUID);
        if (StringUtil.hasText(serieInstanceUID)) {
            DicomParam[] keysInstance = {
                // Matching Keys
                new DicomParam(Tag.StudyInstanceUID, study.getStudyInstanceUID()),
                new DicomParam(Tag.SeriesInstanceUID, serieInstanceUID),
                // Return Keys
                CFind.SOPInstanceUID, CFind.InstanceNumber };
            DicomState state =
                CFind.process(advancedParams, callingNode, calledNode, 0, QueryRetrieveLevel.IMAGE, keysInstance);

            List<Attributes> instances = state.getDicomRSP();
            if (instances != null && !instances.isEmpty()) {
                Series s = getSeries(study, seriesDataset);

                for (Attributes instanceDataSet : instances) {
                    String sopUID = instanceDataSet.getString(Tag.SOPInstanceUID);
                    if (sopUID != null) {
                        SOPInstance sop = new SOPInstance(sopUID);
                        sop.setInstanceNumber(instanceDataSet.getString(Tag.InstanceNumber));
                        s.addSOPInstance(sop);
                    }
                }
            }
        }
    }

    private Patient getPatient(Attributes patientDataset) {
        if (patientDataset == null) {
            throw new IllegalArgumentException("patientDataset cannot be null");
        }

        fillPatientAttributes(patientDataset);

        String id = patientDataset.getString(Tag.PatientID, "Unknown");
        String ispid = patientDataset.getString(Tag.IssuerOfPatientID);
        for (Patient p : patients) {
            if (p.hasSameUniqueID(id, ispid)) {
                return p;
            }
        }
        Patient p = new Patient(id, ispid);
        p.setPatientName(patientDataset.getString(Tag.PatientName));
        // Only set birth date, birth time is often not consistent (00:00)
        p.setPatientBirthDate(patientDataset.getString(Tag.PatientBirthDate));
        p.setPatientSex(patientDataset.getString(Tag.PatientSex));
        patients.add(p);
        return p;
    }

    private void fillPatientAttributes(Attributes patientDataset) {
        // Request at SERIES level without relational model can respond without a Patient ID
        if (!patientDataset.contains(Tag.PatientID)) {
            // Request at IMAGE level without relational model can respond without a Study Instance UID
            if (!patientDataset.contains(Tag.StudyInstanceUID)) {
                String seriesInstanceUID = patientDataset.getString(Tag.SeriesInstanceUID);
                if (!StringUtil.hasText(seriesInstanceUID)) {
                    throw new IllegalStateException("Cannot get Series Instance UID from C-Find");
                }
                DicomParam[] keysSeries = {
                    // Matching Keys
                    new DicomParam(Tag.SeriesInstanceUID, patientDataset.getString(Tag.SeriesInstanceUID)),
                    // Return Keys
                    CFind.StudyInstanceUID, CFind.Modality, CFind.SeriesNumber, CFind.SeriesDescription };

                DicomState state =
                    CFind.process(advancedParams, callingNode, calledNode, 0, QueryRetrieveLevel.SERIES, keysSeries);
                List<Attributes> series = state.getDicomRSP();
                if (series.isEmpty()) {
                    throw new IllegalStateException("Get empty C-Find reply at Series level for " + seriesInstanceUID);
                }
                patientDataset.addAll(series.get(0));
            }

            String studyInstanceUID = patientDataset.getString(Tag.StudyInstanceUID);
            if (!StringUtil.hasText(studyInstanceUID)) {
                throw new IllegalStateException("Cannot get Study Instance UID from C-Find");
            }
            DicomParam[] keysStudies = {
                // Matching Keys
                new DicomParam(Tag.StudyInstanceUID, studyInstanceUID),
                // Return Keys
                CFind.PatientID, CFind.IssuerOfPatientID, CFind.PatientName, CFind.PatientBirthDate, CFind.PatientSex,
                CFind.ReferringPhysicianName, CFind.StudyDescription, CFind.StudyDate, CFind.StudyTime,
                CFind.AccessionNumber, CFind.StudyID };

            DicomState state =
                CFind.process(advancedParams, callingNode, calledNode, 0, QueryRetrieveLevel.STUDY, keysStudies);

            List<Attributes> studies = state.getDicomRSP();
            if (studies.isEmpty()) {
                throw new IllegalStateException("Get empty C-Find reply at Study level for " + studyInstanceUID);
            }
            patientDataset.addAll(studies.get(0));
        }
    }

    private static Study getStudy(Patient patient, final Attributes studyDataset) {
        if (studyDataset == null) {
            throw new IllegalArgumentException("studyDataset cannot be null");
        }
        String uid = studyDataset.getString(Tag.StudyInstanceUID);
        Study s = patient.getStudy(uid);
        if (s == null) {
            s = new Study(uid);
            s.setStudyDescription(studyDataset.getString(Tag.StudyDescription));
            s.setStudyDate(studyDataset.getString(Tag.StudyDate));
            s.setStudyTime(studyDataset.getString(Tag.StudyTime));
            s.setAccessionNumber(studyDataset.getString(Tag.AccessionNumber));
            s.setStudyID(studyDataset.getString(Tag.StudyID));
            s.setReferringPhysicianName(studyDataset.getString(Tag.ReferringPhysicianName));
            patient.addStudy(s);
        }
        return s;
    }

    private static Series getSeries(Study study, final Attributes seriesDataset) {
        if (seriesDataset == null) {
            throw new IllegalArgumentException("seriesDataset cannot be null");
        }
        String uid = seriesDataset.getString(Tag.SeriesInstanceUID);
        Series s = study.getSeries(uid);
        if (s == null) {
            s = new Series(uid);
            s.setModality(seriesDataset.getString(Tag.Modality));
            s.setSeriesNumber(seriesDataset.getString(Tag.SeriesNumber));
            s.setSeriesDescription(seriesDataset.getString(Tag.SeriesDescription));
            study.addSeries(s);
        }
        return s;
    }

}
