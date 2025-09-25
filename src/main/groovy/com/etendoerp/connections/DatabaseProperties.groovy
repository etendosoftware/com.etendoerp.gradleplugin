package com.etendoerp.connections

import org.gradle.api.Project

class DatabaseProperties {

    final static String PROPERTIES_FILE = "Openbravo.properties"

    final static String RDBMS_PROPERTY = "bbdd.rdbms"
    final static String DRIVER_PROPERTY = "bbdd.driver"
    final static String URL_PROPERTY = "bbdd.url"
    final static String SID_PROPERTY = "bbdd.sid"
    final static String SYSTEM_USER_PROPERTY = "bbdd.systemUser"
    final static String SYSTEM_PASSWORD_PROPERTY = "bbdd.systemPassword"
    final static String USER_PROPERTY = "bbdd.user"
    final static String PASSWORD_PROPERTY = "bbdd.password"

    // Properties used to connect with the database
    final static List<String> PROPERTIES = [
            RDBMS_PROPERTY,
            DRIVER_PROPERTY,
            URL_PROPERTY,
            SID_PROPERTY,
            USER_PROPERTY,
            PASSWORD_PROPERTY,
            SYSTEM_USER_PROPERTY,
            SYSTEM_PASSWORD_PROPERTY,
    ]

    Project project

    DatabaseType databaseType
    String rdbms
    String driver
    String url
    String sid
    String systemUser
    String systemPassword
    String user
    String password

    Properties propertiesFile
    File propertiesFileLocation

    DatabaseProperties(Project project) {
        this.project = project
    }

    boolean loadDatabaseProperties() {
        return loadDatabaseProperties(false)
    }

    boolean loadDatabaseProperties(boolean adminConnection) {
        def propertiesFileLocation = new File(project.rootDir, "config" + File.separator + PROPERTIES_FILE)
        if (!propertiesFileLocation.exists()) {
            project.logger.info("* WARNING: Etendo plugin database connection. The properties file ${propertiesFileLocation.absolutePath} does not exists.")
            return false
        }

        this.propertiesFileLocation = propertiesFileLocation

        def properties = new Properties()
        properties.load(new FileInputStream(propertiesFileLocation))
        this.propertiesFile = properties

        Map loadedProperties = loadProperties(PROPERTIES)

        this.rdbms = loadedProperties.get(RDBMS_PROPERTY)
        this.driver = loadedProperties.get(DRIVER_PROPERTY)
        this.url = loadedProperties.get(URL_PROPERTY)
        this.sid = loadedProperties.get(SID_PROPERTY)
        this.user = adminConnection ? loadedProperties.get(SYSTEM_USER_PROPERTY) : loadedProperties.get(USER_PROPERTY)
        this.password = adminConnection ? loadedProperties.get(SYSTEM_PASSWORD_PROPERTY) : loadedProperties.get(PASSWORD_PROPERTY)
        this.databaseType = DatabaseType.parseType(rdbms)

        return true
    }

    String getDatabaseUrl() {
        return "${this.url}/${this.sid}"
    }

    Map loadProperties(List<String> propertiesToSearch) {
        Map propertiesValues = new HashMap()
        for (String property : propertiesToSearch) {
            if (this.propertiesFile == null || !this.propertiesFile.containsKey(property) || this.propertiesFile[property] == null) {
                String message = "* WARNING: Etendo plugin database connection. The file ${this.propertiesFileLocation.absolutePath} does not contain the '${property}' property."
                project.logger.error(message)
                throw new IllegalArgumentException(message)
            } else {
                propertiesValues.put(property, this.propertiesFile[property])
            }
        }
        return propertiesValues
    }
}
