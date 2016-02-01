package org.weasis.query.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.util.StringUtil;

public class DbQuery {
    private static final Logger LOGGER = LoggerFactory.getLogger(DbQuery.class);

    private final Connection connection;
    private final ResultSet resultSet;
    private final Statement statement;

    public DbQuery(Connection connection, Statement statement, ResultSet resultSet) {
        this.connection = connection;
        this.resultSet = resultSet;
        this.statement = statement;
    }

    public Connection getConnection() {
        return connection;
    }

    public ResultSet getResultSet() {
        return resultSet;
    }

    public Statement getStatement() {
        return statement;
    }

    public void close() {
        safeClose(connection, resultSet, statement);
    }

    public final static void safeClose(Object... sqlObjects) {
        if (sqlObjects != null) {
            for (Object sqlClosableObject : sqlObjects) {
                try {
                    if (sqlClosableObject instanceof Connection) {
                        ((Connection) sqlClosableObject).close();
                    } else if (sqlClosableObject instanceof Statement) {
                        ((Statement) sqlClosableObject).close();
                    } else if (sqlClosableObject instanceof ResultSet) {
                        ((ResultSet) sqlClosableObject).close();
                    }
                } catch (Exception doNothing) {
                    LOGGER.warn("Exception on {} close: {}", sqlClosableObject.getClass(), doNothing.getMessage());
                }
            }
        }
    }

    public final static DbQuery executeDBQuery(String query, Properties dbProperties) throws SQLException {
        if (StringUtil.hasText(query) && dbProperties != null) {
            try {
                Class.forName(dbProperties.getProperty("pacs.db.driver"));
            } catch (ClassNotFoundException e) {
                throw new SQLException("Cannot load Database Driver: " + e.getMessage());
            }

            Connection connection = DriverManager.getConnection(dbProperties.getProperty("pacs.db.uri"),
                dbProperties.getProperty("pacs.db.user"), dbProperties.getProperty("pacs.db.password"));
            Statement statement = connection.createStatement();

            long startQuery = System.nanoTime();
            ResultSet resultSet = statement.executeQuery(query);

            long dbQueryTime = (System.nanoTime() - startQuery) / 1000000; // ms
            if (dbQueryTime > 4000) { // to get LOG less verbose and trace only uncommon long time DB response
                LOGGER.info("executeDBQuery - SQL request executed in {} ms :\n\t[{}]", dbQueryTime, query);
            } else {
                LOGGER.debug("executeDBQuery - SQL request executed in {} ms :\n\t[{}]", dbQueryTime, query);
            }
            return new DbQuery(connection, statement, resultSet);
        }
        return null;
    }
}
