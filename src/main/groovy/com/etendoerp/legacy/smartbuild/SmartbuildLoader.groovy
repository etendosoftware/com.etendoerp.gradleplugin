package com.etendoerp.legacy.smartbuild

import com.etendoerp.legacy.ant.ConsistencyVerification
import com.etendoerp.legacy.copy.GradleCopyTasksLoader
import org.gradle.api.Project

/**
 * Loader para la tarea smartbuild nativa de Gradle.
 *
 * Esta tarea reemplaza completamente smartbuild de Ant con una implementación
 * optimizada en Gradle puro que aprovecha:
 * - Compilación incremental nativa de Gradle
 * - Build cache
 * - Tareas de copia optimizadas e incrementales
 *
 * Smartbuild de Gradle NO usa compile.complete de Ant, sino compileJava nativo.
 */
class SmartbuildLoader {

    static void load(Project project) {
        GradleCopyTasksLoader.load(project)
        createSmartbuildTask(project)
        createOptimizedBuildTask(project)
        createOptimizedDeployTask(project)
        logAvailableTasks(project)
    }

    /**
     * Crea la tarea principal smartbuild - Build completo optimizado en Gradle puro
     */
    private static void createSmartbuildTask(Project project) {
        project.tasks.register('smartbuild') {
            description = 'Build completo optimizado (Gradle puro) - reemplaza smartbuild de Ant'
            group = 'etendo-build'

            // === DEPENDENCIAS BÁSICAS (igual que tareas Ant) ===
            dependsOn 'compileFilesCheck'
            dependsOn ConsistencyVerification.CONSISTENCY_VERIFICATION_TASK

            // Dependency sync (si existe)
            def depSync = project.tasks.findByName("dependencies.sync")
            if (depSync != null) {
                dependsOn depSync
            }

            // === GENERACIÓN DE CÓDIGO (Optimizado) ===
            dependsOn 'gradleSqlc'     // Genera código SQLC general
            dependsOn 'gradleWad'      // Ejecuta WAD (Genera UI y web.xml)
            dependsOn 'gradleTrlSqlc'  // Genera código SQLC de traducciones
            dependsOn 'gradleTrlJar'   // Genera JAR de traducciones

            // Generar entidades (si existe la tarea)
            def generateEntities = project.tasks.findByName('generate.entities.quick')
            if (generateEntities != null) {
                dependsOn generateEntities
            }

            // === COMPILACIÓN GRADLE UNIFICADA ===
            // compileJava ahora incluye src, src-core y src-wad
            dependsOn 'compileJava'

            // === TAREAS DE COPIA OPTIMIZADAS DE GRADLE ===
            // Copiar recursos de módulos
            dependsOn 'gradleCopyModuleResources'
            dependsOn 'gradleCopyModuleBuildResources'
            dependsOn 'gradleCopyModuleWeb'
            dependsOn 'gradleCopySkins'

            // Copiar recursos generales
            dependsOn 'gradlePostsrc'

            // Copiar al contexto local (WebContent)
            dependsOn 'gradleBuildLocalContext'
            dependsOn 'gradleCopyWebInf'
            dependsOn 'gradleCopyReferenceData'
            dependsOn 'gradleSyncLib'

            // === CONFIGURACIÓN DE ORDEN DE EJECUCIÓN ===
            configureTaskOrdering(project)

            doLast {
                updateSystemStatus(project, "RB51")
                project.logger.lifecycle("✓ Smartbuild completed - Pure Gradle optimized build")
                project.logger.lifecycle("  - Compiled with: compileJava (Gradle native)")
                project.logger.lifecycle("  - Copied with: Gradle incremental Copy tasks")
                project.logger.lifecycle("  - System status updated to RB51")
            }
        }
        // Smartbuild alias: compile.complete
        project.tasks.register('compile.complete') {
            description = 'Alias de smartbuild - Build completo optimizado (Gradle puro)'
            group = 'etendo-build'
            dependsOn 'smartbuild'
        }

    }

    /**
     * Updates system status in AD_SYSTEM_INFO
     */
    private static void updateSystemStatus(Project project, String status) {
        project.logger.info("Updating system status to ${status}")
        try {
            def getProp = { key ->
                project.hasProperty(key) ? project.property(key) : project.ant.properties[key]
            }

            project.ant.sql(
                driver: getProp('bbdd.driver'),
                url: getProp('bbdd.owner.url'),
                userid: getProp('bbdd.user'),
                password: getProp('bbdd.password'),
                onerror: 'continue',
                autocommit: true
            ) {
                classpath {
                    pathElement(path: project.ant.references['project.class.path'])
                }
                transaction("UPDATE ad_system_info SET system_status='${status}'")
            }
        } catch (Exception e) {
            project.logger.warn("Could not update system status: ${e.message}")
        }
    }

    /**
     * Configura el orden de ejecución de las tareas dentro de smartbuild
     */
    private static void configureTaskOrdering(Project project) {
        // 1. Primero generar código (core.lib, wad.lib, trl.lib, sqlc, wad)
        def compileJavaTask = project.tasks.findByName('compileJava')
        if (compileJavaTask != null) {
            compileJavaTask.mustRunAfter 'gradleSqlc', 'gradleWadLib', 'gradleWad', 'gradleTrlSqlc'
        }

        // 2. Tareas que SI pueden correr en paralelo con compileJava (No tienen mustRunAfter compileJava)
        // - gradleCopyModuleResources
        // - gradleCopyModuleWeb
        // - gradleCopySkins
        // - gradleSqlc / gradleWad (ya son dependencias de compileJava pero corren antes)

        // 3. Tareas que dependen de la generación de archivos específicos
        def copyWebInfTask = project.tasks.findByName('gradleCopyWebInf')
        if (copyWebInfTask != null) {
            // Debe esperar a que WAD genere el web.xml
            copyWebInfTask.mustRunAfter 'gradleWad'
        }

        // 4. Agregador general
        def gradlePostsrcTask = project.tasks.findByName('gradlePostsrc')
        if (gradlePostsrcTask != null) {
            // Corre al final de las copias, pero puede ser en paralelo a la compilación
            gradlePostsrcTask.mustRunAfter 'gradleCopyModuleResources', 'gradleCopyModuleWeb', 'gradleCopySkins'
        }

        def gradleCopyReferenceDataTask = project.tasks.findByName('gradleCopyReferenceData')
        if (gradleCopyReferenceDataTask != null) {
            gradleCopyReferenceDataTask.mustRunAfter 'gradleCopyWebInf'
        }

        def gradleSyncLibTask = project.tasks.findByName('gradleSyncLib')
        if (gradleSyncLibTask != null) {
            gradleSyncLibTask.mustRunAfter 'gradleCopyWebInf'
        }
    }

    /**
     * Crea tarea optimizedBuild - Alias de smartbuild
     */
    private static void createOptimizedBuildTask(Project project) {
        project.tasks.register('optimizedBuild') {
            description = 'Alias de smartbuild - Build completo optimizado'
            group = 'etendo-optimized'

            // Simplemente depende de smartbuild
            dependsOn 'smartbuild'

            doLast {
                project.logger.lifecycle("✓ Optimized build completed using Gradle")
            }
        }
    }

    /**
     * Crea tarea optimizedDeploy - Deploy completo a Tomcat
     */
    private static void createOptimizedDeployTask(Project project) {
        project.tasks.register('optimizedDeploy') {
            description = 'Deploy completo a Tomcat usando tareas optimizadas de Gradle'
            group = 'etendo-optimized'

            dependsOn 'optimizedBuild'
            dependsOn 'gradleDeployToTomcat'

            doLast {
                project.logger.lifecycle("✓ Optimized deploy completed - application deployed to Tomcat")
            }
        }
    }

    /**
     * Log de tareas disponibles al cargar el proyecto
     */
    private static void logAvailableTasks(Project project) {
        project.gradle.projectsEvaluated {
            project.logger.info("")
            project.logger.info("====== GRADLE NATIVE BUILD LOADED ======")
            project.logger.info("Main tasks:")
            project.logger.info("  - smartbuild: Pure Gradle optimized build")
            project.logger.info("  - optimizedBuild: Alias for smartbuild")
            project.logger.info("  - optimizedDeploy: Full optimized deploy to Tomcat")
            project.logger.info("")
            project.logger.info("Legacy Ant tasks (available but not optimized):")
            project.logger.info("  - antSmartbuild: Original Ant smartbuild")
            project.logger.info("  - antCompile.complete: Original Ant compile.complete")
            project.logger.info("  - antInstall: Original Ant install.source")
            project.logger.info("=========================================")
            project.logger.info("")
        }
    }
}
