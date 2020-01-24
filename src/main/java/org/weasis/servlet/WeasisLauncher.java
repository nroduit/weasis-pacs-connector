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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.GzipManager;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.mf.UploadXml;
import org.weasis.dicom.mf.XmlManifest;
import org.weasis.dicom.mf.thread.ManifestBuilder;
import org.weasis.dicom.mf.thread.ManifestManagerThread;
import org.weasis.query.CommonQueryParams;

@WebServlet(name = "WeasisLauncher", urlPatterns = { "/viewer" })
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
        invokeWeasis(request, response, null);
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
            builRequest(request, buf, props, props.getProperty("weasis.default.jnlp"));

            if (!embeddedManifest && StringUtil.hasText(wadoQueryUrl)) {
                buf.append("&");
                buf.append(WeasisConfig.PARAM_ARGUMENT);
                buf.append("=");
                buf.append(URLEncoder.encode("$dicom:get -w \"" + wadoQueryUrl + "\"", "UTF-8"));
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

    static void builRequest(HttpServletRequest request, StringBuilder buf, ConnectorProperties props,
        String jnlpTemplate) {
        String queryCodeBasePath = request.getParameter(WeasisConfig.PARAM_CODEBASE);
        buf.append("?");
        buf.append(WeasisConfig.PARAM_CODEBASE);
        buf.append("=");
        // If weasis codebase is not in the request, set the url from the weasis-pacs-connector properties.
        buf.append(queryCodeBasePath == null
            ? props.getProperty("weasis.base.url", props.getProperty("server.base.url") + "/weasis")
            : queryCodeBasePath);

        String cdbExtParam = request.getParameter(WeasisConfig.PARAM_CODEBASE_EXT);
        if (cdbExtParam == null) {
            // If not in URL parameter, try to get from the config.
            String cdbExt = props.getProperty("weasis.ext.url", null);
            if (cdbExt != null) {
                buf.append("&");
                buf.append(WeasisConfig.PARAM_CODEBASE_EXT);
                buf.append("=");
                buf.append(cdbExt);
            }
        }
        // Overrides template path
        String queryLauncherPath = request.getParameter(JnlpLauncher.PARAM_SOURCE);
        buf.append("&");
        buf.append(JnlpLauncher.PARAM_SOURCE);
        buf.append("=");
        if (queryLauncherPath != null) {
            if (queryLauncherPath.indexOf('/') == -1 || queryLauncherPath.startsWith("/")) {
                String path = queryLauncherPath.startsWith("/") ? queryLauncherPath : "/" + queryLauncherPath;
                URL url = request.getClass().getResource(path);
                if (url == null) {
                    try {
                        url = request.getSession().getServletContext().getResource(path);
                    } catch (MalformedURLException e) {
                        LOGGER.error("Error on getting template", e);
                    }
                }
                buf.append(url == null ? queryLauncherPath : url.toString());
            } else {
                buf.append(queryLauncherPath); // URI
            }
        } else {
            buf.append(jnlpTemplate);
        }
    }

    static String buildManifest(HttpServletRequest request, XmlManifest manifest)
        throws InterruptedException, ExecutionException, TimeoutException, IOException {
        if (manifest != null || CommonQueryParams.isManifestRequest(request.getParameterMap())) {
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

            // BUILDER IS NULL WHEN NO ALLOWED PARAMETER ARE GIVEN WHICH LEADS TO NO MANIFEST BUILT

            if (builder != null) {
                if (embeddedManifest) {
                    Future<XmlManifest> future = builder.getFuture();
                    XmlManifest xml = future.get(ManifestManagerThread.MAX_LIFE_CYCLE, TimeUnit.MILLISECONDS);
                    StringBuilder buf = new StringBuilder("$dicom:get -i ");
                    buf.append(Base64.getEncoder().encode(GzipManager
                        .gzipCompressToByte(xml.xmlManifest((String) props.get("manifest.version")).getBytes())));

                    request.setAttribute(JnlpLauncher.ATTRIBUTE_UPLOADED_ARGUMENT, buf.toString());
                    // Remove the builder as it has been retrieved without calling RequestManifest servlet
                    final ConcurrentHashMap<Integer, ManifestBuilder> builderMap =
                        (ConcurrentHashMap<Integer, ManifestBuilder>) ctx.getAttribute("manifestBuilderMap");
                    builderMap.remove(builder.getRequestId());
                    LOGGER.info("Embedding a ManifestBuilder with key={}", builder.getRequestId());
                } else {
                    return ServletUtil.buildManifestURL(request, builder, props, true);
                }
            }
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
                    LOGGER.error("Invalid manifest: {}", buf);
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
