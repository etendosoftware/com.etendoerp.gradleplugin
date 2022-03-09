package com.etendoerp.connections

import javax.sql.DataSource
import org.apache.commons.dbcp2.BasicDataSource

class DBCPDataSourceFactory {

    static DataSource getDatasource(DatabaseProperties databaseProperties) {
        BasicDataSource ds = new BasicDataSource()

        ds.setDriverClassName(databaseProperties.driver)
        ds.setUrl(databaseProperties.getDatabaseUrl())
        ds.setUsername(databaseProperties.user)
        ds.setPassword(databaseProperties.password)

        return ds
    }

}
