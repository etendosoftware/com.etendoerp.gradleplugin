package com.etendoerp.legacy.ant

import org.gradle.api.Project

/**
 * DatabaseLoader - Tareas relacionadas con base de datos
 * 
 * Las tareas de base de datos son complejas y se mantienen en Ant por ahora:
 * - create.database
 * - update.database
 * - export.database
 * - apply.module
 * 
 * Este loader proporciona wrappers de Gradle y configuración adicional
 */
class DatabaseLoader {
    
    static void load(Project project) {
        
        /**
         * Task: etendoCreateDatabase
         * Wrapper de Gradle para create.database de Ant
         */
        project.tasks.register('etendoCreateDatabase') {
            group = 'etendo database'
            description = 'Create database (delegates to Ant)'
            
            doFirst {
                project.logger.lifecycle("*********************************************")
                project.logger.lifecycle("* Creating database")
                project.logger.lifecycle("*********************************************")
                
                validateDatabaseConfig(project)
            }
            
            // Delegar a la tarea Ant
            def antCreateDb = project.tasks.findByName('create.database')
            if (antCreateDb) {
                dependsOn antCreateDb
            } else {
                doLast {
                    project.logger.warn("* Ant task 'create.database' not found")
                    project.logger.warn("* Make sure to run 'expandCore' first")
                }
            }
        }
        
        /**
         * Task: etendoUpdateDatabase
         * Wrapper de Gradle para update.database de Ant
         */
        project.tasks.register('etendoUpdateDatabase') {
            group = 'etendo database'
            description = 'Update database (delegates to Ant)'
            
            doFirst {
                project.logger.info("* Updating database...")
                validateDatabaseConfig(project)
            }
            
            def antUpdateDb = project.tasks.findByName('update.database')
            if (antUpdateDb) {
                dependsOn antUpdateDb
            }
        }
        
        /**
         * Task: etendoExportDatabase
         * Wrapper de Gradle para export.database de Ant
         */
        project.tasks.register('etendoExportDatabase') {
            group = 'etendo database'
            description = 'Export database (delegates to Ant)'
            
            doFirst {
                project.logger.info("* Exporting database...")
            }
            
            def antExportDb = project.tasks.findByName('export.database')
            if (antExportDb) {
                dependsOn antExportDb
            }
        }
        
        /**
         * Task: etendoApplyModule
         * Wrapper de Gradle para apply.module de Ant
         */
        project.tasks.register('etendoApplyModule') {
            group = 'etendo database'
            description = 'Apply module changes to database (delegates to Ant)'
            
            doFirst {
                project.logger.info("* Applying module changes to database...")
            }
            
            def antApplyMod = project.tasks.findByName('apply.module')
            if (antApplyMod) {
                dependsOn antApplyMod
            }
        }
        
        /**
         * Task: databaseInfo
         * Muestra información de configuración de base de datos
         */
        project.tasks.register('databaseInfo') {
            group = 'etendo database'
            description = 'Show database configuration info'
            
            doLast {
                def propsFile = project.file('config/Openbravo.properties')
                
                if (!propsFile.exists()) {
                    project.logger.error("* Openbravo.properties not found")
                    return
                }
                
                def props = new Properties()
                propsFile.withInputStream { props.load(it) }
                
                project.logger.lifecycle("*********************************************")
                project.logger.lifecycle("* Database Configuration")
                project.logger.lifecycle("*********************************************")
                project.logger.lifecycle("  Driver:   ${props.getProperty('bbdd.driver', 'N/A')}")
                project.logger.lifecycle("  URL:      ${props.getProperty('bbdd.url', 'N/A')}")
                project.logger.lifecycle("  User:     ${props.getProperty('bbdd.user', 'N/A')}")
                project.logger.lifecycle("  SID:      ${props.getProperty('bbdd.sid', 'N/A')}")
                project.logger.lifecycle("  Type:     ${props.getProperty('bbdd.rdbms', 'N/A')}")
                project.logger.lifecycle("*********************************************")
            }
        }
        
        /**
         * Task: validateDatabase
         * Valida la conexión a la base de datos
         */
        project.tasks.register('validateDatabase') {
            group = 'etendo database'
            description = 'Validate database connection'
            
            doLast {
                validateDatabaseConfig(project)
                
                // TODO: Implementar validación real de conexión
                // Por ahora solo valida la configuración
                project.logger.lifecycle("* Database configuration is valid")
            }
        }
    }
    
    /**
     * Valida que la configuración de base de datos esté presente
     */
    private static void validateDatabaseConfig(Project project) {
        def propsFile = project.file('config/Openbravo.properties')
        
        if (!propsFile.exists()) {
            throw new org.gradle.api.GradleException(
                "Openbravo.properties not found. Run 'setup' first."
            )
        }
        
        def props = new Properties()
        propsFile.withInputStream { props.load(it) }
        
        def requiredProps = ['bbdd.driver', 'bbdd.url', 'bbdd.user', 'bbdd.password']
        def missing = requiredProps.findAll { !props.getProperty(it) }
        
        if (!missing.isEmpty()) {
            throw new org.gradle.api.GradleException(
                "Missing database properties: ${missing.join(', ')}"
            )
        }
    }
}
