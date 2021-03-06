/*
 * Copyright (c) 2016-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.query;

import java.util.Base64;
import java.util.Objects;
import java.util.Properties;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.mf.AbstractQueryResult;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.servlet.ConnectorProperties;

public abstract class AbstractQueryConfiguration extends AbstractQueryResult {

  protected final Properties properties;

  public AbstractQueryConfiguration(Properties properties) {
    this.properties = Objects.requireNonNull(properties, "properties cannot be null!");
  }

  public abstract void buildFromPatientID(CommonQueryParams params, String... patientIDs);

  public abstract void buildFromStudyInstanceUID(
      CommonQueryParams params, String... studyInstanceUIDs);

  public abstract void buildFromStudyAccessionNumber(
      CommonQueryParams params, String... accessionNumbers);

  public abstract void buildFromSeriesInstanceUID(
      CommonQueryParams params, String... seriesInstanceUIDs);

  public abstract void buildFromSopInstanceUID(CommonQueryParams params, String... sopInstanceUIDs);

  @Override
  public WadoParameters getWadoParameters() {
    String wadoQueriesURL =
        properties.getProperty("arc.wado.url", properties.getProperty("server.base.url") + "/wado");
    boolean onlysopuid = LangUtil.getEmptytoFalse(properties.getProperty("wado.onlysopuid"));
    String addparams = properties.getProperty("wado.addparams", "");
    String overrideTags = properties.getProperty("wado.override.tags");
    // If the web server requires an authentication (arc.web.login=user:pwd)
    String webLogin = properties.getProperty("arc.web.login");
    if (StringUtil.hasText(webLogin)) {
      webLogin = Base64.getEncoder().encodeToString(webLogin.trim().getBytes());
    }
    String httpTags = properties.getProperty("wado.httpTags");

    WadoParameters wado =
        new WadoParameters(
            getArchiveID(), wadoQueriesURL, onlysopuid, addparams, overrideTags, webLogin);
    if (StringUtil.hasText(httpTags)) {
      for (String tag : httpTags.split(",")) {
        String[] val = tag.split(":");
        if (val.length == 2) {
          wado.addHttpTag(val[0].trim(), val[1].trim());
        }
      }
    }
    return wado;
  }

  public Properties getProperties() {
    return properties;
  }

  public String getArchiveID() {
    return properties.getProperty("arc.id");
  }

  public String getArchiveConfigName() {
    return properties.getProperty(ConnectorProperties.CONFIG_FILENAME);
  }
}
