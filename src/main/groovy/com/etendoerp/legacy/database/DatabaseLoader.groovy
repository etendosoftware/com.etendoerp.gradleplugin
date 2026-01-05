package com.etendoerp.legacy.database

import org.gradle.api.Project
import groovy.sql.Sql

class DatabaseLoader {

    static void load(Project project) {
        project.tasks.register('gradleCreateDatabase') {
            description = 'Creates the database structure and data (Native Gradle implementation)'
            group = 'etendo-database'

            dependsOn 'core.lib' // Needs core classes

            doLast {
                createDatabase(project)
            }
        }
    }

    private static void createDatabase(Project project) {
        def rdbms = getProperty(project, 'bbdd.rdbms')
        
        project.logger.lifecycle("Creating database for ${rdbms}...")

        // 1. Clean Database (Drop)
        cleanDatabase(project, rdbms)

        // 2. Create Structure (Create DB and User)
        createStructure(project, rdbms)

        // 3. Create Database All (Run Java Task via Ant)
        createDatabaseAll(project)

        // 4. Post Create (SQL scripts)
        postCreate(project, rdbms)

        // 5. Update Timestamp
        updateTimestamp(project)
    }

    private static void cleanDatabase(Project project, String rdbms) {
        project.logger.info("Cleaning database...")
        def driver = getProperty(project, 'bbdd.driver')
        def url = getProperty(project, 'bbdd.url')
        def systemUser = getProperty(project, 'bbdd.systemUser')
        def systemPassword = getProperty(project, 'bbdd.systemPassword')
        def user = getProperty(project, 'bbdd.user')
        def sid = getProperty(project, 'bbdd.sid')

        if (rdbms.equalsIgnoreCase('POSTGRE')) {
            // Drop Database
            executeSql(project, driver, "${url}/postgres", systemUser, systemPassword, "DROP DATABASE IF EXISTS ${sid}", true, true)
            // Drop Role (Non-fatal)
            executeSql(project, driver, "${url}/postgres", systemUser, systemPassword, "DROP ROLE IF EXISTS ${user}", true, true)
        } else {
             project.logger.warn("Clean database not implemented for ${rdbms} in Gradle. Please use Ant task.")
        }
    }

    private static void createStructure(Project project, String rdbms) {
        project.logger.info("Creating database structure...")
        def driver = getProperty(project, 'bbdd.driver')
        def url = getProperty(project, 'bbdd.url')
        def systemUser = getProperty(project, 'bbdd.systemUser')
        def systemPassword = getProperty(project, 'bbdd.systemPassword')
        def user = getProperty(project, 'bbdd.user')
        def password = getProperty(project, 'bbdd.password')
        def sid = getProperty(project, 'bbdd.sid')

        if (rdbms.equalsIgnoreCase('POSTGRE')) {
            // Create Role (Non-fatal if exists)
            executeSql(project, driver, "${url}/postgres", systemUser, systemPassword, 
                "CREATE ROLE ${user} LOGIN PASSWORD '${password}' CREATEDB CREATEROLE VALID UNTIL 'infinity'", true, true)
            
            // Create Database
            executeSql(project, driver, "${url}/postgres", user, password, 
                "CREATE DATABASE ${sid} WITH ENCODING='UTF8' TEMPLATE=template0", true)
        }
    }

    private static void createDatabaseAll(Project project) {
        project.logger.info("Executing CreateDatabase task...")
        
        // Use the classpath already defined by AntLoader/build.xml
        def antClasspath = project.ant.references['project.class.path']

        // Define task
        project.ant.taskdef(name: 'createdatabase', 
                            classname: 'org.openbravo.ddlutils.task.CreateDatabase') {
            classpath {
                pathelement(path: antClasspath)
            }
        }

        def baseDir = project.projectDir.absolutePath
        
        // Input string exactly as in Ant (relative to basedir)
        def input = [
            'src-db/database/sourcedata',
            'modules/*/src-db/database/sourcedata',
            'modules_core/*/src-db/database/sourcedata',
            'build/etendo/modules/*/src-db/database/sourcedata',
            'build/etendo/src-db/database/sourcedata'
        ].join(',')

        // Execute task
        project.ant.createdatabase(
            driver: getProperty(project, 'bbdd.driver'),
            url: getProperty(project, 'bbdd.owner.url'),
            user: getProperty(project, 'bbdd.user'),
            password: getProperty(project, 'bbdd.password'),
            model: 'model', 
            object: getProperty(project, 'bbdd.object'),
            dropfirst: false, // Usar booleano real
            failonerror: true,
            basedir: baseDir,
            modulesDir: project.file('modules').absolutePath,
            dirFilter: '*/src-db/database/model', 
            filter: 'com.openbravo.db.OpenbravoMetadataFilter',
            input: input,
            systemUser: getProperty(project, 'bbdd.systemUser'),
            systemPassword: getProperty(project, 'bbdd.systemPassword'),
            isCoreInSources: true // Usar booleano real
        )
    }
    
    // Helper to execute SQL using Groovy Sql to ensure connections are closed
    private static void executeSql(Project project, String driver, String url, String user, String password, String sql, boolean autocommit = true, boolean continueOnError = false) {
        Sql sqlInstance = null
        try {
            sqlInstance = Sql.newInstance(url, user, password, driver)
            sqlInstance.getConnection().setAutoCommit(autocommit)
            
            // Split by semicolon if it's a block, but be careful with functions
            // For simplicity, if it contains multiple lines and ends with semicolon, we try to execute as is
            // or split if it's a simple script.
            if (sql.trim().contains(';')) {
                sql.split(';').each { statement ->
                    if (statement.trim()) {
                        sqlInstance.execute(statement.trim())
                    }
                }
            } else {
                sqlInstance.execute(sql)
            }
        } catch (Exception e) {
            if (!continueOnError) throw e
            project.logger.warn("SQL execution error (ignored): ${e.message}")
        } finally {
            if (sqlInstance != null) {
                sqlInstance.close()
            }
        }
    }

    private static void postCreate(Project project, String rdbms) {
       project.logger.info("Executing Post-Create SQL...")
       def driver = getProperty(project, 'bbdd.driver')
       def url = getProperty(project, 'bbdd.owner.url')
       def user = getProperty(project, 'bbdd.user')
       def password = getProperty(project, 'bbdd.password')
       
       if (rdbms.equalsIgnoreCase('POSTGRE')) {
           def sqlBlock = """
              DELETE FROM AD_SYSTEM;
              INSERT INTO AD_SYSTEM (AD_SYSTEM_ID, AD_CLIENT_ID, AD_ORG_ID, ISACTIVE, CREATED, CREATEDBY, UPDATED, UPDATEDBY, NAME, TAD_RECORDRANGE, TAD_RECORDRANGE_INFO, TAD_TRANSACTIONALRANGE, TAD_THEME)
              VALUES ('0', '0', '0', 'Y', NOW(), '0', NOW(), '0', '?', 20, 100, 1,
                      (SELECT Value FROM AD_Ref_List
                        WHERE AD_Ref_List_ID in ('800247', '27F0D1235450423C814D3A0DCABA7D10')
                        ORDER BY (CASE WHEN Name ='Default' THEN 2 ELSE 1 END)
                        LIMIT 1));
              DELETE FROM AD_SYSTEM_INFO;
              INSERT INTO AD_SYSTEM_INFO(AD_SYSTEM_INFO_ID, AD_CLIENT_ID, AD_ORG_ID, ISACTIVE, CREATED, CREATEDBY, UPDATED, UPDATEDBY, ANT_VERSION, OB_INSTALLMODE, MATURITY_UPDATE, MATURITY_SEARCH,
                    your_company_login_image, your_it_service_login_image, your_company_menu_image, your_company_big_image, your_company_document_image, support_contact)
              VALUES('0', '0', '0', 'Y', NOW(), '0', NOW(), '0', 'Unknown', 'From Sources', '200', '200',
                    '37B37B6A8876462780DB969E5C4D81FD', '6C216D1786B34105ACCBA4DD8612A0CE', '5F3C04DF603F409A875C294910BD3491', '0A41E7C5497B46559BD03AD4100F8FEB', 'AA90B7900AD04E87A890BA2E2604A6D9', 'www.your-it-service.com');
              SELECT AD_UPDATE_ACCESS();
              SELECT AD_DB_MODIFIED('Y');
              UPDATE AD_MODULE SET ISINDEVELOPMENT='N', ISDEFAULT='N', SEQNO=NULL, STATUS='P', UPDATE_AVAILABLE=NULL, ISREGISTERED='N' WHERE STATUS IS NULL OR STATUS='I';
              SELECT AD_LANGUAGE_CREATE(NULL);
              ANALYZE;
           """
           executeSql(project, driver, url, user, password, sqlBlock)
       }
    }

    private static void updateTimestamp(Project project) {
       executeSql(project, 
           getProperty(project, 'bbdd.driver'),
           getProperty(project, 'bbdd.owner.url'),
           getProperty(project, 'bbdd.user'),
           getProperty(project, 'bbdd.password'),
           "UPDATE AD_SYSTEM_INFO SET LAST_DBUPDATE = NOW()",
           true, true
       )
    }

    private static String getProperty(Project project, String key) {
        if (project.hasProperty(key)) return project.property(key)
        if (project.ant.properties.containsKey(key)) return project.ant.properties[key]
        return null
    }
}
