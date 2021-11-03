package com.etendoerp.gradle.jars.core.coreinsources

import groovy.sql.GroovyRowResult
import groovy.sql.Sql

class CoreUtils {

    static Boolean containsModule(String moduleName, Object connection) {
        List<GroovyRowResult> queryResult = null

        String query = "select javapackage from ad_module where javapackage = '${moduleName}'"

        def con = connection
        con.url = "${con.url}${System.getProperty('test.bbdd.sid')}"

        Sql.withInstance(con as Map<String, Object>) {
            Sql sql ->  queryResult = sql.rows(query)
        }

        for (GroovyRowResult row : queryResult) {
            def javaPackage = row.javapackage
            if (javaPackage && javaPackage == moduleName) {
                return true
            }
        }
        return false
    }

}
