package com.etendoerp.legacy.ant

import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.Delete
import org.gradle.api.file.RelativePath

/**
 * GradleCopyTasksLoader - Migración de tareas de copia de Ant a Gradle
 * 
 * Reemplaza las tareas:
 * - build.local.context
 * - postsrc
 * - copy.files
 * - build.web.folder
 * 
 * Copia archivos de configuración, librerías, recursos web y reference data
 * 
 * NOTA: Los nombres de las tareas tienen prefijo 'gradle' para evitar conflictos
 * con tareas existentes en JarCoreGenerator
 */
class GradleCopyTasksLoader {
    
    static final String CONTEXT_DIR = 'WebContent'
    static final String WEB_INF = 'WEB-INF'
    
    static void load(Project project) {
        
        /**
         * Task: etendoCopyModulesConfig
         * Copia configuración de módulos al directorio de contexto
         */
        project.tasks.register('etendoCopyModulesConfig', Copy) {
            group = 'etendo build'
            description = 'Copy module configuration files'
            
            def modulesDir = project.file('modules')
            def modulesCoreDir = project.file('modules_core')
            
            from(modulesDir) {
                include '*/config/**'
                exclude '*.template'
            }
            
            if (AntLoader.isCoreInSources(project) && modulesCoreDir.exists()) {
                from(modulesCoreDir) {
                    include '*/config/**'
                    exclude '*.template'
                }
            }
            
            into "${project.buildDir}/classes"
            
            // Remapear: modules/com.module/config/file -> config/file
            eachFile { fileCopyDetails ->
                def segments = fileCopyDetails.relativePath.segments
                if (segments.length > 2 && segments[1] == 'config') {
                    def newPath = segments.drop(2)
                    fileCopyDetails.relativePath = new RelativePath(true, 'config', *newPath)
                }
            }
            includeEmptyDirs = false
        }
        
        /**
         * Task: etendoCopyLibraries
         * Copia librerías al directorio WEB-INF/lib
         */
        project.tasks.register('etendoCopyLibraries', Copy) {
            group = 'etendo build'
            description = 'Copy runtime libraries to WEB-INF/lib'
            
            def libDir = "${CONTEXT_DIR}/${WEB_INF}/lib"
            
            doFirst {
                // Limpiar el directorio de librerías
                project.delete(libDir)
                project.file(libDir).mkdirs()
            }
            
            from project.configurations.runtimeClasspath
            into libDir
            
            // Excluir JARs que no deben ir en el webapp
            exclude 'openbravo-wad*.jar'
            exclude 'openbravo-trl*.jar'
            exclude '*.war'
        }
        
        /**
         * Task: etendoCopyModuleLibraries
         * Copia librerías de módulos al WEB-INF/lib
         */
        project.tasks.register('etendoCopyModuleLibraries', Copy) {
            group = 'etendo build'
            description = 'Copy module runtime libraries'
            
            def libDir = "${CONTEXT_DIR}/${WEB_INF}/lib"
            def modulesDir = project.file('modules')
            def modulesCoreDir = project.file('modules_core')
            
            from(modulesDir) {
                include '*/lib/runtime/**'
            }
            
            if (AntLoader.isCoreInSources(project) && modulesCoreDir.exists()) {
                from(modulesCoreDir) {
                    include '*/lib/runtime/**'
                }
            }
            
            into libDir
            
            // Flatten: modules/com.module/lib/runtime/file.jar -> file.jar
            eachFile { fileCopyDetails ->
                def name = fileCopyDetails.name
                fileCopyDetails.relativePath = new RelativePath(true, name)
            }
            includeEmptyDirs = false
        }
        
        /**
         * Task: etendoCopyReferenceData
         * Copia reference data de módulos al WEB-INF/referencedata
         */
        project.tasks.register('etendoCopyReferenceData', Copy) {
            group = 'etendo build'
            description = 'Copy reference data from modules'
            
            def refDataDir = "${CONTEXT_DIR}/${WEB_INF}/referencedata"
            def modulesDir = project.file('modules')
            def modulesCoreDir = project.file('modules_core')
            
            from(modulesDir) {
                include '*/referencedata/standard/*.xml'
                include '*/referencedata/accounts/COA.csv'
            }
            
            if (AntLoader.isCoreInSources(project) && modulesCoreDir.exists()) {
                from(modulesCoreDir) {
                    include '*/referencedata/standard/*.xml'
                    include '*/referencedata/accounts/COA.csv'
                }
            }
            
            into refDataDir
            
            // Remapear estructura
            eachFile { fileCopyDetails ->
                def segments = fileCopyDetails.relativePath.segments
                // modules/com.module/referencedata/standard/file.xml -> standard/com.module/file.xml
                if (segments.length >= 4) {
                    def module = segments[0]
                    def type = segments[2]  // standard o accounts
                    def file = segments[segments.length - 1]
                    fileCopyDetails.relativePath = new RelativePath(true, type, module, file)
                }
            }
            includeEmptyDirs = false
        }
        
        /**
         * Task: etendoCopyWebContent
         * Copia recursos web de módulos
         */
        project.tasks.register('etendoCopyWebContent', Copy) {
            group = 'etendo build'
            description = 'Copy web resources from modules'
            
            def webDir = "${CONTEXT_DIR}/web"
            def modulesDir = project.file('modules')
            def modulesCoreDir = project.file('modules_core')
            
            from(modulesDir) {
                include '*/web/*/**'
                exclude '*/web/*/skins/**'  // Skins se procesan por separado
            }
            
            if (AntLoader.isCoreInSources(project) && modulesCoreDir.exists()) {
                from(modulesCoreDir) {
                    include '*/web/*/**'
                    exclude '*/web/*/skins/**'
                }
            }
            
            into webDir
            
            // Remapear: modules/com.module/web/com.module/file -> com.module/file
            eachFile { fileCopyDetails ->
                def segments = fileCopyDetails.relativePath.segments
                if (segments.length >= 3 && segments[1] == 'web') {
                    def newPath = segments.drop(2)
                    fileCopyDetails.relativePath = new RelativePath(
                        fileCopyDetails.relativePath.isFile(),
                        *newPath
                    )
                }
            }
            includeEmptyDirs = false
        }
        
        /**
         * Task: etendoCopyDesignFiles
         * Copia archivos de diseño (XML, FO, jrxml, etc.)
         */
        project.tasks.register('etendoCopyDesignFiles', Copy) {
            group = 'etendo build'
            description = 'Copy design files to classes/design'
            
            def buildAD = project.file('build/javasqlc/srcAD')
            def designDir = "${project.buildDir}/classes/design"
            
            from(buildAD) {
                include '**/*.xml'
                include '**/*.fo'
                include '**/*.html'
                include '**/*.srpt'
                include '**/*.jrxml'
                include '**/*.jasper'
            }
            
            into designDir
        }
        
        /**
         * Task: etendoSyncToTomcat
         * Sincroniza el contexto al directorio de Tomcat
         * Equivalente a copy.files de Ant
         */
        project.tasks.register('etendoSyncToTomcat', Sync) {
            group = 'etendo build'
            description = 'Sync context to Tomcat webapp directory'
            
            // Solo ejecutar si mode.class está activo
            onlyIf {
                def jakartaBase = project.findProperty('jakarta.base') 
                    ?: System.getenv('CATALINA_BASE')
                    ?: System.getenv('CATALINA_HOME')
                def contextName = project.findProperty('context.name') ?: 'etendo'
                
                jakartaBase != null && project.file(jakartaBase).exists()
            }
            
            doFirst {
                project.logger.info("* Syncing to Tomcat...")
            }
            
            from CONTEXT_DIR
            
            def jakartaBase = project.findProperty('jakarta.base') 
                ?: System.getenv('CATALINA_BASE')
                ?: System.getenv('CATALINA_HOME')
            def contextName = project.findProperty('context.name') ?: 'etendo'
            
            if (jakartaBase) {
                into "${jakartaBase}/webapps/${contextName}"
                
                preserve {
                    include '**/WEB-INF/classes/**'
                    include '**/WEB-INF/lib/**'
                }
            }
        }
        
        /**
         * Task agregada: etendoCopyFiles
         * Ejecuta todas las tareas de copia
         */
        project.tasks.register('etendoCopyFiles') {
            group = 'etendo build'
            description = 'Execute all copy tasks'
            dependsOn 'etendoCopyModulesConfig'
            dependsOn 'etendoCopyLibraries'
            dependsOn 'etendoCopyModuleLibraries'
            dependsOn 'etendoCopyReferenceData'
            dependsOn 'etendoCopyWebContent'
            dependsOn 'etendoCopyDesignFiles'
        }
        
        /**
         * Task: etendoDeploy
         * Deploy completo incluyendo sync a Tomcat
         */
        project.tasks.register('etendoDeploy') {
            group = 'etendo build'
            description = 'Deploy to Tomcat (copy files + sync)'
            dependsOn 'etendoCopyFiles'
            finalizedBy 'etendoSyncToTomcat'
        }
    }
}
