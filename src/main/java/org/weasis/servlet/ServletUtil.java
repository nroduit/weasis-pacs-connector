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

import static org.weasis.dicom.wado.DicomQueryParams.AccessionNumber;
import static org.weasis.dicom.wado.DicomQueryParams.ObjectUID;
import static org.weasis.dicom.wado.DicomQueryParams.PatientID;
import static org.weasis.dicom.wado.DicomQueryParams.PatientLevel;
import static org.weasis.dicom.wado.DicomQueryParams.SeriesUID;
import static org.weasis.dicom.wado.DicomQueryParams.StudyLevel;
import static org.weasis.dicom.wado.DicomQueryParams.StudyUID;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;

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
import org.weasis.dicom.util.StringUtil.Suffix;
import org.weasis.dicom.wado.BuildManifestDcmQR;
import org.weasis.dicom.wado.DicomQueryParams;
import org.weasis.dicom.wado.WadoParameters;
import org.weasis.dicom.wado.WadoQuery.WadoMessage;
import org.weasis.dicom.wado.thread.ManifestBuilder;
import org.weasis.util.EncryptUtils;

public class ServletUtil {
    private static Logger LOGGER = LoggerFactory.getLogger(ServletUtil.class);

    private ServletUtil() {
    }

    public static String getFirstParameter(Object val) {
        if (val instanceof String[]) {
            String[] params = (String[]) val;
            if (params.length > 0) {
                return params[0];
            }
        } else if (val != null) {
            return val.toString();
        }
        return null;
    }

    public static String[] getParameters(Object val) {
        if (val instanceof String[]) {
            return (String[]) val;
        } else if (val != null) {
            return new String[] { val.toString() };
        }
        return null;
    }

    public static Object addParameter(Object val, String arg) {
        if (val instanceof String[]) {
            String[] array = (String[]) val;
            String[] arr = Arrays.copyOf(array, array.length + 1);
            arr[array.length] = arg;
            return arr;
        } else if (val != null) {
            return new String[] { val.toString(), arg };
        }
        return arg;
    }

    public static int getIntProperty(Properties prop, String key, int def) {
        int result = def;
        final String value = prop.getProperty(key);
        if (value != null) {
            try {
                result = Integer.parseInt(value);
            } catch (NumberFormatException ignore) {
                // return the default value
            }
        }
        return result;
    }

    public static long getLongProperty(Properties prop, String key, long def) {
        long result = def;
        final String value = prop.getProperty(key);
        if (value != null) {
            try {
                result = Long.parseLong(value);
            } catch (NumberFormatException ignore) {
                // return the default value
            }
        }
        return result;
    }

    public static boolean isRequestAllowed(HttpServletRequest request, Properties pacsProperties, Logger logger)
        throws IOException {

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
                return false;
            }
        }
        return true;
    }

    public static void logInfo(HttpServletRequest request, Logger logger) {
        logger.debug("logRequestInfo() - getRequestQueryURL: {}{}", request.getRequestURL().toString(),
            request.getQueryString() != null ? ("?" + request.getQueryString().trim()) : "");
        logger.debug("logRequestInfo() - getContextPath: {}", request.getContextPath());
        logger.debug("logRequestInfo() - getServletPath: {}", request.getServletPath());
    }

    public static String getFilename(List<Patient> patients) {
        StringBuilder buffer = new StringBuilder();

        if (patients.size() > 0) {
            for (Patient patient : patients) {
                buffer.append(
                    StringUtil.hasText(patient.getPatientName()) ? patient.getPatientName() : patient.getPatientID());
                buffer.append(",");
            }
            buffer.deleteCharAt(buffer.length() - 1);
        }
        return StringUtil.getTruncatedString(buffer.toString(), 30, Suffix.NO);
    }

    public static WadoMessage getPatientList(DicomQueryParams params) {
        WadoMessage wadoMessage = null;
        try {
            Properties properties = params.getProperties();
            String key = properties.getProperty("encrypt.key", null);
            String requestType = params.getRequestType();

            if (StudyLevel.equals(requestType) && isRequestIDAllowed(StudyLevel, properties)) {
                String stuID = params.getReqStudyUID();
                String anbID = params.getReqAccessionNumber();
                if (StringUtil.hasText(anbID)) {
                    BuildManifestDcmQR.buildFromStudyAccessionNumber(params,
                        ServletUtil.decrypt(anbID, key, AccessionNumber));
                } else if (StringUtil.hasText(stuID)) {
                    BuildManifestDcmQR.buildFromStudyInstanceUID(params, ServletUtil.decrypt(stuID, key, StudyUID));
                } else {
                    LOGGER.info("Not ID found for STUDY request type: {}", requestType);
                }
            } else if (PatientLevel.equals(requestType) && isRequestIDAllowed(PatientLevel, properties)) {
                String patID = params.getReqPatientID();
                if (StringUtil.hasText(patID)) {
                    BuildManifestDcmQR.buildFromPatientID(params, ServletUtil.decrypt(patID, key, PatientID));
                }
            } else if (requestType != null) {
                LOGGER.info("Not supported IID request type: {}", requestType);
            } else {
                String[] pat = params.getReqPatientIDs();
                String[] stu = params.getReqStudyUIDs();
                String[] anb = params.getReqAccessionNumbers();
                String[] ser = params.getReqSeriesUIDs();
                String[] obj = params.getReqObjectUIDs();
                if (obj != null && obj.length > 0 && isRequestIDAllowed(ObjectUID, properties)) {
                    for (String id : obj) {
                        BuildManifestDcmQR.buildFromSopInstanceUID(params, decrypt(id, key, ObjectUID));
                    }
                    if (!isValidateAllIDs(ObjectUID, key, params, pat, stu, anb, ser)) {
                        params.getPatients().clear();
                        return null;
                    }
                } else if (ser != null && ser.length > 0 && isRequestIDAllowed(SeriesUID, properties)) {
                    for (String id : ser) {
                        BuildManifestDcmQR.buildFromSeriesInstanceUID(params, decrypt(id, key, SeriesUID));
                    }
                    if (!isValidateAllIDs(SeriesUID, key, params, pat, stu, anb, null)) {
                        params.getPatients().clear();
                        return null;
                    }
                } else if (anb != null && anb.length > 0 && isRequestIDAllowed(AccessionNumber, properties)) {
                    for (String id : anb) {
                        BuildManifestDcmQR.buildFromStudyAccessionNumber(params, decrypt(id, key, AccessionNumber));
                    }
                    if (!isValidateAllIDs(AccessionNumber, key, params, pat, null, null, null)) {
                        params.getPatients().clear();
                        return null;
                    }
                } else if (stu != null && stu.length > 0 && isRequestIDAllowed(StudyUID, properties)) {
                    for (String id : stu) {
                        BuildManifestDcmQR.buildFromStudyInstanceUID(params, decrypt(id, key, StudyUID));
                    }
                    if (!isValidateAllIDs(StudyUID, key, params, pat, null, null, null)) {
                        params.getPatients().clear();
                        return null;
                    }
                } else if (pat != null && pat.length > 0 && isRequestIDAllowed(PatientID, properties)) {
                    for (String id : pat) {
                        BuildManifestDcmQR.buildFromPatientID(params, decrypt(id, key, PatientID));
                    }
                }
            }
        } catch (Exception e) {
            StringUtil.logError(LOGGER, e, "Error when building the patient list");
        }

        return wadoMessage;
    }

    private static boolean isValidateAllIDs(String id, String key, DicomQueryParams params, String[] pat, String[] stu,
        String[] anb, String[] ser) {

        List<Patient> patients = params.getPatients();
        Properties properties = params.getProperties();

        if (id != null && patients != null && patients.size() > 0) {
            String ids = properties.getProperty("request." + id);
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
                        for (Patient p : patients) {
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
                        for (Patient p : patients) {
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
                        for (Patient p : patients) {
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
                        for (Patient p : patients) {
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

    private static boolean isRequestIDAllowed(String id, Properties properties) {
        if (id != null) {
            return Boolean.valueOf(properties.getProperty(id));
        }
        return false;
    }

    public static String getBaseURL(HttpServletRequest request, boolean canonicalHostName) {
        if (canonicalHostName) {
            try {
                /**
                 * To get Fully Qualified Domain Name behind bigIP it's better using
                 * InetAddress.getLocalHost().getCanonicalHostName() instead of req.getLocalAddr()<br>
                 * If not resolved from the DNS server FQDM is taken from the /etc/hosts on Unix server
                 */
                return request.getScheme() + "://" + InetAddress.getLocalHost().getCanonicalHostName() + ":"
                    + request.getServerPort();
            } catch (UnknownHostException e) {
                StringUtil.logError(LOGGER, e, "Cannot get hostname");
            }
        }
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    }

    public static DicomQueryParams buildDicomQueryParams(HttpServletRequest request, Properties props) {

        String pacsAET = props.getProperty("pacs.aet", "DCM4CHEE");
        String pacsHost = props.getProperty("pacs.host", "localhost");
        int pacsPort = Integer.parseInt(props.getProperty("pacs.port", "11112"));
        DicomNode calledNode = new DicomNode(pacsAET, pacsHost, pacsPort);

        String wadoQueriesURL = props.getProperty("pacs.wado.url", props.getProperty("server.base.url") + "/wado");
        boolean onlysopuid = StringUtil.getNULLtoFalse(props.getProperty("wado.onlysopuid"));
        String addparams = props.getProperty("wado.addparams", "");
        String overrideTags = props.getProperty("wado.override.tags", null);
        // If the web server requires an authentication (pacs.web.login=user:pwd)
        String webLogin = props.getProperty("pacs.web.login", null);
        if (webLogin != null) {
            webLogin = Base64.encodeBytes(webLogin.trim().getBytes());
        }
        String httpTags = props.getProperty("wado.httpTags", null);

        WadoParameters wado = new WadoParameters(wadoQueriesURL, onlysopuid, addparams, overrideTags, webLogin);
        if (httpTags != null && !httpTags.trim().equals("")) {
            for (String tag : httpTags.split(",")) {
                String[] val = tag.split(":");
                if (val.length == 2) {
                    wado.addHttpTag(val[0].trim(), val[1].trim());
                }
            }
        }

        boolean tls = StringUtil.getNULLtoFalse(props.getProperty("pacs.tls.mode"));
        AdvancedParams params = null;
        if (tls) {
            try {
                TlsOptions tlsOptions =
                    new TlsOptions(StringUtil.getNULLtoFalse(props.getProperty("pacs.tlsNeedClientAuth")),
                        props.getProperty("pacs.keystoreURL"), props.getProperty("pacs.keystoreType", "JKS"),
                        props.getProperty("pacs.keystorePass"),
                        props.getProperty("pacs.keyPass", props.getProperty("pacs.keystorePass")),
                        props.getProperty("pacs.truststoreURL"), props.getProperty("pacs.truststoreType", "JKS"),
                        props.getProperty("pacs.truststorePass"));
                params = new AdvancedParams();
                params.setTlsOptions(tlsOptions);
            } catch (Exception e) {
                StringUtil.logError(LOGGER, e, "Cannot set TLS configuration");
            }

        }

        return new DicomQueryParams(new DicomNode(props.getProperty("aet", "PACS-CONNECTOR")), calledNode, request,
            wado, props.getProperty("pacs.db.encoding", "utf-8"),
            StringUtil.getNULLtoFalse(props.getProperty("accept.noimage")), params, props);

    }

    public static ManifestBuilder buildManifest(HttpServletRequest request, Properties props) throws Exception {
        final DicomQueryParams params = ServletUtil.buildDicomQueryParams(request, props);
        return buildManifest(request, new ManifestBuilder(params));
    }

    public static ManifestBuilder buildManifest(HttpServletRequest request, ManifestBuilder builder) throws Exception {
        ServletContext ctx = request.getSession().getServletContext();
        final ConcurrentHashMap<Integer, ManifestBuilder> builderMap =
            (ConcurrentHashMap<Integer, ManifestBuilder>) ctx.getAttribute("manifestBuilderMap");

        ExecutorService executor = (ExecutorService) ctx.getAttribute("manifestExecutor");
        builder.submit((ExecutorService) ctx.getAttribute("manifestExecutor"));
        builderMap.put(builder.getRequestId(), builder);
        return builder;
    }

    public static String buildManifestURL(HttpServletRequest request, ManifestBuilder builder, Properties props,
        boolean gzip) throws Exception {
        StringBuilder buf = new StringBuilder(props.getProperty("server.base.url"));
        buf.append(request.getContextPath());
        buf.append("/RequestManifest?");
        buf.append(RequestManifest.PARAM_ID);
        buf.append('=');
        buf.append(builder.getRequestId());
        if (!gzip) {
            buf.append('&');
            buf.append(RequestManifest.PARAM_NO_GZIP);
        }

        String wadoQueryUrl = buf.toString();
        LOGGER.debug("wadoQueryUrl = " + wadoQueryUrl);
        return wadoQueryUrl;
    }

    public static void write(InputStream in, OutputStream out) throws IOException {
        try {
            copy(in, out, 2048);
        } catch (Exception e) {
            handleException(e);
        } finally {
            try {
                in.close();
                out.flush();
            } catch (IOException e) {
                // jetty 6 throws broken pipe exception here too
                handleException(e);
            }
        }
    }

    public static int copy(final InputStream in, final OutputStream out, final int bufSize) throws IOException {
        final byte[] buffer = new byte[bufSize];
        int bytesCopied = 0;
        while (true) {
            int byteCount = in.read(buffer, 0, buffer.length);
            if (byteCount <= 0) {
                break;
            }
            out.write(buffer, 0, byteCount);
            bytesCopied += byteCount;
        }
        return bytesCopied;
    }

    private static void handleException(Exception e) {
        Throwable throwable = e;
        boolean ignoreException = false;
        while (throwable != null) {
            if (throwable instanceof SQLException) {
                break; // leave false and quit loop
            } else if (throwable instanceof SocketException) {
                String message = throwable.getMessage();
                ignoreException = message != null
                    && (message.indexOf("Connection reset") != -1 || message.indexOf("Broken pipe") != -1
                        || message.indexOf("Socket closed") != -1 || message.indexOf("connection abort") != -1);
            } else {
                ignoreException = throwable.getClass().getName().indexOf("ClientAbortException") >= 0
                    || throwable.getClass().getName().indexOf("EofException") >= 0;
            }
            if (ignoreException) {
                break;
            }
            throwable = throwable.getCause();
        }
        if (!ignoreException) {
            throw new RuntimeException("Unable to write the response", e);
        }
    }

    public static void write(String str, ServletOutputStream out) {
        try {
            byte[] bytes = str.getBytes();
            out.write(bytes, 0, bytes.length);
        } catch (Exception e) {
            handleException(e);
        } finally {
            try {
                out.flush();
            } catch (IOException e) {
                // jetty 6 throws broken pipe exception here too
                handleException(e);
            }
        }
    }

}
