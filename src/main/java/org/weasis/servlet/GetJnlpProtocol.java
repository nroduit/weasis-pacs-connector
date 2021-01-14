/*
 * Copyright (c) 2014 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.servlet;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author Nicolas Roduit */

/** @deprecated JNLP protocol has been removed from Java 11, used GetWeasisProtocol instead */
@WebServlet(
    name = "GetJnlpProtocol",
    urlPatterns = {"/getJnlpScheme/*"})
@Deprecated
public class GetJnlpProtocol extends HttpServlet {
  private static final long serialVersionUID = 3831796275365799251L;
  private static final Logger LOGGER = LoggerFactory.getLogger(GetJnlpProtocol.class);

  public GetJnlpProtocol() {
    super();
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    doGet(request, response);
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    try {

      URI buildJnlpURI = URI.create(request.getRequestURL().toString());
      String buildJnlpUriScheme = buildJnlpURI.getScheme();

      if (buildJnlpUriScheme.equalsIgnoreCase("HTTP")) {
        buildJnlpUriScheme = "jnlp";
      } else if (buildJnlpUriScheme.equalsIgnoreCase("HTTPS")) {
        buildJnlpUriScheme = "jnlps";
      } else {
        throw new IllegalStateException("Cannot not convert to jnlp no http or https request");
      }

      String uriPath = buildJnlpURI.getPath().replaceFirst("/getJnlpScheme", "");

      String queryParams = request.getQueryString();

      if (queryParams != null) {
        String requestEncoding = request.getCharacterEncoding();
        if (requestEncoding == null)
          requestEncoding = StandardCharsets.UTF_8.defaultCharset().name();
        // FIX double encoding with new URI created below
        queryParams = URLDecoder.decode(request.getQueryString(), requestEncoding);
      }

      String buildJnlpUrl =
          new URI(
                  buildJnlpUriScheme,
                  buildJnlpURI.getUserInfo(),
                  buildJnlpURI.getHost(),
                  buildJnlpURI.getPort(),
                  uriPath,
                  queryParams,
                  buildJnlpURI.getFragment())
              .toString();

      response.sendRedirect(buildJnlpUrl); // NOSONAR redirect to jnlp protocol

    } catch (Exception e) {
      LOGGER.error("Redirect to jnlp secheme", e);
      ServletUtil.sendResponseError(
          response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }
}
