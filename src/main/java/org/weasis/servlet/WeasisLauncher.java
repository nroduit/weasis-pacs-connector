/*******************************************************************************
 * Copyright (c) 2010 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/

package org.weasis.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.BuildManifestDcmQR;
import org.weasis.dicom.DicomNode;
import org.weasis.dicom.util.EncryptUtils;
import org.weasis.launcher.wado.Patient;
import org.weasis.launcher.wado.Series;
import org.weasis.launcher.wado.Study;
import org.weasis.launcher.wado.WadoParameters;
import org.weasis.launcher.wado.WadoQuery;
import org.weasis.launcher.wado.WadoQueryException;
import org.weasis.launcher.wado.xml.Base64;
import org.weasis.launcher.wado.xml.TagUtil;

public class WeasisLauncher extends HttpServlet {
    private static final long serialVersionUID = 8946852726380985736L;
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(WeasisLauncher.class);

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
        logger.debug("init() - getServletContext : {} ", getServletConfig().getServletContext());
        logger.debug("init() - getRealPath : {}", getServletConfig().getServletContext().getRealPath("/"));
        try {
            URL config = this.getClass().getResource("/weasis-pacs-connector.properties");
            if (config == null) {
                config = this.getClass().getResource("/weasis-connector-default.properties");
                logger.info("Default configuration file : {}", config);
            } else {
                logger.info("External configuration file : {}", config);
            }
            if (config != null) {
                pacsProperties.load(config.openStream());
                String requests = pacsProperties.getProperty("request.ids", null);
                if (requests == null) {
                    logger.error("No request ID is allowed!");
                } else {
                    for (String id : requests.split(",")) {
                        pacsProperties.put(id, "true");
                    }
                }
            } else {
                logger.error("Cannot find  a configuration file for weasis-pacs-connector");
            }
            URL jnlpTemplate = this.getClass().getResource("/weasis-jnlp.xml");
            if (jnlpTemplate == null) {
                jnlpTemplate = this.getClass().getResource("/weasis-jnlp-default.xml");
                logger.info("Default  Weasis template  : {}", jnlpTemplate);
            } else {
                logger.info("External Weasis template : {}", jnlpTemplate);
            }
            if (jnlpTemplate == null) {
                logger.error("Cannot find  JNLP template");
            } else {
                pacsProperties.put("weasis.jnlp", jnlpTemplate.toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
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
                logger.warn("The request from {} is not allowed.", clientHost);
                return;
            }
        }
        try {
            logRequestInfo(request);
            response.setContentType(JNLP_MIME_TYPE);

            String baseURL = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
            System.setProperty("server.base.url", baseURL);

            Properties dynamicProps = (Properties) pacsProperties.clone();
            // Perform variable substitution for system properties.
            for (Enumeration e = pacsProperties.propertyNames(); e.hasMoreElements();) {
                String name = (String) e.nextElement();
                dynamicProps.setProperty(name,
                    TagUtil.substVars(pacsProperties.getProperty(name), name, null, pacsProperties));
            }

            String wadoQueriesURL = dynamicProps.getProperty("pacs.wado.url", "http://localhost:8080/wado");
            String pacsAET = dynamicProps.getProperty("pacs.aet", "DCM4CHEE");
            String pacsHost = dynamicProps.getProperty("pacs.host", "localhost");
            int pacsPort = Integer.parseInt(dynamicProps.getProperty("pacs.port", "11112"));
            DicomNode dicomSource = new DicomNode(pacsAET, pacsHost, pacsPort);
            String componentAET = dynamicProps.getProperty("aet", "WEASIS");
            List<Patient> patients = getPatientList(request, dicomSource, componentAET);

            String wadoQueryFile = "";
            boolean acceptNoImage = Boolean.valueOf(dynamicProps.getProperty("accept.noimage"));

            if ((patients == null || patients.size() < 1) && !acceptNoImage) {
                logger.warn("No data has been found!");
                response.sendError(HttpServletResponse.SC_NO_CONTENT, "No data has been found!");
                return;
            }
            try {
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
                // ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                // WadoQuery.gzipCompress(new ByteArrayInputStream(wadoQuery.toString().getBytes()), outStream);
                wadoQueryFile = Base64.encodeBytes(wadoQuery.toString().getBytes(), Base64.GZIP);

            } catch (WadoQueryException e) {
                logger.error(e.getMessage());
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            InputStream is = null;
            try {
                URL jnlpTemplate = new URL((String) dynamicProps.get("weasis.jnlp"));
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
            logger.error("doGet(HttpServletRequest, HttpServletResponse)", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * The doPost method of the servlet. <br>
     * 
     * This method is called when a form has its tag value method equals to post.
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
        doGet(request, response);
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            response.setContentType(JNLP_MIME_TYPE);

        } catch (Exception e) {
            logger.error("doHead(HttpServletRequest, HttpServletResponse)", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Destruction of the servlet. <br>
     */
    @Override
    public void destroy() {
        super.destroy();
    }

    /**
     * @param request
     */
    protected void logRequestInfo(HttpServletRequest request) {
        logger.debug("logRequestInfo(HttpServletRequest) - getRequestQueryURL : {}{}", request.getRequestURL()
            .toString(), request.getQueryString() != null ? ("?" + request.getQueryString().trim()) : "");
        logger.debug("logRequestInfo(HttpServletRequest) - getContextPath : {}", request.getContextPath());
        logger.debug("logRequestInfo(HttpServletRequest) - getRequestURI : {}", request.getRequestURI());
        logger.debug("logRequestInfo(HttpServletRequest) - getServletPath : {}", request.getServletPath());
    }

    private List<Patient> getPatientList(HttpServletRequest request, DicomNode dicomSource, String componentAET) {
        String pat = request.getParameter(PatientID);
        String stu = request.getParameter(StudyUID);
        String anb = request.getParameter(AccessionNumber);
        String ser = request.getParameter(SeriesUID);
        String obj = request.getParameter(ObjectUID);
        List<Patient> patients = null;
        try {
            String key = pacsProperties.getProperty("encrypt.key", null);

            if (obj != null && isRequestIDAllowed(ObjectUID)) {
                patients =
                    BuildManifestDcmQR.buildFromSopInstanceUID(dicomSource, componentAET, decrypt(obj, key, ObjectUID));
                if (!isValidateAllIDs(ObjectUID, patients, request)) {
                    return null;
                }
            } else if (ser != null && isRequestIDAllowed(SeriesUID)) {
                patients =
                    BuildManifestDcmQR.buildFromSeriesInstanceUID(dicomSource, componentAET,
                        decrypt(ser, key, SeriesUID));
                if (!isValidateAllIDs(SeriesUID, patients, request)) {
                    return null;
                }
            } else if (anb != null && isRequestIDAllowed(AccessionNumber)) {
                patients =
                    BuildManifestDcmQR.buildFromStudyAccessionNumber(dicomSource, componentAET,
                        decrypt(anb, key, AccessionNumber));
                if (!isValidateAllIDs(AccessionNumber, patients, request)) {
                    return null;
                }
            } else if (stu != null && isRequestIDAllowed(StudyUID)) {
                patients =
                    BuildManifestDcmQR
                        .buildFromStudyInstanceUID(dicomSource, componentAET, decrypt(stu, key, StudyUID));
                if (!isValidateAllIDs(StudyUID, patients, request)) {
                    return null;
                }
            } else if (pat != null && isRequestIDAllowed(PatientID)) {
                patients =
                    BuildManifestDcmQR.buildFromPatientID(dicomSource, componentAET, decrypt(pat, key, PatientID));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return patients;
    }

    private String decrypt(String message, String key, String level) {
        if (key != null) {
            String decrypt = EncryptUtils.decrypt(message, key);
            logger.debug("Decrypt {}: {} to {}", new Object[] { level, message, decrypt });
            return decrypt;
        }
        return message;
    }

    private boolean isRequestIDAllowed(String id) {
        if (id != null) {
            return Boolean.valueOf(pacsProperties.getProperty(id));
        }
        return false;
    }

    private boolean isValidateAllIDs(String id, List<Patient> patients, HttpServletRequest request) {
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
