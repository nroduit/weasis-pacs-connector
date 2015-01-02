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
import java.net.URLEncoder;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.data.xml.Base64;
import org.weasis.dicom.util.StringUtil;
import org.weasis.dicom.wado.UploadXml;
import org.weasis.dicom.wado.XmlManifest;
import org.weasis.dicom.wado.thread.ManifestBuilder;
import org.weasis.dicom.wado.thread.ManifestManagerThread;

public class WeasisAppletLauncher extends HttpServlet {

    private static final long serialVersionUID = -9044908938729820195L;
    private static final Logger LOGGER = LoggerFactory.getLogger(WeasisAppletLauncher.class);

    public WeasisAppletLauncher() {
        super();
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            RequestDispatcher dispatcher = getServletContext().getRequestDispatcher("/jnlpBuilder");
            dispatcher.forward(request, response);
        } catch (Exception e) {
            StringUtil.logError(LOGGER, e, "jnlpBuilder request");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UploadXml manifest = null;

        String uploadParam = request.getParameter(SLwebstart_launcher.PARAM_UPLOAD);
        // Start reading XML manifest
        if ("manifest".equals(uploadParam)) {
            StringBuilder buf = new StringBuilder();
            String line = null;
            BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                buf.append(line);
            }
            if (buf.length() > 10) {
                manifest = new UploadXml(buf.toString(), request.getCharacterEncoding());
            }
        }

        invokeWeasis(request, response, manifest);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        invokeWeasis(request, response,  null);
    }



    protected static void invokeWeasis(HttpServletRequest request, HttpServletResponse response,
        XmlManifest manifest) throws IOException {

        try {
            if (LOGGER.isDebugEnabled()) {
                ServletUtil.logInfo(request, LOGGER);
            }
            
            Properties props = WeasisLauncher.initialize(request);
            
            ServletContext ctx = request.getSession().getServletContext();
            boolean embeddedManifest = request.getParameterMap().containsKey(WeasisLauncher.PARAM_EMBED);
            String wadoQueryUrl = "";

            try {
                ManifestBuilder builder;
                if (manifest == null) {
                    builder = ServletUtil.buildManifest(request, props);
                } else {
                    builder = ServletUtil.buildManifest(request, new ManifestBuilder(manifest));
                }
                if (embeddedManifest) {
                    Future<XmlManifest> future = builder.getFuture();
                    XmlManifest xml = future.get(ManifestManagerThread.MAX_LIFE_CYCLE, TimeUnit.MILLISECONDS);

                    request.setAttribute(SLwebstart_launcher.ATTRIBUTE_UPLOADED_ARGUMENT,
                        "$dicom:get -i " + Base64.encodeBytes(xml.xmlManifest().getBytes(), Base64.GZIP));
                    // Remove the builder as it has been retrieved without calling RequestManifest servlet
                    final ConcurrentHashMap<Integer, ManifestBuilder> builderMap =
                        (ConcurrentHashMap<Integer, ManifestBuilder>) ctx.getAttribute("manifestBuilderMap");
                    builderMap.remove(builder.getRequestId());
                } else {
                    wadoQueryUrl = ServletUtil.buildManifestURL(request, builder, props, true);
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage());
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            String serverPath = props.getProperty("server.base.url");

            StringBuilder buf = new StringBuilder(serverPath);
            buf.append(request.getContextPath());
            // TODO servlet parameter
            buf.append("/weasisApplet.jnlp");
            
            String queryCodeBasePath = request.getParameter(SLwebstart_launcher.PARAM_CODEBASE);
            buf.append("?");
            buf.append(SLwebstart_launcher.PARAM_CODEBASE);
            buf.append("=");
            // If weasis codebase is not in the request, set the url from the weasis-pacs-connector properties.
            buf.append(queryCodeBasePath == null ? props.getProperty("weasis.base.url", props.getProperty("server.base.url") + "/weasis")
                : queryCodeBasePath);
            
            String jnlpScr = props.getProperty("weasis.default.jnlp", null);
            if(jnlpScr != null){
                buf.append("&");
                buf.append(SLwebstart_launcher.PARAM_SOURCE);
                buf.append("=");
                buf.append(jnlpScr);
            }
            
            // TODO should transmit codebase ext, props and args

            /*
             * Issue when setting directly in the jnlp building url into jnlp_href. It seems some characters must be
             * escaped.
             */
            // buf.append("&#38;");
            // buf.append(SLwebstart_launcher.PARAM_ARGUMENT);
            // buf.append("=commands=$dicom:get -w ");
            // buf.append(wadoQueryUrl);

            String manifestCmd = embeddedManifest ? "" : "&commands=$dicom:get -w " + wadoQueryUrl;
            
            RequestDispatcher dispatcher =
                request.getRequestDispatcher("/applet.jsp?jnlp=" + URLEncoder.encode(buf.toString(), "UTF-8")
                    + manifestCmd);
            dispatcher.forward(request, response);

        } catch (Exception e) {
            String msg = e.getMessage();
            if (StringUtil.hasText(msg) && msg.startsWith("Unautorized")) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            } else {
                LOGGER.error("doGet)", e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }
}
