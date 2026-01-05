package com.etendoerp.legacy.install

import org.gradle.api.Project
import groovy.sql.Sql

class InstallLoader {

    static void load(Project project) {
        project.afterEvaluate {
            createInstallTask(project)
        }
    }

    private static void createInstallTask(Project project) {
        project.tasks.register('install') {
            description = 'Instalación completa optimizada (usa smartbuild en lugar de Ant)'
            group = 'etendo-build'

            // === SETUP OPCIONAL ===
            // Ejecutar setup solo si doSetup=true (default true)
            def doSetup = project.hasProperty("doSetup") ?
                          project.property("doSetup").toString().toBoolean() : true

            if (doSetup) {
                def setupTask = project.tasks.findByName("setup")
                if (setupTask != null) {
                    dependsOn setupTask
                }
            }

            // === LIMPIEZA Y PREPARACIÓN ===
            dependsOn 'cleanSubfolders'

            // === CREACIÓN DE BASE DE DATOS ===
            // create.database depende de init y core.lib (ya incluido en smartbuild)
            def createDbTask = project.tasks.findByName('create.database')
            if (createDbTask != null) {
                dependsOn createDbTask
            } else {
                project.logger.warn("Task 'create.database' not found. Install may fail.")
            }

            // === BUILD OPTIMIZADO ===
            // Usa smartbuild en lugar de compile.complete.deploy
            // smartbuild ya incluye: wad.lib, trl.lib, compilación, copia de archivos
            def smartbuildTask = project.tasks.findByName('smartbuild')
            if (smartbuildTask != null) {
                dependsOn smartbuildTask
            } else {
                project.logger.warn("Task 'smartbuild' not found.")
            }

            // === DEPLOY A TOMCAT (si está en mode.class) ===
            def deployTask = project.tasks.findByName('gradleDeployToTomcat')
            if (deployTask != null) {
                dependsOn deployTask
            }

            // === APLICACIÓN DE MÓDULOS ===
            def applyModuleTask = project.tasks.findByName('apply.module')
            if (applyModuleTask != null) {
                dependsOn applyModuleTask
            }

            // === IMPORTACIÓN DE DATOS DE EJEMPLO ===
            def importSampleDataTask = project.tasks.findByName('import.sample.data')
            if (importSampleDataTask != null) {
                dependsOn importSampleDataTask
            }

            // === CONFIGURACIÓN DE ORDEN ===
            configureInstallTaskOrdering(project)

            doLast {
                // Update system status to RB51 (Install complete)
                updateSystemStatus(project, "RB51")

                // Set applied in AD_SYSTEM_INFO
                setApplied(project)

                project.logger.lifecycle("✓ Install completed - Etendo installed successfully")
                project.logger.lifecycle("  - Database created and configured")
                project.logger.lifecycle("  - Application built with: smartbuild (Gradle optimized)")
                project.logger.lifecycle("  - Modules applied and sample data imported")
                project.logger.lifecycle("  - System status updated to RB51")
            }
        }
    }

    /**
     * Updates system status in AD_SYSTEM_INFO
     * Replicates macro updatesystemstatus from build.xml
     */
    private static void updateSystemStatus(Project project, String status) {
        project.logger.info("Updating system status to ${status}")
        
        Sql sqlInstance = null
        try {
            def driver = getProperty(project, 'bbdd.driver')
            def url = getProperty(project, 'bbdd.owner.url')
            def user = getProperty(project, 'bbdd.user')
            def password = getProperty(project, 'bbdd.password')

            if (!driver || !url || !user || !password) {
                 project.logger.warn("Skipping updateSystemStatus: Missing database properties")
                 return
            }

            sqlInstance = Sql.newInstance(url, user, password, driver)
            sqlInstance.execute("UPDATE ad_system_info SET system_status=?", [status])
            
        } catch (Exception e) {
            project.logger.warn("Could not update system status: ${e.message}")
        } finally {
            if (sqlInstance != null) {
                sqlInstance.close()
            }
        }
    }

    private static String getProperty(Project project, String key) {
        if (project.hasProperty(key)) {
            return project.property(key)
        }
        // Check ant properties as fallback (from build.xml)
        if (project.ant.properties.containsKey(key)) {
            return project.ant.properties[key]
        }
        return null
    }

    /**
     * Sets applied flag in database
     * Calls 'setApplied' target from src-db/database/build.xml
     */
    private static void setApplied(Project project) {
        project.logger.info("Setting database as applied")
        try {
            project.ant.ant(
                dir: project.file('src-db/database'),
                target: 'setApplied',
                inheritAll: true,
                inheritRefs: true
            )
        } catch (Exception e) {
            project.logger.warn("Could not set database as applied: ${e.message}")
        }
    }

    private static void configureInstallTaskOrdering(Project project) {
        def setupTask = project.tasks.findByName('setup')
        def cleanSubfoldersTask = project.tasks.findByName('cleanSubfolders')
        def createDbTask = project.tasks.findByName('create.database')
        def smartbuildTask = project.tasks.findByName('smartbuild')
        def wadGenerateSqlc = project.tasks.findByName('wadGenerateSqlc')
        def deployTask = project.tasks.findByName('gradleDeployToTomcat')
        def applyModuleTask = project.tasks.findByName('apply.module')
        def importSampleDataTask = project.tasks.findByName('import.sample.data')

        // 1. Setup debe ejecutarse primero (si está presente)
        if (setupTask != null && cleanSubfoldersTask != null) {
            cleanSubfoldersTask.mustRunAfter setupTask
        }

        // 2. create.database debe ejecutarse después de cleanSubfolders
        if (createDbTask != null && cleanSubfoldersTask != null) {
            createDbTask.mustRunAfter cleanSubfoldersTask
        }

        // 3. smartbuild debe ejecutarse después de create.database
        if (smartbuildTask != null && createDbTask != null) {
            smartbuildTask.mustRunAfter createDbTask
        }

        // Ensure wadGenerateSqlc runs after create.database because Sqlc requires DB connection
        if (wadGenerateSqlc != null && createDbTask != null) {
            wadGenerateSqlc.mustRunAfter createDbTask
            project.logger.info("Configured wadGenerateSqlc to run after create.database")
        } else {
             if (wadGenerateSqlc == null) project.logger.warn("wadGenerateSqlc task not found for ordering")
             if (createDbTask == null) project.logger.warn("create.database task not found for ordering")
        }

        // 4. deploy debe ejecutarse después de smartbuild
        if (deployTask != null && smartbuildTask != null) {
            deployTask.mustRunAfter smartbuildTask
        }

        // 5. apply.module debe ejecutarse después de deploy
        if (applyModuleTask != null && deployTask != null) {
            applyModuleTask.mustRunAfter deployTask
        } else if (applyModuleTask != null && smartbuildTask != null) {
            applyModuleTask.mustRunAfter smartbuildTask
        }

        // 6. import.sample.data debe ejecutarse al final
        if (importSampleDataTask != null && applyModuleTask != null) {
            importSampleDataTask.mustRunAfter applyModuleTask
        }
    }
}
