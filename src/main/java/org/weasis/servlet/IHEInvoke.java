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
import static org.weasis.dicom.wado.DicomQueryParams.PatientID;
import static org.weasis.dicom.wado.DicomQueryParams.StudyUID;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.data.xml.TagUtil;
import org.weasis.dicom.util.FileUtil;
import org.weasis.dicom.util.StringUtil;
import org.weasis.dicom.wado.BuildManifestDcmQR;
import org.weasis.dicom.wado.DicomQueryParams;
import org.weasis.dicom.wado.WadoQuery.WadoMessage;
import org.weasis.dicom.wado.thread.ManifestBuilder;

public class IHEInvoke extends HttpServlet {

    private static final long serialVersionUID = -8426435270216254245L;
    private static final Logger LOGGER = LoggerFactory.getLogger(IHEInvoke.class);

    public IHEInvoke() {
        super();
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            response.setContentType(ServletUtil.JNLP_MIME_TYPE);

        } catch (Exception e) {
            StringUtil.logError(LOGGER, e, "doHead()");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doGet(request, response);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Properties pacsProperties = (Properties) this.getServletContext().getAttribute("componentProperties");
        // Check if the source of this request is allowed
        ServletUtil.isRequestAllowed(request, pacsProperties, LOGGER);

        Properties extProps = new Properties();
        extProps.put(
            "server.base.url",
            ServletUtil.getBaseURL(request,
                ServletUtil.getNULLtoFalse(pacsProperties.getProperty("server.canonical.hostname.mode"))));

        Properties dynamicProps = (Properties) pacsProperties.clone();

        // Perform variable substitution for system properties.
        for (Enumeration<?> e = pacsProperties.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            dynamicProps.setProperty(name,
                TagUtil.substVars(pacsProperties.getProperty(name), name, null, pacsProperties, extProps));
        }

        dynamicProps.putAll(extProps);

        invokeWeasis(request, response, dynamicProps);
    }

    public static void invokeWeasis(HttpServletRequest request, HttpServletResponse response, Properties props)
        throws IOException {

        try {
            if (LOGGER.isDebugEnabled()) {
                ServletUtil.logInfo(request, LOGGER);
            }

            response.setCharacterEncoding("UTF-8");
            response.setContentType(ServletUtil.JNLP_MIME_TYPE);

            String wadoQueryUrl = "";

            try {
                ManifestBuilder builder = ServletUtil.buildManifest(request, props);
                wadoQueryUrl = ServletUtil.buildManifestURL(request, builder, props, true);

                response.setHeader("Content-Disposition", "filename=\"weasis.jnlp\";");

            } catch (Exception e) {
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
                    jnlpTemplate = new URL((String) props.get("weasis.jnlp"));
                } else {
                    LOGGER.info("External Weasis template : {}", jnlpTemplate);
                }

                is = jnlpTemplate.openStream();
                BufferedReader dis = new BufferedReader(new InputStreamReader(is));
                // response.setContentLength(launcherStr.length());
                String weasisBaseURL = props.getProperty("weasis.base.url", props.getProperty("server.base.url"));

                PrintWriter outWriter = response.getWriter();
                String s;
                while ((s = dis.readLine()) != null) {
                    if (s.trim().equals("</application-desc>")) {
                        outWriter.print("\t\t<argument>$dicom:get -w ");
                        outWriter.print(wadoQueryUrl);
                        outWriter.println("</argument>");
                        outWriter.println(s);
                    } else {
                        s = s.replace("${weasis.base.url}", weasisBaseURL);
                        outWriter.println(s);
                    }
                }
                outWriter.close();
            } finally {
                FileUtil.safeClose(is);
            }
        } catch (Exception e) {
            LOGGER.error("doGet(HttpServletRequest, HttpServletResponse)", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

}
