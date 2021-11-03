package com.etendoerp.jars

import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.file.FileTree

class ExtractResourcesOfJars {

    public static String JAR_ETENDO_LOCATION = PathUtils.createPath(
            PublicationUtils.META_INF,
            PublicationUtils.ETENDO
    )

    public static String JAR_ETENDO_MODULE_LOCATION = PathUtils.createPath(
            PublicationUtils.META_INF,
            PublicationUtils.ETENDO,
            PublicationUtils.BASE_MODULE_DIR
    )

    /**
     * Extract all the resources of the JAR files which contains 'META-INF/etendo' directory
     * @param project
     */
    static void extractResources(Project project) {

        Map<String, FileTree> etendoJarFiles = [:]

        /**
         * Filter the Etendo Jar files
         */
        project.configurations.findByName(PublicationUtils.ETENDO_DEPENDENCY_CONTAINER).findResults {jarFile ->
            FileTree unzipJar = project.zipTree(jarFile)
            for (File file : unzipJar) {
                def filePath = file.absolutePath

                // Is a Etendo Jar
                if (filePath.contains(JAR_ETENDO_LOCATION)) {

                    // The jar is the Etendo core
                    println("jarfile name: ${jarFile.name}")
                    println("jarfile name: ${jarFile.absolutePath}")
                    if (jarFile.name.contains(JarCoreGenerator.ETENDO_CORE)) {
                        etendoJarFiles.put(JarCoreGenerator.ETENDO_CORE, unzipJar)
                        break
                    }

                    // The jar is a Etendo module
                    if (filePath.contains(JAR_ETENDO_MODULE_LOCATION)) {
                        // Obtains the module name
                        def moduleLoc = filePath.substring(filePath.lastIndexOf(JAR_ETENDO_MODULE_LOCATION)).replace(JAR_ETENDO_MODULE_LOCATION,"")
                        def moduleName = moduleLoc.split(File.separator)[0]

                        if (moduleName) {
                            etendoJarFiles.put(moduleName, unzipJar)
                            break
                        }
                    }
                }
            }
        }

        etendoJarFiles.each {
            String name = it.key
            FileTree files = it.value

            def metainfFilter = files.matching {
                include "${JAR_ETENDO_LOCATION}"
            }

            project.copy {
                from {
                    metainfFilter
                }
                into "${project.buildDir}/etendo"
                eachFile { f ->
                    f.path = f.path.replaceFirst("${JAR_ETENDO_LOCATION}", '')
                }
                includeEmptyDirs false
            }

            // The Jar is a module
            if (name != JarCoreGenerator.ETENDO_CORE) {
                def srcFilter = files.matching {
                    include '**/*'
                    exclude 'META-INF/**'
                    exclude '**/*.class'
                }

                project.copy {
                    from {
                        srcFilter
                    }
                    into "${project.buildDir}/etendo/modules/${name}/src"
                    includeEmptyDirs false
                }
            }
        }
    }

    /**
     * Copies the 'config' files located in the 'build/etendo/config' dir to the
     * root project. The copy is performed only if the 'config' dir
     * does not exists in the root project.
     * @param project
     */
    static void copyConfigFile(Project project) {
        def etendoConfigLocation = project.file("${project.buildDir}/etendo/config")
        def rootConfigLocation   = project.file("${project.rootDir}/config")

        if (etendoConfigLocation.exists() && !rootConfigLocation.exists()) {
            project.logger.info("Copying 'etendo/config' file to the root project.")
            project.copy {
                from(project.file("${project.buildDir}/etendo")) {
                    include("config/**")
                }
                into project.rootDir
            }
        }
    }
}

