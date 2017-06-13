package org.weasis.query.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.StringUtil;

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

    public static final void safeClose(Object... sqlObjects) {
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

    public static final DbQuery executeDBQuery(String query, Properties dbProperties) throws SQLException {
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
                connection = DriverManager.getConnection(dbProperties.getProperty("arc.db.uri"),
                    dbProperties.getProperty("arc.db.user"), dbProperties.getProperty("arc.db.password"));
                statement = connection.createStatement();

                long startQuery = System.currentTimeMillis();
                resultSet = statement.executeQuery(query);

                long dbQueryTime = System.currentTimeMillis() - startQuery;
                if (dbQueryTime > 3000) { // to get LOG less verbose and trace only uncommon long time DB response
                    LOGGER.info("executeDBQuery - SQL request executed in {} ms :\n\t[{}]", dbQueryTime, query);
                } else {
                    LOGGER.debug("executeDBQuery - SQL request executed in {} ms :\n\t[{}]", dbQueryTime, query);
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
