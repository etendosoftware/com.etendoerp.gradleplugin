package com.etendoerp.gradle.jars.modules

import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.internal.impldep.org.apache.ivy.util.FileUtil
import org.gradle.testkit.runner.BuildResult
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.TempDir
import org.gradle.testkit.runner.TaskOutcome

import java.nio.file.Files
import java.util.zip.ZipFile

abstract class ModuleToJarSpecificationTest extends EtendoSpecification {

    final static String BASE_JAR_LOCATION     = "src/test/resources/jars"
    final static String ENVIRONMENTS_LOCATION = "src/test/resources/jars/environments"

    def setup() {
        def ant = new AntBuilder()

        def baseDir = new File("${ENVIRONMENTS_LOCATION}/moduleToJarEnvironment")
        FileUtils.copyDirectory(baseDir, getProjectDir())

        //Override the default build file
        def buildJarFile = new File("${BASE_JAR_LOCATION}/build.xml")
        ant.copy(file: buildJarFile, todir: getProjectDir(), overwrite: true)
    }

    String moduleToPath(String module) {
        return module.replace(".","/")
    }

    /**
     * Get the files from the generated file.
     * Each file is converted to a path format.
     * Ex: com.test.CustomClass -> com/test/CustomClass.class
     * @param generated
     * @return
     */
    String[] getFilesFromGenerated(File generated) {
        def files = []
        generated.eachLine {
            files.add("${moduleToPath(it)}.class")
        }
        return files
    }

    String dummyJavaClassNested(String packageName, String className, String methodName, List<String> nestedClasses=null) {
        return """
        package ${packageName};
        
        public class $className {
            public String ${methodName}() {
                return "test method";
            }
            
            ${nestedClasses ? createMultipleJavaClasses(nestedClasses):""}
        }
        """
    }

    String createMultipleJavaClasses(List<String> javaClasses) {
        def classes = ""
        javaClasses.each {
            classes += createJavaClass(it) + "\n"
        }
        return classes
    }

    String createJavaClass(String className) {
        return """
            public class $className {
            } 
        """
    }

    void containsClassFiles(File jarFile, List classes) {
        def javaClassesInJar = getFilesFromJar([jarFile: jarFile, fileExtension: ".class", pathToIgnore: "META-INF/etendo"])

        Set jarClassesSet = javaClassesInJar.flatten() as Set
        Set moduleClassesSet = classes.flatten() as Set

        assert jarClassesSet == moduleClassesSet

    }

    void containsJavaFiles(File sourceJarFile, List javaFiles) {
        def javaFilesInJar = getFilesFromJar([jarFile: sourceJarFile, fileExtension: ".java", pathToIgnore: "META-INF/etendo"])

        Set jarJavaFilesSet = javaFilesInJar.flatten() as Set
        Set moduleJavaFilesSet = javaFiles.flatten() as Set

        assert jarJavaFilesSet == moduleJavaFilesSet
    }

    String[] getFilesFromLocation(def map=[:]) {

        // Arguments
        def location            = map.location            ?: ""
        def fileExtension       = map.fileExtension       ?: ""
        def locationDir         = map.locationDir         ?: ""
        def replacePathLocation = map.replacePathLocation ?: true
        def ignoreDir           = map.ignoreDir           ?: true
        def pathToIgnore        = map.pathToIgnore        ?: ""
        def ignoreBuildDir      = map.ignoreBuildDir      ?: true

        def files = []
        def baseLocation = location

        if (locationDir) {
            baseLocation += locationDir.endsWith(File.separator) ? locationDir : "${locationDir}${File.separator}"
        }

        def auxBaseLocation = baseLocation.endsWith(File.separator) ? baseLocation : "${baseLocation}${File.separator}"

        String pathToReplace = map.pathToReplace ?: baseLocation

        def filesLocation = new File(baseLocation as String)

        filesLocation.eachFileRecurse {
            if (ignoreDir && it.isDirectory()) {
                return
            }

            if (pathToIgnore && it.absolutePath.contains(pathToIgnore as String)) {
                return
            }

            // Ignore build directory
            if (ignoreBuildDir && it.absolutePath.contains("${auxBaseLocation}build/")) {
                return
            }

            if (it.name.endsWith(fileExtension as String)) {
                def fileLocation = it.absolutePath
                if (replacePathLocation) {
                    fileLocation = fileLocation.replace(pathToReplace,"")
                }
                files.add(fileLocation)
            }
        }
        return files
    }

    String[] getFilesFromModule(def map=[:]) {
        def module = map.module
        map.location = "${getProjectDir().absolutePath}/modules/$module/"
        getFilesFromLocation(map)
    }

    String[] getFilesFromJar(def map = [:]) {

        File jarFile         = map.jarFile
        String fileExtension = map.fileExtension ?: ""
        String pathToIgnore  = map.pathToIgnore  ?: ""
        String pathToSearch  = map.pathToSearch  ?: ""
        Boolean ignoreDir    = map.ignoreDir     ?: true
        String ignoreMatch   = map.ignorematch   ?: ""

        def files = []
        new ZipFile(jarFile).entries().each {
            // continue
            if (ignoreDir && it.isDirectory()) {
                return
            }

            if (pathToSearch && !it.name.contains(pathToSearch)) {
                return
            }

            if (pathToIgnore && it.name.contains(pathToIgnore)) {
                return
            }

            if (ignoreMatch && it.name.matches(ignoreMatch)) {
                return
            }

            if (it.name.endsWith(fileExtension)) {
                files.add(it.name)
            }
        }
        return files
    }

    void createJavaFilesWithPackage(Map map=[:]) {
        String baseLocation   = map.baseLocation
        String module         = map.module
        List<String> packages = map.packages as List<String>

        packages.each {
            def finalPackage = module
            if (it) {
                finalPackage += ".${it}"
            }

            def finalLocation = "${baseLocation}/${moduleToPath(finalPackage)}"

            createJavaFiles([location: finalLocation, module: finalPackage, javaClasses: map.javaClasses, nestedClasses: []])
        }

    }

    void createJavaFiles(Map map=[:]) {
        String location            = map.location
        String module              = map.module
        List<String> javaClasses   = map.javaClasses as List<String>
        List<String> nestedClasses = map.nestedClasses as List<String>

        File createdLocation = new File(location)
        if (!createdLocation.exists()) {
            createdLocation.mkdirs()
        }

        javaClasses.each {javaClassName ->
            def javaClass = new File("${createdLocation.absolutePath}/${javaClassName}.java")
            javaClass.createNewFile()
            javaClass << dummyJavaClassNested(module, javaClassName,"customClassMethod", nestedClasses)
        }
    }

    def getListOfClasses(String module, List<String> customClasses, List<String> nestedClasses) {
        def list = []
        def modulePath = moduleToPath(module)
        for (String customClass : customClasses) {
            list.add("${modulePath}/${customClass}.class")
            for (String nested : nestedClasses) {
                def nestedClass = "${customClass}\$${nested}"
                list.add("${modulePath}/${nestedClass}.class")
            }
        }
        return list
    }

    def getListOfJavaFilesWithPackage(Map map=[:]) {
        def module            = map.module
        List<String> packages = map.packages as List<String>

        def list = []
        packages.each {
            def finalPackage = module
            if (it) {
                finalPackage += ".${it}"
            }
            list.addAll(getListOfJavaFiles([module: finalPackage, javaFiles: map.javaFiles]))
        }
        return list
    }

    def getListOfJavaFiles(Map map=[:]) {
        def module = map.module
        List<String> javaFiles = map.javaFiles as List<String>

        def list = []
        def modulePath = moduleToPath(module as String)

        for (String javaFile : javaFiles) {
            list.add("${modulePath}/${javaFile}.java")
        }
        return list
    }

    void validateClassFiles(String module, List<String> customClasses, List<String> nestedClasses) {
        for (String customClass : customClasses) {
            assert new File("${getProjectDir().absolutePath}/build/classes/${moduleToPath(module)}/${customClass}.class").exists()
            for (String nested : nestedClasses) {
                def nestedClass = "${customClass}\$${nested}"
                assert new File("${getProjectDir().absolutePath}/build/classes/${moduleToPath(module)}/${nestedClass}.class").exists()
            }
        }
    }

}
