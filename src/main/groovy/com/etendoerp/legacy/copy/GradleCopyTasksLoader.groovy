package com.etendoerp.legacy.copy

import com.etendoerp.legacy.ant.AntLoader
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync

/**
 * Loader de tareas Copy optimizadas de Gradle que reemplazan las tareas de copia de Ant.
 */
class GradleCopyTasksLoader {

    static void load(Project project) {
        def getProperty = { String key, String defaultValue = null ->
            project.hasProperty(key) ? project.property(key) : defaultValue
        }

        createCopyResourcesTask(project)
        createCopyModuleResourcesTask(project)
        createBuildLocalContextTask(project)
        createCopyToTomcatTask(project, getProperty)
        createCopyCoreLiberTask(project, getProperty)

        project.afterEvaluate {
            def compileJavaTask = project.tasks.findByName('compileJava')
            if (compileJavaTask != null) {
                ['gradleCopyConfig', 'gradleCopyResources', 'gradleCopyModuleBuildResources'].each { 
                    def t = project.tasks.findByName(it)
                    if (t != null) compileJavaTask.dependsOn(t)
                }
            }

            // Dependencias de archivos de configuración
            def gradleCopyConfigTask = project.tasks.findByName('gradleCopyConfig')
            def gradleCopyWebInfTask = project.tasks.findByName('gradleCopyWebInf')
            def gradleSyncLibTask = project.tasks.findByName('gradleSyncLib')
            
            ['createOtherConfigProperties', 'prepareConfig'].each { genTaskName ->
                def genTask = project.tasks.findByName(genTaskName)
                if (genTask != null) {
                    if (gradleCopyConfigTask != null) gradleCopyConfigTask.dependsOn(genTask)
                    if (gradleCopyWebInfTask != null) gradleCopyWebInfTask.dependsOn(genTask)
                }
            }

            // gradleCopyWebInf depends on gradleWad and gradleSqlc to get web.xml and other generated files
            def gradleWadTask = project.tasks.findByName('gradleWad')
            def gradleSqlcTask = project.tasks.findByName('gradleSqlc')
            if (gradleCopyWebInfTask != null) {
                if (gradleWadTask != null) gradleCopyWebInfTask.dependsOn(gradleWadTask)
                if (gradleSqlcTask != null) gradleCopyWebInfTask.dependsOn(gradleSqlcTask)
            }

            // Ensure gradleSyncLib depends on JAR generation tasks
            def trlJarTask = project.tasks.findByName('gradleTrlJar')
            if (gradleSyncLibTask != null && trlJarTask != null) {
                gradleSyncLibTask.dependsOn(trlJarTask)
            }
        }
    }

    private static void createCopyResourcesTask(Project project) {
        project.tasks.register('gradleCopyResources', Copy) {
            description = 'Copia recursos al directorio build/classes'
            group = 'etendo-copy'
            
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"

            from(project.file("${corePath}/src")) {
                include '**/*.properties', '**/*.xslt', '**/*.hbm.xml', '**/*.ftl', '**/*.xml', '**/*.fo', '**/*.html', '**/*.srpt', '**/*.jrxml', '**/*.jasper'
            }
            into project.file('build/classes/java/main')
            duplicatesStrategy = 'include'
            includeEmptyDirs = false
            outputs.cacheIf { true }
        }

        project.tasks.register('gradleCopyDesign', Copy) {
            description = 'Copia archivos de diseño al directorio WebContent/src-loc/design'
            group = 'etendo-copy'
            
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"

            // Core design files
            if (project.file("${corePath}/src").exists()) {
                from(project.file("${corePath}/src")) {
                    include '**/*.xml', '**/*.fo', '**/*.html', '**/*.srpt', '**/*.jrxml', '**/*.jasper'
                }
            }
            
            // Modules design files
            ['modules', 'modules_core', "${corePath}/modules", "${corePath}/modules_core"].each { dirName ->
                def base = project.file(dirName)
                if (base.exists()) {
                    base.listFiles()?.each { modDir ->
                        if (modDir.isDirectory()) {
                            def modSrc = new File(modDir, 'src')
                            if (modSrc.exists()) {
                                from(modSrc) {
                                    include '**/*.xml', '**/*.fo', '**/*.html', '**/*.srpt', '**/*.jrxml', '**/*.jasper'
                                }
                            }
                        }
                    }
                }
            }
            
            into project.file('WebContent/src-loc/design')
            duplicatesStrategy = 'include'
            includeEmptyDirs = false
            outputs.cacheIf { true }
        }

        project.tasks.register('gradleCopyConfig', Copy) {
            description = 'Copia archivos de configuración específicos'
            group = 'etendo-copy'
            
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"

            // Use task outputs as inputs to avoid dependency warnings
            def prepareConfigTask = project.tasks.findByName('prepareConfig')
            if (prepareConfigTask != null) {
                from(prepareConfigTask) { include 'quartz.properties' }
            } else {
                from(project.file('config')) { include 'quartz.properties' }
            }

            from(project.file("${corePath}/src/org/openbravo/erpReports")) { include 'jasperreports.properties' }
            into project.file('build/classes/java/main')
            includeEmptyDirs = false
            outputs.cacheIf { true }
        }

        project.tasks.register('gradlePostsrc') {
            description = 'Ejecuta todas las tareas de copia de recursos'
            group = 'etendo-copy'
            dependsOn 'gradleCopyResources', 'gradleCopyDesign', 'gradleCopyConfig', 'gradleCopyModuleWeb', 'gradleCopySkins', 'gradleCopyModuleResources'
        }
    }

    private static void createCopyModuleResourcesTask(Project project) {
        project.tasks.register('gradleCopyModuleResources', Copy) {
            description = 'Copia recursos de módulos a WebContent/src-loc/design'
            group = 'etendo-copy'
            
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"

            ['modules', 'modules_core', "${corePath}/modules", "${corePath}/modules_core"].each { dirName ->
                def base = project.file(dirName)
                if (base.exists()) {
                    base.listFiles()?.each { modDir ->
                        if (modDir.isDirectory()) {
                            def srcDir = new File(modDir, 'src')
                            if (srcDir.exists()) {
                                from(srcDir) {
                                    include '**/*.xml', '**/*.fo', '**/*.html', '**/*.srpt', '**/*.jrxml', '**/*.jasper'
                                }
                            }
                        }
                    }
                }
            }
            into project.file('WebContent/src-loc/design')
            duplicatesStrategy = 'include'
            includeEmptyDirs = false
        }

        project.tasks.register('gradleCopyModuleBuildResources', Copy) {
            description = 'Copia recursos de módulos al directorio build/classes'
            group = 'etendo-copy'
            
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"

            ['modules', 'modules_core', "${corePath}/modules", "${corePath}/modules_core"].each { dirName ->
                def base = project.file(dirName)
                if (base.exists()) {
                    base.listFiles()?.each { modDir ->
                        if (modDir.isDirectory()) {
                            def srcDir = new File(modDir, 'src')
                            if (srcDir.exists()) {
                                from(srcDir) {
                                    include '**/*'
                                    exclude '**/*.java'
                                }
                            }
                        }
                    }
                }
            }
            into project.file('build/classes/java/main')
            duplicatesStrategy = 'include'
            includeEmptyDirs = false
        }
    }

    private static void createBuildLocalContextTask(Project project) {
        project.tasks.register('gradleBuildLocalContext', Copy) {
            description = 'Copia archivos al contexto local WebContent'
            group = 'etendo-copy'
            
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"
            
            if (project.file("${corePath}/src").exists()) {
                from(project.file("${corePath}/src")) { include 'index.jsp' }
            }
            into project.file('WebContent')
            includeEmptyDirs = false
        }

        project.tasks.register('gradleCopyWebInf', Copy) {
            description = 'Copia archivos a WebContent/WEB-INF'
            group = 'etendo-copy'
            
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"
            
            def wadTask = project.tasks.named('gradleWad')
            def prepareConfig = project.tasks.named('prepareConfig')
            
            dependsOn wadTask, prepareConfig

            // 1. Core Config
            from(wadTask) { include 'web.xml' }
            
            if (project.file('config').exists()) {
                from(project.file('config')) {
                    exclude '**/eclipse/**', '**/setup-properties**', '*.template', 'checksums', 'log4j2.xml'
                }
            } else if (project.file("${corePath}/config").exists()) {
                from(project.file("${corePath}/config")) {
                    exclude '**/eclipse/**', '**/setup-properties**', '*.template', 'checksums', 'log4j2.xml'
                }
            }
            
            // 2. Module Config (Directamente a WEB-INF)
            ['modules', 'modules_core', "${corePath}/modules"].each { dirName ->
                def base = project.file(dirName)
                if (base.exists()) {
                    base.listFiles()?.each { modDir ->
                        if (modDir.isDirectory()) {
                            def configDir = new File(modDir, 'config')
                            if (configDir.exists()) {
                                from(configDir) {
                                    exclude '**/*.template'
                                }
                            }
                        }
                    }
                }
            }

            into project.file("WebContent/WEB-INF")
            duplicatesStrategy = 'include'
            includeEmptyDirs = false
        }

        project.tasks.register('gradleCopyReferenceData', Copy) {
            description = 'Copia datos de referencia a WEB-INF/referencedata'
            group = 'etendo-copy'
            
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"

            // Core standard
            if (project.file("${corePath}/referencedata/standard").exists()) {
                from(project.file("${corePath}/referencedata/standard")) {
                    include '*.xml'
                    into 'standard/org.openbravo'
                }
            }
            
            // Modules referencedata
            ['modules', 'modules_core', "${corePath}/modules", "${corePath}/modules_core"].each { dirName ->
                def base = project.file(dirName)
                if (base.exists()) {
                    base.listFiles()?.each { modDir ->
                        if (modDir.isDirectory()) {
                            def refDataDir = new File(modDir, 'referencedata')
                            if (refDataDir.exists()) {
                                from(refDataDir) {
                                    include 'standard/*.xml', 'accounts/COA.csv'
                                    eachFile { details ->
                                        def parts = details.relativePath.segments
                                        if (parts.length >= 2) {
                                            def newParts = [parts[0], modDir.name] + parts[1..-1]
                                            details.relativePath = new org.gradle.api.file.RelativePath(true, newParts as String[])
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            into project.file("WebContent/WEB-INF/referencedata")
            duplicatesStrategy = 'include'
            includeEmptyDirs = false
        }

        project.tasks.register('gradleCopyModuleWeb', Copy) {
            description = 'Copia archivos web (core y módulos) a WebContent/web'
            group = 'etendo-copy'
            
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"

            // 1. Core Web
            if (project.file("${corePath}/web").exists()) {
                from(project.file("${corePath}/web")) {
                    exclude 'skins/**' // Only exclude root skins
                }
            }

            // 2. Modules Web
            ['modules', 'modules_core', "${corePath}/modules", "${corePath}/modules_core"].each { dirName ->
                def base = project.file(dirName)
                if (base.exists()) {
                    base.listFiles()?.each { modDir ->
                        if (modDir.isDirectory()) {
                            def webDir = new File(modDir, 'web')
                            if (webDir.exists()) {
                                from(webDir) {
                                    exclude 'skins/**' // Only exclude root skins
                                }
                            }
                        }
                    }
                }
            }
            into project.file("WebContent/web")
            duplicatesStrategy = 'include'
            includeEmptyDirs = false
        }

        project.tasks.register('gradleCopySkins', Copy) {
            description = 'Copia skins a ltr y rtl en WebContent/web/skins'
            group = 'etendo-copy'
            
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"

            // Core skins
            def coreSkinsDir = project.file("${corePath}/web/skins")
            if (coreSkinsDir.exists()) {
                from(coreSkinsDir) {
                    into "ltr"
                }
                from(coreSkinsDir) {
                    into "rtl"
                }
            }
            
            // Modules skins
            ['modules', 'modules_core', "${corePath}/modules", "${corePath}/modules_core"].each { dirName ->
                def base = project.file(dirName)
                if (base.exists()) {
                    base.listFiles()?.each { modDir ->
                        if (modDir.isDirectory()) {
                            // Case 1: web/skins (legacy or special)
                            def modSkinsDir = new File(modDir, 'web/skins')
                            if (modSkinsDir.exists()) {
                                from(modSkinsDir) {
                                    into "ltr"
                                }
                                from(modSkinsDir) {
                                    into "rtl"
                                }
                            }
                            // Case 2: web/[ModuleID]/skins (standard)
                            def modIdSkinsDir = new File(modDir, "web/${modDir.name}/skins")
                            if (modIdSkinsDir.exists()) {
                                from(modIdSkinsDir) {
                                    into "ltr/${modDir.name}"
                                }
                                from(modIdSkinsDir) {
                                    into "rtl/${modDir.name}"
                                }
                            }
                        }
                    }
                }
            }
            into project.file("WebContent/web/skins")
            duplicatesStrategy = 'include'
            includeEmptyDirs = false
        }

        project.tasks.register('gradleSyncLib', Sync) {
            description = 'Sincroniza JARs en WebContent/WEB-INF/lib'
            group = 'etendo-copy'
            
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"

            from project.configurations.findByName('runtimeClasspath') ?: project.configurations.findByName('compileClasspath')
            
            if (project.file('lib/runtime').exists()) {
                from(project.file('lib/runtime')) {
                    exclude 'openbravo-wad.jar', 'openbravo-trl.jar', '*.war'
                }
            }
            
            if (!coreInSources && project.file('build/etendo/lib').exists()) {
                from(project.file('build/etendo/lib')) {
                    exclude 'openbravo-wad.jar', 'openbravo-trl.jar', '*.war'
                }
            }

            if (project.file("${corePath}/src-db/database/lib").exists()) {
                from(project.file("${corePath}/src-db/database/lib")) {
                    exclude 'wstx-asl-3.0.2.jar'
                }
            }
            into project.file("WebContent/WEB-INF/lib")
            duplicatesStrategy = 'include'
            includeEmptyDirs = false
        }
    }

    private static void createCopyToTomcatTask(Project project, Closure getProperty) {
        project.tasks.register('gradleCopyToTomcat', Sync) {
            description = 'Sincroniza WebContent con Tomcat'
            group = 'etendo-deploy'
            def jakartaBase = getProperty('jakarta.base', 'tomcat')
            def contextName = getProperty('context.name', 'etendo')
            onlyIf { project.hasProperty('mode.class') && project.property('mode.class').toBoolean() }
            from project.file('WebContent')
            into project.file("${jakartaBase}/webapps/${contextName}")
            preserve { include '**/WEB-INF/classes/**', '**/WEB-INF/lib/**' }
            includeEmptyDirs = false
        }

        project.tasks.register('gradleCopyClassesToTomcat', Sync) {
            description = 'Sincroniza build/classes con Tomcat'
            group = 'etendo-deploy'
            def jakartaBase = getProperty('jakarta.base', 'tomcat')
            def contextName = getProperty('context.name', 'etendo')
            onlyIf { project.hasProperty('mode.class') && project.property('mode.class').toBoolean() }
            from project.file('build/classes/java/main')
            into project.file("${jakartaBase}/webapps/${contextName}/WEB-INF/classes")
            includeEmptyDirs = false
        }

        project.tasks.register('gradleDeployToTomcat') {
            description = 'Ejecuta todas las tareas de deploy a Tomcat'
            group = 'etendo-deploy'
            dependsOn 'gradleCopyToTomcat', 'gradleCopyClassesToTomcat'
        }
    }

    private static void createCopyCoreLiberTask(Project project, Closure getProperty) {
        project.tasks.register('gradleCopyCoreLib', Copy) {
            description = 'Copia openbravo-core.jar a Tomcat'
            group = 'etendo-deploy'
            def jakartaBase = getProperty('jakarta.base', 'tomcat')
            def contextName = getProperty('context.name', 'etendo')
            onlyIf { project.ant.properties['is.source.jar']?.toBoolean() && project.file('src-core/lib').exists() }
            from project.file('src-core/lib')
            include 'openbravo-core.jar'
            into project.file("${jakartaBase}/webapps/${contextName}/WEB-INF/lib")
            includeEmptyDirs = false
        }
    }
}