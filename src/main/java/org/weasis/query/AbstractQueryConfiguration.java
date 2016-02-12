package org.weasis.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.weasis.dicom.data.Patient;
import org.weasis.dicom.data.Series;
import org.weasis.dicom.data.Study;
import org.weasis.dicom.data.xml.Base64;
import org.weasis.dicom.util.StringUtil;
import org.weasis.dicom.wado.WadoParameters;
import org.weasis.dicom.wado.WadoQuery;
import org.weasis.dicom.wado.WadoQuery.WadoMessage;

public abstract class AbstractQueryConfiguration {

    protected final List<Patient> patients;
    protected final List<WadoMessage> wadoMessages;
    protected final Properties properties;

    public AbstractQueryConfiguration(Properties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties cannot be null!");
        }
        this.properties = properties;
        this.patients = new ArrayList<Patient>();
        this.wadoMessages = new ArrayList<WadoQuery.WadoMessage>();
    }

    public abstract void buildFromPatientID(CommonQueryParams params, String... patientIDs) throws Exception;

    public abstract void buildFromStudyInstanceUID(CommonQueryParams params, String... studyInstanceUIDs) throws Exception;

    public abstract void buildFromStudyAccessionNumber(CommonQueryParams params, String... accessionNumbers)
        throws Exception;

    public abstract void buildFromSeriesInstanceUID(CommonQueryParams params, String... seriesInstanceUIDs)
        throws Exception;

    public abstract void buildFromSopInstanceUID(CommonQueryParams params, String... sopInstanceUIDs) throws Exception;

    public String getCharsetEncoding() {
        // Not required with DICOM C-FIND (handle with attributes.getString(...))
        return properties.getProperty("arc.db.encoding", "UTF-8");
    }

    public WadoParameters getWadoParameters() {
        String wadoQueriesURL =
            properties.getProperty("arc.wado.url", properties.getProperty("server.base.url") + "/wado");
        boolean onlysopuid = StringUtil.getNULLtoFalse(properties.getProperty("wado.onlysopuid"));
        String addparams = properties.getProperty("wado.addparams", "");
        String overrideTags = properties.getProperty("wado.override.tags");
        // If the web server requires an authentication (arc.web.login=user:pwd)
        String webLogin = properties.getProperty("arc.web.login");
        if (webLogin != null) {
            webLogin = Base64.encodeBytes(webLogin.trim().getBytes());
        }
        String httpTags = properties.getProperty("wado.httpTags");

        WadoParameters wado = new WadoParameters(properties.getProperty("arc.id"), wadoQueriesURL, onlysopuid, addparams, overrideTags, webLogin);
        if (httpTags != null && !httpTags.trim().equals("")) {
            for (String tag : httpTags.split(",")) {
                String[] val = tag.split(":");
                if (val.length == 2) {
                    wado.addHttpTag(val[0].trim(), val[1].trim());
                }
            }
        }
        return wado;
    }

    public void removePatientId(List<String> patientIdList) {
        if (patientIdList != null && !patientIdList.isEmpty()) {
            for (int i = patients.size() - 1; i >= 0; i--) {
                if (!patientIdList.contains(patients.get(i).getPatientID())) {
                    patients.remove(i);
                }
            }
        }
    }

    public void removeStudyUid(List<String> studyUidList) {
        if (studyUidList != null && !studyUidList.isEmpty()) {
            for (Patient p : patients) {
                List<Study> studies = p.getStudies();
                for (int i = studies.size() - 1; i >= 0; i--) {
                    if (!studyUidList.contains(studies.get(i).getStudyInstanceUID())) {
                        studies.remove(i);
                    }
                }
            }
        }
    }

    public void removeAccessionNumber(List<String> accessionNumberList) {
        if (accessionNumberList != null && !accessionNumberList.isEmpty()) {
            for (Patient p : patients) {
                List<Study> studies = p.getStudies();
                for (int i = studies.size() - 1; i >= 0; i--) {
                    if (!accessionNumberList.contains(studies.get(i).getAccessionNumber())) {
                        studies.remove(i);
                    }
                }
            }
        }
    }

    public void removeSeriesUid(List<String> seriesUidList) {
        if (seriesUidList != null && !seriesUidList.isEmpty()) {
            for (Patient p : patients) {
                List<Study> studies = p.getStudies();
                for (int i = studies.size() - 1; i >= 0; i--) {
                    List<Series> series = studies.get(i).getSeriesList();
                    for (int k = series.size() - 1; k >= 0; k--) {
                        if (!seriesUidList.contains(series.get(k).getSeriesInstanceUID())) {
                            series.remove(k);
                            if (series.isEmpty()) {
                                studies.remove(i);
                            }
                        }
                    }
                }
            }
        }
    }

    public List<Patient> getPatients() {
        return patients;
    }

    public List<WadoMessage> getWadoMessages() {
        return wadoMessages;
    }

    public Properties getProperties() {
        return properties;
    }

}