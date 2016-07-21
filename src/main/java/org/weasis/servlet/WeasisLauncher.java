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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.data.xml.Base64;
import org.weasis.dicom.mf.UploadXml;
import org.weasis.dicom.mf.XmlManifest;
import org.weasis.dicom.mf.thread.ManifestBuilder;
import org.weasis.dicom.mf.thread.ManifestManagerThread;
import org.weasis.dicom.util.StringUtil;

public class WeasisLauncher extends HttpServlet {

    private static final long serialVersionUID = 7933047406409849509L;
    private static final Logger LOGGER = LoggerFactory.getLogger(WeasisLauncher.class);

    protected static final String PARAM_EMBED = "embedManifest";

    public WeasisLauncher() {
        super();
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        try {
            RequestDispatcher dispatcher = getServletContext().getRequestDispatcher("/jnlpBuilder");
            dispatcher.forward(request, response);
        } catch (Exception e) {
            LOGGER.error("JNLP dispatcher error", e);
            ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        UploadXml manifest = uploadManifest(request, response);
        if (manifest != null && "INVALID".equals(manifest.xmlManifest(null))) {
            return;
        }

        invokeWeasis(request, response, manifest);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        invokeWeasis(request, response, null);
    }

    private static void invokeWeasis(HttpServletRequest request, HttpServletResponse response, XmlManifest manifest) {

        try {
            if (LOGGER.isDebugEnabled()) {
                ServletUtil.logInfo(request, LOGGER);
            }

            ServletContext ctx = request.getSession().getServletContext();
            ConnectorProperties connectorProperties = (ConnectorProperties) ctx.getAttribute("componentProperties");
            // Check if the source of this request is allowed
            if (!ServletUtil.isRequestAllowed(request, connectorProperties, LOGGER)) {
                return;
            }

            ConnectorProperties props = connectorProperties.getResolveConnectorProperties(request);

            boolean embeddedManifest = request.getParameterMap().containsKey(PARAM_EMBED);
            String wadoQueryUrl = buildManifest(request, manifest);

            StringBuilder buf = new StringBuilder("/");

            builRequest(request, buf, props);

            if (!embeddedManifest) {
                buf.append("&");
                buf.append(JnlpLauncher.PARAM_ARGUMENT);
                buf.append("=");
                buf.append("$dicom:get -w ");
                buf.append(wadoQueryUrl);
            }

            String addparams = props.getProperty("request.addparams", null);
            if (addparams != null) {
                buf.append(addparams);
            }

            RequestDispatcher dispatcher = request.getRequestDispatcher(buf.toString());
            dispatcher.forward(request, response);

        } catch (Exception e) {
            LOGGER.error("Weasis Servlet Launcher", e);
            String msg = e.getMessage();
            if (StringUtil.hasText(msg) && msg.startsWith("Unautorized")) {
                ServletUtil.sendResponseError(response, HttpServletResponse.SC_FORBIDDEN, msg);
            } else {
                ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg);
            }
        }
    }

    static void builRequest(HttpServletRequest request, StringBuilder buf, ConnectorProperties props) {
        String queryCodeBasePath = request.getParameter(JnlpLauncher.PARAM_CODEBASE);
        buf.append("?");
        buf.append(JnlpLauncher.PARAM_CODEBASE);
        buf.append("=");
        // If weasis codebase is not in the request, set the url from the weasis-pacs-connector properties.
        buf.append(queryCodeBasePath == null
            ? props.getProperty("weasis.base.url", props.getProperty("server.base.url") + "/weasis")
            : queryCodeBasePath);

        String cdbExtParam = request.getParameter(JnlpLauncher.PARAM_CODEBASE_EXT);
        if (cdbExtParam == null) {
            // If not in URL parameter, try to get from the config.
            String cdbExt = props.getProperty("weasis.ext.url", null);
            if (cdbExt != null) {
                buf.append("&");
                buf.append(JnlpLauncher.PARAM_CODEBASE_EXT);
                buf.append("=");
                buf.append(cdbExt);
            }
        }

        String jnlpScr = props.getProperty("weasis.default.jnlp", null);
        if (jnlpScr != null) {
            buf.append("&");
            buf.append(JnlpLauncher.PARAM_SOURCE);
            buf.append("=");
            buf.append(jnlpScr);
        }
    }

    static String buildManifest(HttpServletRequest request, XmlManifest manifest)
        throws InterruptedException, ExecutionException, TimeoutException, IOException {
        ServletContext ctx = request.getSession().getServletContext();
        ConnectorProperties connectorProperties = (ConnectorProperties) ctx.getAttribute("componentProperties");
        ConnectorProperties props = connectorProperties.getResolveConnectorProperties(request);

        boolean embeddedManifest = request.getParameterMap().containsKey(PARAM_EMBED);

        ManifestBuilder builder;
        if (manifest == null) {
            builder = ServletUtil.buildManifest(request, props);
        } else {
            builder = ServletUtil.buildManifest(request, new ManifestBuilder(manifest));
        }
        if (embeddedManifest) {
            Future<XmlManifest> future = builder.getFuture();
            XmlManifest xml = future.get(ManifestManagerThread.MAX_LIFE_CYCLE, TimeUnit.MILLISECONDS);

            request.setAttribute(JnlpLauncher.ATTRIBUTE_UPLOADED_ARGUMENT, "$dicom:get -i " + Base64.encodeBytes(
                xml.xmlManifest((String) connectorProperties.get("manifest.version")).getBytes(), Base64.GZIP));
            // Remove the builder as it has been retrieved without calling RequestManifest servlet
            final ConcurrentHashMap<Integer, ManifestBuilder> builderMap =
                (ConcurrentHashMap<Integer, ManifestBuilder>) ctx.getAttribute("manifestBuilderMap");
            builderMap.remove(builder.getRequestId());
            LOGGER.info("Embedding a ManifestBuilder with key={}", builder.getRequestId());
        } else {
            return ServletUtil.buildManifestURL(request, builder, props, true);
        }
        return "";
    }

    static UploadXml uploadManifest(HttpServletRequest request, HttpServletResponse response) {

        String uploadParam = request.getParameter(JnlpLauncher.PARAM_UPLOAD);
        try {
            // Start reading XML manifest
            if ("manifest".equals(uploadParam)) {
                StringBuilder buf = new StringBuilder();
                String line;
                BufferedReader reader = request.getReader();
                while ((line = reader.readLine()) != null) {
                    buf.append(line);
                }
                if (buf.length() > 10) {
                    return new UploadXml(buf.toString(), request.getCharacterEncoding());
                } else {
                    LOGGER.error("Invalid manifest: {}", buf.toString());
                    ServletUtil.sendResponseError(response, HttpServletResponse.SC_NO_CONTENT,
                        "Invalid manifest: " + buf.toString());
                }
            } else {
                // No manifest, threat as doGet()
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Weasis Servlet Launcher", e);
            ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
        return new UploadXml("INVALID", request.getCharacterEncoding());
    }
}
