package org.weasis.dicom.wado;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.data.Patient;
import org.weasis.dicom.data.Series;
import org.weasis.dicom.data.Study;
import org.weasis.dicom.data.xml.Base64;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.TlsOptions;
import org.weasis.dicom.util.StringUtil;
import org.weasis.dicom.wado.WadoQuery.WadoMessage;

public class PacsConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(PacsConfiguration.class);

    private final List<Patient> patients;
    private final List<WadoMessage> wadoMessages;
    private final DicomNode calledNode;
    private final AdvancedParams advancedParams;
    private final Properties properties;

    public PacsConfiguration(Properties properties) {
        this.properties = properties;
        this.patients = new ArrayList<Patient>();
        this.wadoMessages = new ArrayList<WadoQuery.WadoMessage>();
        this.calledNode = new DicomNode(properties.getProperty("pacs.aet", "DCM4CHEE"),
            properties.getProperty("pacs.host", "localhost"),
            Integer.parseInt(properties.getProperty("pacs.port", "11112")));
        this.advancedParams = buildAdvancedParams();
    }

    private AdvancedParams buildAdvancedParams() {
        boolean tls = StringUtil.getNULLtoFalse(properties.getProperty("pacs.tls.mode"));
        AdvancedParams params = null;
        if (tls) {
            try {
                TlsOptions tlsOptions = new TlsOptions(
                    StringUtil.getNULLtoFalse(properties.getProperty("pacs.tlsNeedClientAuth")),
                    properties.getProperty("pacs.keystoreURL"), properties.getProperty("pacs.keystoreType", "JKS"),
                    properties.getProperty("pacs.keystorePass"),
                    properties.getProperty("pacs.keyPass", properties.getProperty("pacs.keystorePass")),
                    properties.getProperty("pacs.truststoreURL"), properties.getProperty("pacs.truststoreType", "JKS"),
                    properties.getProperty("pacs.truststorePass"));
                params = new AdvancedParams();
                params.setTlsOptions(tlsOptions);
            } catch (Exception e) {
                StringUtil.logError(LOGGER, e, "Cannot set TLS configuration");
            }
        }
        return params;
    }

    public String getCharsetEncoding() {
        return properties.getProperty("pacs.db.encoding", "UTF-8");
    }

    public WadoParameters getWadoParameters() {
        String wadoQueriesURL =
            properties.getProperty("pacs.wado.url", properties.getProperty("server.base.url") + "/wado");
        boolean onlysopuid = StringUtil.getNULLtoFalse(properties.getProperty("wado.onlysopuid"));
        String addparams = properties.getProperty("wado.addparams", "");
        String overrideTags = properties.getProperty("wado.override.tags", null);
        // If the web server requires an authentication (pacs.web.login=user:pwd)
        String webLogin = properties.getProperty("pacs.web.login", null);
        if (webLogin != null) {
            webLogin = Base64.encodeBytes(webLogin.trim().getBytes());
        }
        String httpTags = properties.getProperty("wado.httpTags", null);

        WadoParameters wado = new WadoParameters(wadoQueriesURL, onlysopuid, addparams, overrideTags, webLogin);
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

    public DicomNode getCalledNode() {
        return calledNode;
    }

    public AdvancedParams getAdvancedParams() {
        return advancedParams;
    }

    public Properties getProperties() {
        return properties;
    }

}
