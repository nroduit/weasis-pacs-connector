/*
 * Copyright (c) 2016-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.query.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

public record DbQuery(Connection connection, Statement statement, ResultSet resultSet) {

  private static final Logger LOGGER = LoggerFactory.getLogger(DbQuery.class);

  public void close() {
    safeClose(connection, resultSet, statement);
  }

  public static void safeClose(Object... sqlObjects) {
    if (sqlObjects == null) {
      return;
    }
    for (Object sqlClosableObject : sqlObjects) {
      try {
        if (sqlClosableObject instanceof Connection) {
          ((Connection) sqlClosableObject).close();
        } else if (sqlClosableObject instanceof Statement) {
          ((Statement) sqlClosableObject).close();
        } else if (sqlClosableObject instanceof ResultSet) {
          ((ResultSet) sqlClosableObject).close();
        }
      } catch (Exception e) {
        LOGGER.warn("Exception when closing sqlClosableObject", e);
      }
    }
  }

  public static DbQuery executeDBQuery(String query, Properties dbProperties) throws SQLException {
    if (StringUtil.hasText(query) && dbProperties != null) {
      try {
        Class.forName(dbProperties.getProperty("arc.db.driver"));
      } catch (ClassNotFoundException e) {
        throw new SQLException("Cannot load Database Driver", e);
      }

      Connection connection = null;
      Statement statement = null;
      ResultSet resultSet = null;
      try {
        connection =
            DriverManager.getConnection(
                dbProperties.getProperty("arc.db.uri"),
                dbProperties.getProperty("arc.db.user"),
                dbProperties.getProperty("arc.db.password"));
        statement = connection.createStatement();

        long startQuery = System.currentTimeMillis();
        resultSet = statement.executeQuery(query);

        long dbQueryTime = System.currentTimeMillis() - startQuery;
        if (dbQueryTime
            > 3000) { // to get LOG less verbose and trace only uncommon long time DB response
          LOGGER.info(
              "executeDBQuery - SQL request executed in {} ms :\n\t[{}]", dbQueryTime, query);
        } else {
          LOGGER.debug(
              "executeDBQuery - SQL request executed in {} ms :\n\t[{}]", dbQueryTime, query);
        }
      } catch (SQLException e) {
        // Ensure to close DB connection with any exception
        DbQuery.safeClose(connection, resultSet, statement);
        throw e;
      }
      return new DbQuery(connection, statement, resultSet);
    }
    return null;
  }
}
