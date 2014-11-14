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

package org.weasis.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.data.Patient;
import org.weasis.dicom.data.Series;
import org.weasis.dicom.data.Study;
import org.weasis.dicom.data.xml.Base64;
import org.weasis.dicom.data.xml.TagUtil;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.util.StringUtil;
import org.weasis.dicom.util.StringUtil.Suffix;
import org.weasis.dicom.wado.BuildManifestDcmQR;
import org.weasis.dicom.wado.DicomQueryParams;
import org.weasis.dicom.wado.WadoParameters;
import org.weasis.dicom.wado.WadoQuery;
import org.weasis.dicom.wado.WadoQueryException;
import org.weasis.util.EncryptUtils;

public class WeasisLauncher extends HttpServlet {
    private static final long serialVersionUID = 8946852726380985736L;
    private static final Logger LOGGER = LoggerFactory.getLogger(WeasisLauncher.class);

    static final String JNLP_MIME_TYPE = "application/x-java-jnlp-file";

    static final String PatientID = "patientID";
    static final String StudyUID = "studyUID";
    static final String AccessionNumber = "accessionNumber";
    static final String SeriesUID = "seriesUID";
    static final String ObjectUID = "objectUID";

    static final Properties pacsProperties = new Properties();

    /**
     * Constructor of the object.
     */
    public WeasisLauncher() {
        super();
    }

    /**
     * Initialization of the servlet. <br>
     * 
     * @throws ServletException
     *             if an error occurs
     */
    @Override
    public void init() throws ServletException {
        LOGGER.debug("init() - getServletContext : {} ", getServletConfig().getServletContext());
        LOGGER.debug("init() - getRealPath : {}", getServletConfig().getServletContext().getRealPath("/"));
        try {
            URL config = this.getClass().getResource("/weasis-pacs-connector.properties");
            if (config == null) {
                config = this.getClass().getResource("/weasis-connector-default.properties");
                LOGGER.info("Default configuration file : {}", config);
            } else {
                LOGGER.info("External configuration file : {}", config);
            }
            if (config != null) {
                pacsProperties.load(config.openStream());
                String requests = pacsProperties.getProperty("request.ids", null);
                if (requests == null) {
                    LOGGER.error("No request ID is allowed!");
                } else {
                    for (String id : requests.split(",")) {
                        pacsProperties.put(id, "true");
                    }
                }
            } else {
                LOGGER.error("Cannot find  a configuration file for weasis-pacs-connector");
            }
            URL jnlpTemplate = this.getClass().getResource("/weasis-jnlp.xml");
            if (jnlpTemplate == null) {
                jnlpTemplate = this.getClass().getResource("/weasis-jnlp-default.xml");
                LOGGER.info("Default  Weasis template  : {}", jnlpTemplate);
                if (jnlpTemplate == null) {
                    LOGGER.error("Cannot find the default JNLP template");
                }
            } else {
                LOGGER.info("External Weasis template : {}", jnlpTemplate);
            }
            pacsProperties.put("weasis.jnlp", jnlpTemplate.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            response.setContentType(JNLP_MIME_TYPE);

        } catch (Exception e) {
            LOGGER.error("doHead(HttpServletRequest, HttpServletResponse)", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * The doPost method of the servlet. <br>
     * 
     * This method is called when a form has its tag value method equals to post. It is redirected to doGet.
     * 
     * @param request
     *            the request send by the client to the server
     * @param response
     *            the response send by the server to the client
     * @throws ServletException
     *             if an error occurred
     * @throws IOException
     *             if an error occurred
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        invokeWeasis(request, response);
    }

    /**
     * The doGet method of the servlet. <br>
     * 
     * This method is called when a form has its tag value method equals to get.
     * 
     * @param request
     *            the request send by the client to the server
     * @param response
     *            the response send by the server to the client
     * @throws ServletErrorException
     * @throws IOException
     * @throws ServletException
     * @throws ServletException
     *             if an error occurred
     * @throws IOException
     *             if an error occurred
     */

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        invokeWeasis(request, response);
    }

    public static void invokeWeasis(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Test if this client is allowed
        String hosts = pacsProperties.getProperty("hosts.allow");
        if (hosts != null && !hosts.trim().equals("")) {
            String clientHost = request.getRemoteHost();
            String clientIP = request.getRemoteAddr();
            boolean accept = false;
            for (String host : hosts.split(",")) {
                if (host.equals(clientHost) || host.equals(clientIP)) {
                    accept = true;
                    break;
                }
            }
            if (!accept) {
                LOGGER.warn("The request from {} is not allowed.", clientHost);
                return;
            }
        }
        try {
            LOGGER.debug("logRequestInfo(HttpServletRequest) - getRequestQueryURL : {}{}", request.getRequestURL()
                .toString(), request.getQueryString() != null ? ("?" + request.getQueryString().trim()) : "");
            LOGGER.debug("logRequestInfo(HttpServletRequest) - getContextPath : {}", request.getContextPath());
            LOGGER.debug("logRequestInfo(HttpServletRequest) - getRequestURI : {}", request.getRequestURI());
            LOGGER.debug("logRequestInfo(HttpServletRequest) - getServletPath : {}", request.getServletPath());

            response.setCharacterEncoding("UTF-8");
            response.setContentType(JNLP_MIME_TYPE);

            String baseURL = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
            System.setProperty("server.base.url", baseURL);

            Properties dynamicProps = (Properties) pacsProperties.clone();
            // Perform variable substitution for system properties.
            for (Enumeration<?> e = pacsProperties.propertyNames(); e.hasMoreElements();) {
                String name = (String) e.nextElement();
                dynamicProps.setProperty(name,
                    TagUtil.substVars(pacsProperties.getProperty(name), name, null, pacsProperties));
            }

            String wadoQueriesURL = dynamicProps.getProperty("pacs.wado.url", "http://localhost:8080/wado");
            String pacsAET = dynamicProps.getProperty("pacs.aet", "DCM4CHEE");
            String pacsHost = dynamicProps.getProperty("pacs.host", "localhost");
            int pacsPort = Integer.parseInt(dynamicProps.getProperty("pacs.port", "11112"));
            DicomNode calledNode = new DicomNode(pacsAET, pacsHost, pacsPort);

            final DicomQueryParams params =
                new DicomQueryParams(new DicomNode(dynamicProps.getProperty("aet", "WEASIS")), calledNode, null);
            List<Patient> patients = getPatientList(request, params);

            String wadoQueryFile = "";
            boolean acceptNoImage = Boolean.valueOf(dynamicProps.getProperty("accept.noimage"));

            if ((patients == null || patients.size() < 1) && !acceptNoImage) {
                LOGGER.warn("No image has been found!");
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "No image has been found for this request!");
                return;
            }
            try {
                response.setHeader("Content-Disposition", "filename=\"wea-" + getFilename(patients) + ".jnlp\";");
                // If the web server requires an authentication (pacs.web.login=user:pwd)
                String webLogin = dynamicProps.getProperty("pacs.web.login", null);
                if (webLogin != null) {
                    webLogin = Base64.encodeBytes(webLogin.trim().getBytes());

                }
                boolean onlysopuid = Boolean.valueOf(dynamicProps.getProperty("wado.onlysopuid"));
                String addparams = dynamicProps.getProperty("wado.addparams", "");
                String overrideTags = dynamicProps.getProperty("wado.override.tags", null);
                String httpTags = dynamicProps.getProperty("wado.httpTags", null);

                WadoParameters wado = new WadoParameters(wadoQueriesURL, onlysopuid, addparams, overrideTags, webLogin);
                if (httpTags != null && !httpTags.trim().equals("")) {
                    for (String tag : httpTags.split(",")) {
                        String[] val = tag.split(":");
                        if (val.length == 2) {
                            wado.addHttpTag(val[0].trim(), val[1].trim());
                        }
                    }
                }
                WadoQuery wadoQuery =
                    new WadoQuery(patients, wado, dynamicProps.getProperty("pacs.db.encoding", "utf-8"), acceptNoImage);
                wadoQueryFile = Base64.encodeBytes(wadoQuery.toString().getBytes(), Base64.GZIP);

            } catch (WadoQueryException e) {
                LOGGER.error(e.getMessage());
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            InputStream is = null;
            try {
                URL jnlpTemplate = null;
                String template = request.getParameter("jnlp");
                if (template != null && !"".equals(template)) {
                    jnlpTemplate = WeasisLauncher.class.getResource("/" + template);
                }
                if (jnlpTemplate == null) {
                    jnlpTemplate = new URL((String) dynamicProps.get("weasis.jnlp"));
                } else {
                    LOGGER.info("External Weasis template : {}", jnlpTemplate);
                }

                is = jnlpTemplate.openStream();
                BufferedReader dis = new BufferedReader(new InputStreamReader(is));
                // response.setContentLength(launcherStr.length());
                String weasisBaseURL = dynamicProps.getProperty("weasis.base.url", baseURL);

                PrintWriter outWriter = response.getWriter();
                String s;
                while ((s = dis.readLine()) != null) {
                    if (s.trim().equals("</application-desc>")) {
                        outWriter.print("\t\t<argument>$dicom:get -i ");
                        outWriter.print(wadoQueryFile);
                        outWriter.println("</argument>");
                        outWriter.println(s);
                    } else {
                        s = s.replace("${weasis.base.url}", weasisBaseURL);
                        outWriter.println(s);
                    }
                }
                outWriter.close();
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                } catch (IOException ioe) {
                    // just going to ignore this
                }

            }
        } catch (Exception e) {
            LOGGER.error("doGet(HttpServletRequest, HttpServletResponse)", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    static String getFilename(List<Patient> patients) {
        StringBuilder buffer = new StringBuilder();

        if (patients.size() > 0) {
            for (Patient patient : patients) {
                buffer.append(StringUtil.hasText(patient.getPatientName()) ? patient.getPatientName() : patient
                    .getPatientID());
                buffer.append(",");
            }
            buffer.deleteCharAt(buffer.length() - 1);
        }
        return StringUtil.getTruncatedString(buffer.toString(), 30, Suffix.NO);
    }

    static List<Patient> getPatientList(HttpServletRequest request, DicomQueryParams params) {
        String[] pat = request.getParameterValues(PatientID);
        String[] stu = request.getParameterValues(StudyUID);
        String[] anb = request.getParameterValues(AccessionNumber);
        String[] ser = request.getParameterValues(SeriesUID);
        String[] obj = request.getParameterValues(ObjectUID);

        try {
            String key = WeasisLauncher.pacsProperties.getProperty("encrypt.key", null);

            if (obj != null && obj.length > 0 && isRequestIDAllowed(ObjectUID)) {
                for (String id : obj) {
                    BuildManifestDcmQR.buildFromSopInstanceUID(params, decrypt(id, key, ObjectUID));
                }
                if (!isValidateAllIDs(ObjectUID, key, params.getPatients(), pat, stu, anb, ser)) {
                    return null;
                }
            } else if (ser != null && ser.length > 0 && isRequestIDAllowed(SeriesUID)) {
                for (String id : ser) {
                    BuildManifestDcmQR.buildFromSeriesInstanceUID(params, decrypt(id, key, SeriesUID));
                }
                if (!isValidateAllIDs(SeriesUID, key, params.getPatients(), pat, stu, anb, null)) {
                    return null;
                }
            } else if (anb != null && anb.length > 0 && isRequestIDAllowed(AccessionNumber)) {
                for (String id : anb) {
                    BuildManifestDcmQR.buildFromStudyAccessionNumber(params, decrypt(id, key, AccessionNumber));
                }
                if (!isValidateAllIDs(AccessionNumber, key, params.getPatients(), pat, null, null, null)) {
                    return null;
                }
            } else if (stu != null && stu.length > 0 && isRequestIDAllowed(StudyUID)) {
                for (String id : stu) {
                    BuildManifestDcmQR.buildFromStudyInstanceUID(params, decrypt(id, key, StudyUID));
                }
                if (!isValidateAllIDs(StudyUID, key, params.getPatients(), pat, null, null, null)) {
                    return null;
                }
            } else if (pat != null && pat.length > 0 && isRequestIDAllowed(PatientID)) {
                for (String id : pat) {
                    BuildManifestDcmQR.buildFromPatientID(params, decrypt(id, key, PatientID));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return params.getPatients();
    }

    private static boolean isValidateAllIDs(String id, String key, List<Patient> patientList, String[] pat,
        String[] stu, String[] anb, String[] ser) {
        if (id != null && patientList != null && patientList.size() > 0) {
            String ids = pacsProperties.getProperty("request." + id);
            if (ids != null) {
                for (String val : ids.split(",")) {
                    if (val.trim().equals(PatientID)) {
                        if (pat == null) {
                            return false;
                        }
                        List<String> list = new ArrayList<String>(pat.length);
                        for (String s : pat) {
                            list.add(decrypt(s, key, PatientID));
                        }
                        for (Patient p : patientList) {
                            if (!list.contains(p.getPatientID())) {
                                return false;
                            }
                        }
                    } else if (val.trim().equals(StudyUID)) {
                        if (stu == null) {
                            return false;
                        }
                        List<String> list = new ArrayList<String>(stu.length);
                        for (String s : stu) {
                            list.add(decrypt(s, key, StudyUID));
                        }
                        for (Patient p : patientList) {
                            for (Study study : p.getStudies()) {
                                if (!list.contains(study.getStudyID())) {
                                    return false;
                                }
                            }
                        }
                    } else if (val.trim().equals(AccessionNumber)) {
                        if (anb == null) {
                            return false;
                        }
                        List<String> list = new ArrayList<String>(anb.length);
                        for (String s : anb) {
                            list.add(decrypt(s, key, AccessionNumber));
                        }
                        for (Patient p : patientList) {
                            for (Study study : p.getStudies()) {
                                if (!list.contains(study.getAccessionNumber())) {
                                    return false;
                                }
                            }
                        }
                    } else if (val.trim().equals(SeriesUID)) {
                        if (ser == null) {
                            return false;
                        }
                        List<String> list = new ArrayList<String>(ser.length);
                        for (String s : ser) {
                            list.add(decrypt(s, key, SeriesUID));
                        }
                        for (Patient p : patientList) {
                            for (Study study : p.getStudies()) {
                                for (Series series : study.getSeriesList()) {
                                    if (!list.contains(series.getSeriesInstanceUID())) {
                                        return false;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    static String decrypt(String message, String key, String level) {
        if (key != null) {
            String decrypt = EncryptUtils.decrypt(message, key);
            LOGGER.debug("Decrypt {}: {} to {}", new Object[] { level, message, decrypt });
            return decrypt;
        }
        return message;
    }

    private static boolean isRequestIDAllowed(String id) {
        if (id != null) {
            return Boolean.valueOf(pacsProperties.getProperty(id));
        }
        return false;
    }

    static boolean isValidateAllIDs(String id, List<Patient> patients, HttpServletRequest request) {
        if (id != null && patients != null && patients.size() == 1) {
            Patient patient = patients.get(0);
            String ids = pacsProperties.getProperty("request." + id);
            if (ids != null) {
                for (String val : ids.split(",")) {
                    if (val.trim().equals(PatientID)) {
                        if (!patient.getPatientID().equals(request.getParameter(PatientID))) {
                            return false;
                        }
                    } else if (val.trim().equals(StudyUID)) {
                        for (Study study : patient.getStudies()) {
                            if (!study.getStudyID().equals(request.getParameter(StudyUID))) {
                                return false;
                            }
                        }
                    } else if (val.trim().equals(AccessionNumber)) {
                        for (Study study : patient.getStudies()) {
                            if (!study.getAccessionNumber().equals(request.getParameter(AccessionNumber))) {
                                return false;
                            }
                        }
                    } else if (val.trim().equals(SeriesUID)) {
                        for (Study study : patient.getStudies()) {
                            for (Series series : study.getSeriesList()) {
                                if (!series.getSeriesInstanceUID().equals(request.getParameter(SeriesUID))) {
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }
}
