package com.etendoerp.gradle.jars.core.coreinsources

import groovy.sql.GroovyRowResult
import groovy.sql.Sql

class CoreUtils {

    static Map<String, GroovyRowResult> getMapOfModules(Object connection) {
        Map<String, GroovyRowResult> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
        String qry = "select * from ad_module"
        def rowResult = executeQuery(qry, connection)

        if (rowResult) {
            for (GroovyRowResult row : rowResult) {
                def javaPackage = row.javapackage as String
                if (javaPackage) {
                    map.put(javaPackage, row)
                }
            }
        }

        return map
    }

    static Boolean containsModule(String moduleName, Object connection) {
        List<GroovyRowResult> queryResult = null

        String query = "select javapackage from ad_module where javapackage = '${moduleName}'"

        def con = connection
        con.url = "${con.url}${con.unused}"
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

    static List<GroovyRowResult> executeQuery(String query, Object connection) {
        List<GroovyRowResult> queryResult = null

        def con = connection
        con.url = "${con.url}${con.unused}"

        Sql.withInstance(con as Map<String, Object>) {
            Sql sql ->  queryResult = sql.rows(query)
        }

        return queryResult
    }

    static List<List<Object>> executeQueryInserts(String query, Object connection) {
        def queryResult = null

        def con = connection
        con.url = "${con.url}${con.unused}"

        Sql.withInstance(con as Map<String, Object>) {
            Sql sql ->  queryResult = sql.executeInsert(query)
        }

        return queryResult
    }

    static int executeQueryUpdate(String query, Object connection) {
        int queryResult = 0

        def con = connection
        con.url = "${con.url}${con.unused}"

        Sql.withInstance(con as Map<String, Object>) {
            Sql sql ->  queryResult = sql.executeUpdate(query)
        }

        return queryResult
    }

    static Boolean updateModule(String javapackage, Map valuesMap, Object connection) {
        def values = generateUpdateQueryValues(valuesMap)

        def qry = "update ad_module set ${values} where javapackage = '${javapackage}'"
        def qryResult = executeQueryUpdate(qry, connection)

        if (qryResult == 0) {
            return false
        }
        return true
    }

    static String generateUpdateQueryValues(Map valuesMap) {
        def values = ""
        valuesMap.each {
            values += "${it.getKey()}='${it.getValue()}',"
        }
        values = values.substring(0,values.length() - 1)
        return values
    }

}
