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

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.BuildManifestDcmQR;
import org.weasis.dicom.DicomNode;
import org.weasis.launcher.wado.Patient;
import org.weasis.launcher.wado.Series;
import org.weasis.launcher.wado.Study;
import org.weasis.launcher.wado.WadoParameters;
import org.weasis.launcher.wado.WadoQuery;
import org.weasis.launcher.wado.WadoQueryException;
import org.weasis.launcher.wado.xml.Base64;
import org.weasis.launcher.wado.xml.TagUtil;

public class BuildManifest extends HttpServlet {
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(BuildManifest.class);

    static final String PatientID = "patientID";
    static final String StudyUID = "studyUID";
    static final String AccessionNumber = "accessionNumber";
    static final String SeriesUID = "seriesUID";
    static final String ObjectUID = "objectUID";

    /**
     * Constructor of the object.
     */
    public BuildManifest() {
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
        Properties pacsProperties = WeasisLauncher.pacsProperties;
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

            String baseURL = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
            System.setProperty("server.base.url", baseURL);

            // Perform variable substitution for system properties.
            for (Enumeration e = pacsProperties.propertyNames(); e.hasMoreElements();) {
                String name = (String) e.nextElement();
                pacsProperties.setProperty(name,
                    TagUtil.substVars(pacsProperties.getProperty(name), name, null, pacsProperties));
            }

            String wadoQueriesURL = pacsProperties.getProperty("pacs.wado.url", "http://localhost:8080/wado");
            String pacsAET = pacsProperties.getProperty("pacs.aet", "DCM4CHEE");
            String pacsHost = pacsProperties.getProperty("pacs.host", "localhost");
            int pacsPort = Integer.parseInt(pacsProperties.getProperty("pacs.port", "11112"));
            DicomNode dicomSource = new DicomNode(pacsAET, pacsHost, pacsPort);
            String componentAET = pacsProperties.getProperty("aet", "WEASIS");
            List<Patient> patients = getPatientList(request, dicomSource, componentAET);

            if (patients == null || patients.size() < 1) {
                logger.warn("No data has been found!");
                response.sendError(HttpServletResponse.SC_NO_CONTENT, "No data has been found!");
                return;
            }

            // If the web server requires an authentication (pacs.web.login=user:pwd)
            String webLogin = pacsProperties.getProperty("pacs.web.login", null);
            if (webLogin != null) {
                webLogin = Base64.encodeBytes(webLogin.trim().getBytes());
            }
            boolean onlysopuid = Boolean.valueOf(pacsProperties.getProperty("wado.onlysopuid"));
            String addparams = pacsProperties.getProperty("wado.addparams", "");
            String overrideTags = pacsProperties.getProperty("wado.override.tags", null);
            String httpTags = pacsProperties.getProperty("wado.httpTags", null);

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
                new WadoQuery(patients, wado, pacsProperties.getProperty("pacs.db.encoding", "utf-8"));

            if (request.getParameter("gzip") != null) {
                response.setContentType("application/x-gzip");
                Closeable stream = null;
                GZIPOutputStream gz = null;
                try {
                    stream = response.getOutputStream();
                    gz = new GZIPOutputStream((OutputStream) stream);
                    gz.write(wadoQuery.toString().getBytes());
                } finally {
                    if (gz != null) {
                        gz.close();
                    }
                    if (stream != null) {
                        stream.close();
                    }
                }
            } else {
                response.setContentType("text/xml");
                PrintWriter outWriter = response.getWriter();
                outWriter.print(wadoQuery.toString());
                outWriter.close();
            }

        } catch (Exception e) {
            logger.error("doGet(HttpServletRequest, HttpServletResponse)", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (WadoQueryException e) {
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
            response.setContentType("text/xml");

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
            if (obj != null && isRequestIDAllowed(ObjectUID)) {
                patients = BuildManifestDcmQR.buildFromSopInstanceUID(dicomSource, componentAET, obj);
                if (!isValidateAllIDs(ObjectUID, patients, request)) {
                    return null;
                }
            } else if (ser != null && isRequestIDAllowed(SeriesUID)) {
                patients = BuildManifestDcmQR.buildFromSeriesInstanceUID(dicomSource, componentAET, ser);
                if (!isValidateAllIDs(SeriesUID, patients, request)) {
                    return null;
                }
            } else if (anb != null && isRequestIDAllowed(AccessionNumber)) {
                patients = BuildManifestDcmQR.buildFromStudyAccessionNumber(dicomSource, componentAET, anb);
                if (!isValidateAllIDs(AccessionNumber, patients, request)) {
                    return null;
                }
            } else if (stu != null && isRequestIDAllowed(StudyUID)) {
                patients = BuildManifestDcmQR.buildFromStudyInstanceUID(dicomSource, componentAET, stu);
                if (!isValidateAllIDs(StudyUID, patients, request)) {
                    return null;
                }
            } else if (pat != null && isRequestIDAllowed(PatientID)) {
                patients = BuildManifestDcmQR.buildFromPatientID(dicomSource, componentAET, pat);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return patients;
    }

    private boolean isRequestIDAllowed(String id) {
        if (id != null) {
            return Boolean.valueOf(WeasisLauncher.pacsProperties.getProperty(id));
        }
        return false;
    }

    private boolean isValidateAllIDs(String id, List<Patient> patients, HttpServletRequest request) {
        if (id != null && patients != null && patients.size() == 1) {
            Patient patient = patients.get(0);
            String ids = WeasisLauncher.pacsProperties.getProperty("request." + id);
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
