package com.etendoerp.legacy.wad

import com.etendoerp.legacy.ant.AntLoader
import org.gradle.api.Project

class WadGenerateLoader {

    static void load(Project project) {
        registerTask(project)

        project.afterEvaluate {
            // Ensure compileJava depends on WAD generation
            def compileJava = project.tasks.findByName('compileJava')
            def wadTaskRef = project.tasks.findByName('gradleWad')
            if (compileJava != null && wadTaskRef != null) {
                compileJava.dependsOn(wadTaskRef)
            }
        }
    }

    private static void registerTask(Project project) {
        project.tasks.register('gradleWad') {
            description = 'Generates UI windows and web.xml using WAD (Incremental)'
            group = 'etendo-wad'

            dependsOn 'prepareConfig'
            dependsOn 'gradleWadLib'

            // Detect mode
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"
            def outputDir = coreInSources ? project.file('build/javasqlc/wad/src') : project.file('build/etendo/build/javasqlc/wad/src')

            // --- INPUTS: Lo que hace que WAD cambie ---
            // 1. Plantillas y lógica de WAD
            inputs.files("${corePath}/src-wad/src").optional().withPropertyName('wadTemplates')
            
            // 2. Diccionario de Aplicación (Modelos XML)
            if (project.file("${corePath}/src-db/database/model").exists()) {
                inputs.files(project.fileTree("${corePath}/src-db/database/model") { include '**/*.xml' }).withPropertyName('coreModel')
            }
            if (project.file('modules').exists()) {
                inputs.files(project.fileTree('modules') { include '**/src-db/database/model/**/*.xml' }).withPropertyName('modulesModel')
            }
            if (project.file('modules_core').exists()) {
                inputs.files(project.fileTree('modules_core') { include '**/src-db/database/model/**/*.xml' }).withPropertyName('modulesCoreModel')
            }
            
            // 3. Configuración y Librerías
            def prepareConfig = project.tasks.named('prepareConfig')
            inputs.files(prepareConfig).withPropertyName('configFiles')
            inputs.files("${corePath}/src-wad/lib/openbravo-wad.jar").optional().withPropertyName('wadJar')
            inputs.files("${corePath}/src-core/lib/openbravo-core.jar").optional().withPropertyName('coreJar')

            // --- OUTPUTS: Lo que WAD genera ---
            outputs.dir('srcAD').withPropertyName('generatedADSource')
            outputs.file(new File(outputDir, 'web.xml')).withPropertyName('generatedWebXml')
            outputs.dir(outputDir).withPropertyName('generatedJava')
            
            // Enable caching
            outputs.cacheIf { true }

            doLast {
                def openbravoProperties = prepareConfig.get().outputs.files.find { it.name == 'Openbravo.properties' }
                
                // Asegurar que los directorios de entrada y salida existan
                project.file("${corePath}/src-wad/src").mkdirs()
                project.file('src-gen').mkdirs()
                project.file('srcAD/org/openbravo/erpWindows').mkdirs()
                project.file('srcAD/org/openbravo/erpCommon/reference').mkdirs()
                project.file('srcAD/org/openbravo/erpCommon/ad_actionButton').mkdirs()
                project.file('srcAD/org/openbravo/erpCommon/ad_callouts').mkdirs()
                outputDir.mkdirs()

                // Construct classpath natively to avoid StackOverflow with Ant references
                def wadClasspath = project.files()
                
                def wadJar = project.file("${corePath}/src-wad/lib/openbravo-wad.jar")
                if (wadJar.exists()) wadClasspath += project.files(wadJar)
                
                def coreJar = project.file("${corePath}/src-core/lib/openbravo-core.jar")
                if (coreJar.exists()) wadClasspath += project.files(coreJar)
                
                if (project.file("${corePath}/src-wad/src").exists()) wadClasspath += project.files("${corePath}/src-wad/src")
                if (project.file("${corePath}/src").exists()) wadClasspath += project.files("${corePath}/src")
                
                wadClasspath += project.configurations.findByName('compileClasspath')
                
                if (project.file('lib').exists()) {
                    wadClasspath += project.fileTree(dir: 'lib', include: '**/*.jar')
                }
                
                wadClasspath += project.files('config')

                // If core is in JAR, some jars might be in root lib or build/etendo/lib
                if (!coreInSources && project.file('build/etendo/lib').exists()) {
                    wadClasspath += project.fileTree(dir: 'build/etendo/lib', include: '**/*.jar')
                }

                def baseConfig = project.file('config').absolutePath
                
                // Read properties
                def getProp = { key, defVal ->
                    if (project.hasProperty(key)) return project.property(key)
                    // Check Ant properties
                    if (project.ant.properties.containsKey(key)) return project.ant.properties[key]
                    return defVal
                }

                def webUrl = getProp('web.url', 'http://localhost:8080/etendo/web')
                def attachPath = getProp('attach.path', project.file('attachments').absolutePath)

                project.javaexec {
                    executable = "${System.env.JAVA_HOME}/bin/java"
                    workingDir = project.file(corePath)
                    mainClass = 'org.openbravo.wad.Wad'
                    classpath = wadClasspath
                    maxHeapSize = '1536m' 
                    jvmArgs = ["-Djava.security.egd=file:///dev/urandom"]
                    
                    // Args matching src/build.xml 'wad' task
                    args = [
                        openbravoProperties.parentFile.absolutePath,
                        "%", // tab
                        project.file('srcAD/org/openbravo/erpWindows').absolutePath,
                        project.file('srcAD/org/openbravo/erpCommon').absolutePath,
                        outputDir.absolutePath,
                        "all", // webTab
                        project.file('srcAD/org/openbravo/erpCommon/ad_actionButton').absolutePath,
                        project.file('WebContent/src-loc').absolutePath, // baseDesign
                        "org/openbravo/erpWindows", // base.translate.structure
                        "dummyValueUnused",
                        "..",
                        attachPath,
                        webUrl,
                        project.file("${corePath}/src").absolutePath,
                        "true", // complete
                        "%", // module
                        "noquick", // quick
                        "false", // wad.generateAllClassic250Windows
                        "false"  // exclude.cdi
                    ]
                }
            }
        }
    }
}