/*
 * Copyright (c) 2014-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.mf.XmlManifest;
import org.weasis.dicom.mf.thread.ManifestBuilder;
import org.weasis.dicom.mf.thread.ManifestManagerThread;
import org.weasis.util.InetUtil;

/**
 * @author Nicolas Roduit
 */
@WebServlet(
    name = "RequestManifest",
    urlPatterns = {"/RequestManifest"})
public class RequestManifest extends HttpServlet {

  private static final long serialVersionUID = 3012016354418267374L;
  private static final Logger LOGGER = LoggerFactory.getLogger(RequestManifest.class);

  public static final String PARAM_ID = "id";
  public static final String PARAM_NO_GZIP = "noGzip";

  public static final String CONSUME_MANIFEST_DURATION_HEADER = "ConsumeManifestDuration";

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    response.setStatus(HttpServletResponse.SC_ACCEPTED);

    response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1
    response.setHeader("Pragma", "no-cache"); // HTTP 1.0
    response.setDateHeader("Expires", -1); // Proxies

    String wadoXmlId = request.getParameter(PARAM_ID);
    Integer id = null;
    try {
      id = StringUtil.hasText(wadoXmlId) ? Integer.parseInt(wadoXmlId) : null;
    } catch (NumberFormatException e1) {
      // Do nothing
    }

    if (id == null) {
      String errorMsg = "Missing or bad 'id' parameter in request";
      LOGGER.error("{}: {}", errorMsg, wadoXmlId.replaceAll("[\n|\r|\t]", "_"));
      ServletUtil.sendResponseError(response, HttpServletResponse.SC_BAD_REQUEST, errorMsg);
      return;
    }
    LOGGER.debug("doGet [id={}] - START", id);

    ConcurrentHashMap<Integer, ManifestBuilder> threadsMap =
        (ConcurrentHashMap<Integer, ManifestBuilder>)
            getServletContext().getAttribute("manifestBuilderMap");

    if (threadsMap == null) {
      String errorMsg = "Missing 'ManifestBuilderMap' from current ServletContext";
      LOGGER.error(errorMsg);
      ServletUtil.sendResponseError(
          response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMsg);
      return;
    }

    ManifestBuilder builder = threadsMap.get(id);

    if (builder == null) {
      String errorMsg = "No 'ManifestBuilder' found with id=" + id;
      LOGGER.error(errorMsg);
      ServletUtil.sendResponseError(response, HttpServletResponse.SC_NOT_FOUND, errorMsg);
      return;
    }

    XmlManifest xml = null;
    String errorMessage = null;

    try {
      Future<XmlManifest> future = builder.getFuture();
      if (future != null) {
        xml = future.get(ManifestManagerThread.MAX_LIFE_CYCLE, TimeUnit.MILLISECONDS);
      }
    } catch (InterruptedException e) {
      LOGGER.warn("Interrupted Exception of [id={}]", id);
      Thread.currentThread().interrupt();
    } catch (Exception e1) {
      errorMessage = e1.getMessage();
      LOGGER.error("Building Manifest Exception [id={}]", id, e1);
    }

    long consumeManifestDuration = System.currentTimeMillis() - builder.getStartTimeMillis();
    response.setHeader(CONSUME_MANIFEST_DURATION_HEADER, Long.toString(consumeManifestDuration));

    String clientAddr = InetUtil.getClientHostFromRequest(request);
    String callingComponent = request.getHeader("User-Agent");
    LOGGER.info(
        "Consume Manifest [id={}] in {} ms by HOST: {} [User-Agent: {}]",
        id,
        consumeManifestDuration,
        clientAddr,
        callingComponent);

    threadsMap.remove(id);

    if (xml == null) {
      if (errorMessage == null) {
        errorMessage = "Unexpected Exception";
      }
      ServletUtil.sendResponseError(
          response,
          HttpServletResponse.SC_NOT_FOUND,
          "Cannot build Manifest [id=" + id + "] - " + errorMessage);
      return;
    }

    response.setCharacterEncoding(xml.getCharsetEncoding());
    String wadoXmlGenerated =
        xml.xmlManifest(request.getParameter(ConnectorProperties.MANIFEST_VERSION));
    if (wadoXmlGenerated == null) {
      ServletUtil.sendResponseError(
          response,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error when building the xml manifest.");
      return;
    }

    Boolean gzip = request.getParameter(PARAM_NO_GZIP) == null;

    response.setStatus(HttpServletResponse.SC_OK);

    if (gzip) {
      try {
        OutputStream outputStream = response.getOutputStream();
        GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream);
        response.setContentType("application/x-gzip");
        response.setHeader("Content-Disposition", "filename=\"manifest-" + id + ".gz\";");

        gzipStream.write(wadoXmlGenerated.getBytes(xml.getCharsetEncoding()));
        gzipStream.finish();
      } catch (Exception e) {
        String errorMsg = "Exception writing GZIP response [id=" + id + "]";
        LOGGER.error(errorMsg, e);
        ServletUtil.sendResponseError(
            response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMsg);
        return;
      }
    } else {
      try {
        PrintWriter writer = response.getWriter();
        response.setContentType("text/xml");
        response.setHeader("Content-Disposition", "filename=\"manifest-" + id + ".xml\";");

        writer.print(wadoXmlGenerated);
      } catch (Exception e) {
        String errorMsg = "Exception writing noGzip response [id=" + id + "]";
        LOGGER.error(errorMsg, e);
        ServletUtil.sendResponseError(
            response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMsg);
        return;
      }
    }
  }
}
