package com.etendoerp.connections

import javax.sql.DataSource
import org.apache.commons.dbcp2.BasicDataSource

class DBCPDataSourceFactory {

    static DataSource getDatasource(DatabaseProperties databaseProperties) {
        BasicDataSource ds = new BasicDataSource()

        ds.driverClassName = databaseProperties.driver
        ds.url = databaseProperties.getDatabaseUrl()
        ds.username = databaseProperties.user
        ds.password = databaseProperties.password

        return ds
    }

}