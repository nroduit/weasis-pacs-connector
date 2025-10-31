/*
 * Copyright (c) 2011-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.servlet;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serial;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.mf.UploadXml;
import org.weasis.dicom.mf.XmlManifest;
import org.weasis.dicom.mf.thread.ManifestBuilder;
import org.weasis.query.CommonQueryParams;

/**
 * @author Nicolas Roduit
 */
@WebServlet(
    name = "GetWeasisProtocol",
    urlPatterns = {"/weasis", "/IHEInvokeImageDisplay"})
public class GetWeasisProtocol extends HttpServlet {

  @Serial private static final long serialVersionUID = 2987582758040784229L;
  private static final Logger LOGGER = LoggerFactory.getLogger(GetWeasisProtocol.class);

  public static final String CODEBASE_PROPERTY = "weasis.base.url";
  public static final String CODEBASE_EXT_PROPERTY = "weasis.ext.url";
  public static final String SERVICE_CONFIG_PROPERTY = "weasis.config.url";
  public static final String SERVICE_PREFS_PROPERTY = "weasis.pref.url";

  protected static final String PARAM_UPLOAD = "upload";
  private static final String INVALID_MANIFEST = "INVALID";
  private static final int MIN_MANIFEST_LENGTH = 10;
  private static final String WEASIS_PROTOCOL_PREFIX = "weasis://?";
  private static final String DICOM_GET_COMMAND = "$dicom:get -w \"";
  private static final String WEASIS_CONFIG_COMMAND = " $weasis:config";

  public GetWeasisProtocol() {
    super();
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    UploadXml manifest = uploadManifest(request, response);
    if (manifest != null && INVALID_MANIFEST.equals(manifest.xmlManifest(null))) {
      // Error response already sent in uploadManifest
      return;
    }
    invokeWeasis(request, response, manifest);
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    invokeWeasis(request, response, null);
  }

  private static void invokeWeasis(
      HttpServletRequest request, HttpServletResponse response, XmlManifest manifest) {
    try {
      if (LOGGER.isDebugEnabled()) {
        ServletUtil.logInfo(request, LOGGER);
      }

      ServletContext ctx = request.getSession().getServletContext();
      final ConnectorProperties connectorProperties =
          (ConnectorProperties) ctx.getAttribute("componentProperties");
      // Check if the source of this request is allowed
      if (!ServletUtil.isRequestAllowed(request, connectorProperties, LOGGER)) {
        return;
      }

      ConnectorProperties props = connectorProperties.getResolveConnectorProperties(request);

      LaunchUrlBuilder urlBuilder = new LaunchUrlBuilder(request, response, props, manifest);
      String launcherUrlStr = urlBuilder.build();
      LOGGER.info("Redirect to Weasis launcher URL: {}", launcherUrlStr);

      response.sendRedirect(launcherUrlStr);

    } catch (Exception e) {
      LOGGER.error("Redirect to weasis scheme", e);
      ServletUtil.sendResponseError(
          response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  private static class LaunchUrlBuilder {
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final ConnectorProperties props;
    private final XmlManifest manifest;
    private final Map<String, String[]> requestParams;
    private final Map<String, String> requestProperties;
    private final StringBuilder commandBuffer;
    private final StringBuilder configBuffer;
    private boolean isRemoteLaunchConfigDefined;

    public LaunchUrlBuilder(
        HttpServletRequest request,
        HttpServletResponse response,
        ConnectorProperties props,
        XmlManifest manifest) {
      this.request = request;
      this.response = response;
      this.props = props;
      this.manifest = manifest;
      this.requestParams = new LinkedHashMap<>(request.getParameterMap());
      this.commandBuffer = new StringBuilder();
      this.configBuffer = new StringBuilder();

      // Clean up request parameters
      CommonQueryParams.removeWadoQueryParams.accept(requestParams.keySet());
      ConnectorProperties.removeParams.accept(requestParams.keySet());

      this.requestProperties = getPropertiesFromRequestParameters(requestParams);
    }

    public String build() {
      addManifestCommand();
      handleArguments();
      String weasisConfigUrl = resolveConfigUrl();
      this.isRemoteLaunchConfigDefined = StringUtil.hasText(weasisConfigUrl);

      addCodebaseParameters();
      addPreferencesParameter();
      addPropertiesParameters();

      if (isRemoteLaunchConfigDefined) {
        addRemainingParametersToConfig();
        String finalConfigUrl = buildFinalConfigUrl(weasisConfigUrl);
        commandBuffer.append(WEASIS_CONFIG_COMMAND);
        addElement(commandBuffer, WeasisConfig.PARAM_CONFIG_URL, finalConfigUrl);
      } else if (!configBuffer.isEmpty()) {
        commandBuffer.append(WEASIS_CONFIG_COMMAND);
        commandBuffer.append(" ").append(configBuffer);
      }

      return WEASIS_PROTOCOL_PREFIX
          + URLEncoder.encode(commandBuffer.toString().trim(), StandardCharsets.UTF_8);
    }

    private void addManifestCommand() {
      ManifestBuilder builder;
      if (manifest == null) {
        builder = ServletUtil.buildManifest(request, props);
      } else {
        builder = ServletUtil.buildManifest(request, new ManifestBuilder(manifest));
      }

      if (builder != null) {
        String wadoQueryUrl = ServletUtil.buildManifestURL(request, builder, props, true);
        wadoQueryUrl = response.encodeRedirectURL(wadoQueryUrl);

        int startIndex = wadoQueryUrl.indexOf(':');
        if (startIndex > 0) {
          commandBuffer.append(DICOM_GET_COMMAND);
        } else {
          throw new IllegalStateException("Cannot not get a valid manifest URL " + wadoQueryUrl);
        }
        commandBuffer.append(wadoQueryUrl);
        commandBuffer.append("\"");
      }
    }

    private void handleArguments() {
      handleRequestParameters(commandBuffer, requestParams, WeasisConfig.PARAM_ARGUMENT);
    }

    private String resolveConfigUrl() {
      String weasisConfigUrl =
          ServletUtil.getFirstParameter(requestParams.remove(WeasisConfig.PARAM_CONFIG_URL));

      String weasisConfigUrlProp = requestProperties.remove(SERVICE_CONFIG_PROPERTY);
      if (weasisConfigUrl == null) {
        weasisConfigUrl = weasisConfigUrlProp;
      }

      if (weasisConfigUrl == null) {
        weasisConfigUrl = props.getProperty(SERVICE_CONFIG_PROPERTY);
      }

      return weasisConfigUrl;
    }

    private void addCodebaseParameters() {
      String weasisBaseUrl =
          resolveParameter(
              getCodeBaseFromRequest(request),
              requestProperties.remove(CODEBASE_PROPERTY),
              () -> getCodeBaseFromConnectorProperties(props));
      requestParams.remove(WeasisConfig.PARAM_CODEBASE);
      addElementWithEmptyValue(
          configBuffer, WeasisConfig.PARAM_CODEBASE, weasisBaseUrl, isRemoteLaunchConfigDefined);

      String weasisExtUrl =
          resolveParameter(
              getCodeBaseExtFromRequest(request),
              requestProperties.remove(CODEBASE_EXT_PROPERTY),
              () -> getCodeBaseExtFromConnectorProperties(props));
      requestParams.remove(WeasisConfig.PARAM_CODEBASE_EXT);
      addElementWithEmptyValue(
          configBuffer, WeasisConfig.PARAM_CODEBASE_EXT, weasisExtUrl, isRemoteLaunchConfigDefined);
    }

    private void addPreferencesParameter() {
      String weasisPrefURL = requestProperties.get(SERVICE_PREFS_PROPERTY);

      if (weasisPrefURL == null && !isRemoteLaunchConfigDefined) {
        weasisPrefURL = props.getProperty(SERVICE_PREFS_PROPERTY);
        if (StringUtil.hasText(weasisPrefURL)) {
          requestProperties.put(SERVICE_PREFS_PROPERTY, weasisPrefURL.trim());
        }
      }
    }

    private void addPropertiesParameters() {
      for (Entry<String, String> entry : requestProperties.entrySet()) {
        String propertyValue = entry.getKey() + "+" + entry.getValue();
        addElement(
            configBuffer, WeasisConfig.PARAM_PROPERTY, propertyValue, isRemoteLaunchConfigDefined);
      }
    }

    private void addRemainingParametersToConfig() {
      Iterator<Entry<String, String[]>> itParams = requestParams.entrySet().iterator();

      while (itParams.hasNext()) {
        Entry<String, String[]> param = itParams.next();
        String value = ServletUtil.getFirstParameter(param.getValue());
        if (StringUtil.hasText(value)) {
          value = removeEnglobingQuotes(value);
        }
        addElementWithEmptyValue(configBuffer, param.getKey(), value, isRemoteLaunchConfigDefined);
        itParams.remove();
      }
    }

    private String buildFinalConfigUrl(String weasisConfigUrl) {
      if (!configBuffer.isEmpty()) {
        configBuffer.replace(0, 1, "?");
        return weasisConfigUrl + configBuffer.toString();
      }
      return weasisConfigUrl;
    }

    private String resolveParameter(
        String fromRequest,
        String fromProperty,
        java.util.function.Supplier<String> fromConnector) {
      if (fromRequest != null) {
        return fromRequest;
      }
      if (fromProperty != null) {
        return fromProperty;
      }
      if (!isRemoteLaunchConfigDefined) {
        return fromConnector.get();
      }
      return null;
    }
  }

  private static void addElementWithEmptyValue(
      StringBuilder buf, String key, String val, boolean isElementQueryParameter) {
    if (val != null) {
      buf.append(isElementQueryParameter ? '&' : ' ');
      buf.append(key);
      if (StringUtil.hasText(val)) {
        if (isElementQueryParameter) buf.append("=").append(val);
        else buf.append("=\"").append(val).append("\"");
      }
    }
  }

  private static void addElement(StringBuilder buf, String key, String val) {
    addElement(buf, key, val, false);
  }

  private static void addElement(
      StringBuilder buf, String key, String val, boolean isElementQueryParameter) {
    if (StringUtil.hasText(val)) {
      if (WeasisConfig.PARAM_ARGUMENT.equalsIgnoreCase(key)) {
        buf.append(" ");
        if (!(val.startsWith("$"))) buf.append("$");
        buf.append(val);
        // For identifying the commands at start-up, the symbol “$” must be added before the command
        // @see https://nroduit.github.io/en/basics/commands/
      } else {
        buf.append(isElementQueryParameter ? '&' : ' ');
        buf.append(key);
        if (isElementQueryParameter) buf.append("=").append(val);
        else buf.append("=\"").append(val).append("\"");
      }
    }
  }

  protected static String getCodeBaseFromConnectorProperties(ConnectorProperties props) {
    return getCodeBaseFromConnectorProperties(props, false);
  }

  protected static String getCodeBaseExtFromConnectorProperties(ConnectorProperties props) {
    return getCodeBaseFromConnectorProperties(props, true);
  }

  protected static String getCodeBaseFromConnectorProperties(
      ConnectorProperties props, boolean extCodeBase) {
    String propertyName = extCodeBase ? CODEBASE_EXT_PROPERTY : CODEBASE_PROPERTY;
    String codeBasePath = props.getProperty(CODEBASE_PROPERTY);

    if (StringUtil.hasText(codeBasePath)) {
      codeBasePath = codeBasePath.trim();

      if (extCodeBase) {
        codeBasePath = props.getProperty(CODEBASE_EXT_PROPERTY, codeBasePath + "-ext");
      }

      return removeTrailingSlash(codeBasePath);
    }
    return codeBasePath;
  }

  public static String getCodeBaseFromRequest(HttpServletRequest request) {
    return getCodeBaseFromRequest(request, false);
  }

  public static String getCodeBaseExtFromRequest(HttpServletRequest request) {
    return getCodeBaseFromRequest(request, true);
  }

  protected static String getCodeBaseFromRequest(HttpServletRequest request, boolean extCodeBase) {
    String paramName = extCodeBase ? WeasisConfig.PARAM_CODEBASE_EXT : WeasisConfig.PARAM_CODEBASE;
    String codeBasePath = request.getParameter(paramName);

    if (StringUtil.hasText(codeBasePath)) {
      codeBasePath = codeBasePath.trim();

      if (codeBasePath.startsWith("/")) {
        codeBasePath = ServletUtil.getBaseURL(request) + codeBasePath;
      }
      return removeTrailingSlash(codeBasePath);
    }

    return codeBasePath;
  }

  private static String removeTrailingSlash(String path) {
    if (path != null && path.endsWith("/")) {
      return path.substring(0, path.length() - 1);
    }
    return path;
  }

  private static Map<String, String> getPropertiesFromRequestParameters(
      Map<String, String[]> params) {
    Map<String, String> props = new HashMap<>();
    String[] paramValues = ServletUtil.getParameters(params.remove(WeasisConfig.PARAM_PROPERTY));
    if (paramValues != null) {
      Pattern pattern = Pattern.compile("\\s+");
      for (String p : paramValues) {
        String[] res = pattern.split(removeEnglobingQuotes(p), 2);
        if (res.length == 2) {
          props.putIfAbsent(res[0], res[1]);
        } else {
          LOGGER.warn("Cannot parse property: {}", p);
        }
      }
    }
    return props;
  }

  private static void handleRequestParameters(
      StringBuilder buf, Map<String, String[]> params, String param) {
    String[] paramValues = ServletUtil.getParameters(params.remove(param));

    if (paramValues != null) {
      for (String p : paramValues) {
        addElement(buf, param, removeEnglobingQuotes(p));
      }
    }
  }

  private static String removeEnglobingQuotes(String value) {
    return value.replaceAll("(?:^\")|(?:\"$)", "");
  }

  static UploadXml uploadManifest(HttpServletRequest request, HttpServletResponse response) {

    String uploadParam = request.getParameter(PARAM_UPLOAD);
    if (!"manifest".equals(uploadParam)) {
      // No manifest, treat as doGet()
      return null;
    }
    try {
      String manifestContent = readManifestFromRequest(request);

      if (manifestContent.length() <= MIN_MANIFEST_LENGTH) {
        LOGGER.error("Invalid manifest: too short (length={})", manifestContent.length());
        ServletUtil.sendResponseError(
            response, HttpServletResponse.SC_BAD_REQUEST, "Invalid manifest: content too short");
        return new UploadXml(INVALID_MANIFEST, request.getCharacterEncoding());
      }
      return new UploadXml(manifestContent, request.getCharacterEncoding());

    } catch (IOException e) {
      LOGGER.error("Error reading manifest from request", e);
      ServletUtil.sendResponseError(
          response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
      return new UploadXml(INVALID_MANIFEST, request.getCharacterEncoding());
    }
  }

  private static String readManifestFromRequest(HttpServletRequest request) throws IOException {
    StringBuilder buf = new StringBuilder();
    try (BufferedReader reader = request.getReader()) {
      String line;
      while ((line = reader.readLine()) != null) {
        buf.append(line);
      }
    }
    return buf.toString();
  }
}
