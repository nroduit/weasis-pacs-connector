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

import static org.weasis.query.CommonQueryParams.AccessionNumber;
import static org.weasis.query.CommonQueryParams.ObjectUID;
import static org.weasis.query.CommonQueryParams.PatientID;
import static org.weasis.query.CommonQueryParams.PatientLevel;
import static org.weasis.query.CommonQueryParams.SeriesUID;
import static org.weasis.query.CommonQueryParams.StudyLevel;
import static org.weasis.query.CommonQueryParams.StudyUID;

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
import org.weasis.dicom.util.StringUtil;
import org.weasis.dicom.util.StringUtil.Suffix;
import org.weasis.dicom.wado.WadoQuery.WadoMessage;
import org.weasis.dicom.wado.thread.ManifestBuilder;
import org.weasis.query.AbstractQueryConfiguration;
import org.weasis.query.CommonQueryParams;
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

    public static void fillPatientList(CommonQueryParams params) {
        try {
            Properties properties = params.getProperties();
            String key = properties.getProperty("encrypt.key", null);
            String requestType = params.getRequestType();

            if (StudyLevel.equals(requestType) && isRequestIDAllowed(StudyLevel, properties)) {
                String stuID = params.getReqStudyUID();
                String anbID = params.getReqAccessionNumber();
                if (StringUtil.hasText(anbID)) {
                    String val = ServletUtil.decrypt(anbID, key, AccessionNumber);
                    for (AbstractQueryConfiguration query : params.getPacsList()) {
                        query.buildFromStudyAccessionNumber(params, val);
                    }
                } else if (StringUtil.hasText(stuID)) {
                    String val = ServletUtil.decrypt(stuID, key, StudyUID);
                    for (AbstractQueryConfiguration query : params.getPacsList()) {
                        query.buildFromStudyInstanceUID(params, val);
                    }
                } else {
                    LOGGER.error("Not ID found for STUDY request type: {}", requestType);
                    params.addGeneralWadoMessage(
                        new WadoMessage("Missing Study ID", "No study requested", WadoMessage.eLevel.WARN));
                }
            } else if (PatientLevel.equals(requestType) && isRequestIDAllowed(PatientLevel, properties)) {
                String patID = params.getReqPatientID();
                if (StringUtil.hasText(patID)) {
                    String val = ServletUtil.decrypt(patID, key, PatientID);
                    for (AbstractQueryConfiguration query : params.getPacsList()) {
                        query.buildFromPatientID(params, val);
                    }
                }
            } else if (requestType != null) {
                LOGGER.error("Not supported IID request type: {}", requestType);
                params.addGeneralWadoMessage(
                    new WadoMessage("Unexpected Request", "IID request type: " + requestType, WadoMessage.eLevel.WARN));
            } else {
                String[] pat = params.getReqPatientIDs();
                String[] stu = params.getReqStudyUIDs();
                String[] anb = params.getReqAccessionNumbers();
                String[] ser = params.getReqSeriesUIDs();
                String[] obj = params.getReqObjectUIDs();
                if (obj != null && obj.length > 0 && isRequestIDAllowed(ObjectUID, properties)) {
                    String[] val = decrypt(obj, key, ObjectUID);
                    for (AbstractQueryConfiguration query : params.getPacsList()) {
                        query.buildFromSopInstanceUID(params, val);
                    }
                    validateRequiredIDs(ObjectUID, key, params, pat, stu, anb, ser);
                } else if (ser != null && ser.length > 0 && isRequestIDAllowed(SeriesUID, properties)) {
                    String[] val = decrypt(ser, key, SeriesUID);
                    for (AbstractQueryConfiguration query : params.getPacsList()) {
                        query.buildFromSeriesInstanceUID(params, val);
                    }
                    validateRequiredIDs(SeriesUID, key, params, pat, stu, anb, null);
                } else if (anb != null && anb.length > 0 && isRequestIDAllowed(AccessionNumber, properties)) {
                    String[] val = decrypt(anb, key, AccessionNumber);
                    for (AbstractQueryConfiguration query : params.getPacsList()) {
                        query.buildFromStudyAccessionNumber(params, val);
                    }
                    validateRequiredIDs(AccessionNumber, key, params, pat, null, null, null);
                } else if (stu != null && stu.length > 0 && isRequestIDAllowed(StudyUID, properties)) {
                    String[] val = decrypt(stu, key, StudyUID);
                    for (AbstractQueryConfiguration query : params.getPacsList()) {
                        query.buildFromStudyInstanceUID(params, val);
                    }
                    validateRequiredIDs(StudyUID, key, params, pat, null, null, null);
                } else if (pat != null && pat.length > 0 && isRequestIDAllowed(PatientID, properties)) {
                    String[] val = decrypt(pat, key, PatientID);
                    for (AbstractQueryConfiguration query : params.getPacsList()) {
                        query.buildFromPatientID(params, val);
                    }
                }
            }
        } catch (Exception e) {
            StringUtil.logError(LOGGER, e, "Error when building the patient list");
            params.addGeneralWadoMessage(new WadoMessage("Unexpected Error",
                "Unexpected Error when building the manifest", WadoMessage.eLevel.WARN));
        }
    }

    private static void validateRequiredIDs(String id, String key, CommonQueryParams params, String[] pat, String[] stu,
        String[] anb, String[] ser) {

        if (id != null) {
            String ids = params.getProperties().getProperty("request." + id);
            if (ids != null) {
                for (String val : ids.split(",")) {
                    if (val.trim().equals(PatientID)) {
                        if (pat == null) {
                            params.clearAllPatients();
                            return;
                        }
                        List<String> list = new ArrayList<String>(pat.length);
                        for (String s : pat) {
                            list.add(decrypt(s, key, PatientID));
                        }
                        params.removePatientId(list);
                    } else if (val.trim().equals(StudyUID)) {
                        if (stu == null) {
                            params.clearAllPatients();
                            return;
                        }
                        List<String> list = new ArrayList<String>(stu.length);
                        for (String s : stu) {
                            list.add(decrypt(s, key, StudyUID));
                        }
                        params.removeStudyUid(list);
                    } else if (val.trim().equals(AccessionNumber)) {
                        if (anb == null) {
                            params.clearAllPatients();
                            return;
                        }
                        List<String> list = new ArrayList<String>(anb.length);
                        for (String s : anb) {
                            list.add(decrypt(s, key, AccessionNumber));
                        }
                        params.removeAccessionNumber(list);
                    } else if (val.trim().equals(SeriesUID)) {
                        if (ser == null) {
                            params.clearAllPatients();
                            return;
                        }
                        List<String> list = new ArrayList<String>(ser.length);
                        for (String s : ser) {
                            list.add(decrypt(s, key, SeriesUID));
                        }
                        params.removeSeriesUid(list);
                    }
                }

                // Remove Patient without study
                for (AbstractQueryConfiguration pacsConfiguration : params.getPacsList()) {
                    List<Patient> patients = pacsConfiguration.getPatients();
                    for (int i = patients.size() - 1; i >= 0; i--) {
                        if (patients.get(i).isEmpty()) {
                            patients.remove(i);
                        }
                    }
                }
            }
        }
    }

    static String decrypt(String message, String key, String level) {
        if (key != null) {
            String decrypt = EncryptUtils.decrypt(message, key);
            LOGGER.debug("Decrypt {}: {} to {}", new Object[] { level, message, decrypt });
            return decrypt;
        }
        return message;
    }

    static String[] decrypt(String[] message, String key, String level) {
        if (key != null) {
            String[] decrypt = new String[message.length];
            for (int i = 0; i < decrypt.length; i++) {
                decrypt[i] = EncryptUtils.decrypt(message[i], key);
                LOGGER.debug("Decrypt {}: {} to {}", new Object[] { level, message[i], decrypt[i] });
            }
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

    public static ManifestBuilder buildManifest(HttpServletRequest request, ConnectorProperties props)
        throws Exception {
        return buildManifest(request, new ManifestBuilder(new CommonQueryParams(request, props)));
    }

    public static ManifestBuilder buildManifest(HttpServletRequest request, ManifestBuilder builder) throws Exception {
        ServletContext ctx = request.getSession().getServletContext();
        final ConcurrentHashMap<Integer, ManifestBuilder> builderMap =
            (ConcurrentHashMap<Integer, ManifestBuilder>) ctx.getAttribute("manifestBuilderMap");

        builder.submit((ExecutorService) ctx.getAttribute("manifestExecutor"));
        builderMap.put(builder.getRequestId(), builder);
        return builder;
    }

    public static String buildManifestURL(HttpServletRequest request, ManifestBuilder builder, Properties props,
        boolean gzip) throws Exception {
        StringBuilder buf =
            new StringBuilder(props.getProperty("manifest.base.url", props.getProperty("server.base.url")));
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
