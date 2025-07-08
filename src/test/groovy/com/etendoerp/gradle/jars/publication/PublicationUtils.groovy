package com.etendoerp.gradle.jars.publication

import com.etendoerp.publication.configuration.pom.PomConfigurationContainer
import groovy.json.JsonSlurper

import java.util.jar.JarFile
import java.util.zip.ZipFile

class PublicationUtils {

    static List<String> VALID_REPOSITORIES_TO_CLEAN = [
            "etendo-multiplepublish-test"
    ]

    static void cleanRepositoryModules(String repository) {
        if (!(repository in VALID_REPOSITORIES_TO_CLEAN)) {
            throw new IllegalArgumentException("The repository '${repository}' is not in the valid repositories list.")
        }
        HttpURLConnection uc
        try {
            def obj = getRepositoryModules(repository)
            for (def module :obj.items ){
                URL url2 = new URL("https://repo.futit.cloud/service/rest/v1/components/${module.id}")
                uc = url2.openConnection() as HttpURLConnection
                uc.setRequestMethod("DELETE")
                uc.setRequestProperty("Authorization", generateBasicAuth());
                uc.getInputStream()
            }
        } catch (Exception e ) {
            print(e)
        }
    }

    static Object getRepositoryModules(String repository, String token=null) {
        Object response = null
        HttpURLConnection uc
        try {
            def contToken = ""
            if (token) {
                contToken = "&continuationToken=${token}"
            }
            URL url = new URL( "https://repo.futit.cloud/service/rest/v1/components?repository=${repository}${contToken}")
            uc = url.openConnection() as HttpURLConnection
            uc.setRequestMethod("GET")
            String basicAuth = generateBasicAuth()
            uc.setRequestProperty("Authorization", basicAuth);
            def responseText = uc.getInputStream().getText()
            def jsonSlurper = new JsonSlurper()
            response = jsonSlurper.parseText(responseText)
            String continuationToken = response.continuationToken
            if (continuationToken && continuationToken != null) {
                response.items += getRepositoryModules(repository, continuationToken).items
            }
        } catch (Exception e ) {
            print(e)
        }
        return response
    }

    static String generateBasicAuth() {
        String userPass = System.getProperty("nexusUser") + ":" + System.getProperty("nexusPassword")
        return  "Basic " + new String(Base64.getEncoder().encode(userPass.getBytes()));
    }

    static File download(String address, File parent, String filename) {
        File dataFile = new File(parent, filename)

        def url = new URL(address)
        def uc = url.openConnection()
        String basicAuth = generateBasicAuth()
        uc.setRequestProperty("Authorization", basicAuth);
        dataFile.withOutputStream { out ->
            out << uc.getInputStream().readAllBytes()
        }
        return dataFile
    }

    static void repoContainsModules(String repo, Map<String, List<String>> modulesData) {
        def modules = getRepositoryModules(repo)
        Map<String, List<String>> modulesFromRepo = new TreeMap(String.CASE_INSENSITIVE_ORDER)
        for (def module : modules.items) {
            String moduleName = "${module.group}.${module.name}"
            if (!modulesFromRepo.containsKey(moduleName)) {
                modulesFromRepo.put(moduleName, [])
            }
            (modulesFromRepo.get(moduleName) as List).add(module.version)
        }

        // Check that the modules are in the repository
        for (def moduleToVerify : modulesData) {
            assert modulesFromRepo.containsKey(moduleToVerify.key) : "Module ${moduleToVerify.key} not found in repository ${repo}"
            List<String> versionsFromRepo = modulesFromRepo.get(moduleToVerify.key) as List<String>

            List<String> versionsToVerify = moduleToVerify.value
            versionsToVerify.each {
                assert versionsFromRepo.contains(it) : "Module ${moduleToVerify.key} with version ${it} not found in repository ${repo}. Existing versions are ${versionsFromRepo} and expected versions are ${versionsToVerify}."
            }
        }
    }

    static void correctModuleVersion(String repo, Map<String, List<String>> modulesData) {
        def modules = getRepositoryModules(repo)
        for (def module : modules.items) {
            String moduleName = "${module.group}.${module.name}"
            if (modulesData.containsKey(moduleName)) {
                assert module.version in modulesData.get(moduleName) : "Module ${moduleName} with version ${module.version} not found in repository ${repo}"
            }
        }
    }

    static void downloadAndValidatePomFile(String address, File parent, String filename, String moduleVersion, Map<String , Map> dependencies) {
        def pomFile = download(address, parent, filename)
        validatePomFile(pomFile, moduleVersion, dependencies)
    }

    static void validatePomFile(File pomFile, String moduleVersion, Map<String , Map> dependencies) {
        String pomFileText = pomFile.text
        def project = new XmlParser().parseText(pomFileText)
        assert project.version.text() == moduleVersion : "Expected version ${moduleVersion} but found ${project.version.text()} in POM file."

        def xmlDependencies = project.dependencies

        Map<String, Object> pomDependenciesMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)

        // Load the pom dependencies map from the POM xml
        xmlDependencies.dependency.each { dep ->
            def group = dep.groupId.text()
            def artifact = dep.artifactId.text()
            def version = dep.version.text()
            def map = [
                    "group" : group,
                    "artifact" : artifact,
                    "version" : version
            ]

            String name = "${group}.${artifact}"
            pomDependenciesMap.put(name, map)
        }

        dependencies.entrySet().each {
            String depName = it.key
            Map depValues = it.value
            assert pomDependenciesMap.containsKey(depName) : "Dependency ${depName} not found in POM file."
            def pomValues = pomDependenciesMap.get(depName)
            assert depValues.group == pomValues.group : "Expected group ${depValues.group} but found ${pomValues.group} in POM file for dependency ${depName}."
            assert depValues.artifact == pomValues.artifact : "Expected artifact ${depValues.artifact} but found ${pomValues.artifact} in POM file for dependency ${depName}."
            assert depValues.version == pomValues.version : "Expected version ${depValues.version} but found ${pomValues.version} in POM file for dependency ${depName}."
        }
    }

    static void downloadAndValidateZipFile(String address, File parent, String filename, String moduleVersion, Map<String , Map> dependencies) {
        def zipFile = download(address, parent, filename)

        File buildFile = obtainFileFromZipType(zipFile, "build.gradle", parent, "tempzip")
        validateBuildGradleFile(buildFile, moduleVersion, dependencies)

        File adModuleFile = obtainFileFromZipType(zipFile, "AD_MODULE.xml", parent, "tempzip")
        validateAdModuleFile(adModuleFile, moduleVersion)
    }

    static void downloadAndValidateBuildDirFile(String address, File parent, String filename) {
        def zipFile = download(address, parent, filename)
        def files = getFilesFromZipType([file:zipFile, pathToSearch: 'build/classes'])
        assert !files.toList().isEmpty()
        assert files.each {
            it.contains("build/classes") && (it.contains('buildvalidation') || it.contains('modulescript'))
        }
    }

    static String[] getFilesFromZipType(def map = [:]) {

        File file            = map.file
        String fileExtension = map.fileExtension ?: ""
        String pathToIgnore  = map.pathToIgnore  ?: ""
        String pathToSearch  = map.pathToSearch  ?: ""
        Boolean ignoreDir    = map.ignoreDir     ?: true
        String ignoreMatch   = map.ignorematch   ?: ""

        def files = []
        new ZipFile(file).entries().each {
            // continue
            if ((ignoreDir && it.isDirectory())
                || (pathToSearch && !it.name.contains(pathToSearch))
                || (pathToIgnore && it.name.contains(pathToIgnore))
                || (ignoreMatch && it.name.matches(ignoreMatch))) {
                return
            }

            if (it.name.endsWith(fileExtension)) {
                files.add(it.name)
            }
        }
        return files
    }

    /**
     * Only has to validate the 'AD_MODULE.xml' file
     * @param address
     * @param parent
     * @param filename
     * @param moduleVersion
     */
    static void downloadAndValidateJarFile(String address, File parent, String filename, String moduleVersion) {
        def jarFile = download(address, parent, filename)
        File adModuleFile = obtainFileFromZipType(jarFile, "AD_MODULE.xml", parent, "tempjar")
        validateAdModuleFile(adModuleFile, moduleVersion)
    }

    static File obtainFileFromZipType(File zipTypeFile, String filePath, File outputFile, String destine) {
        File tempDir = new File("${outputFile.absolutePath}/${destine}")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        File data = new File(tempDir, filePath)
        if (!data.exists()) {
            data.createNewFile()
        }

        def zipFile = new ZipFile(zipTypeFile)

        for (def entry : zipFile.entries()) {
            if (!entry.isDirectory() && entry.name.contains(filePath)) {
                data.text = zipFile.getInputStream(entry).text
                break
            }
        }
        return data
    }

    static void validateBuildGradleFile(File buildGradleFile, String moduleVersion, Map<String , Map> dependencies) {
        String buildFileText = buildGradleFile.text

        // Validate version
        for (String line : buildFileText.lines()) {
            if (PomConfigurationContainer.isVersionLine(line)) {
                assert line.contains(moduleVersion)
                break
            }
        }

        // Validate dependencies
        dependencies.entrySet().each {
            String dep = "${it.value.group}:${it.value.artifact}:${it.value.version}"
            assert buildFileText.contains(dep)
        }
    }

    static void validateAdModuleFile(File adModuleFile, String moduleVersion) {
        String adModuleText = adModuleFile.text
        def data = new XmlParser().parseText(adModuleText)
        assert data.AD_MODULE.VERSION.text() == moduleVersion
    }

    static void validateModuleContents(String moduleName, String version, String expectedClassPath,
                                       String expectedSourcePath, String repository, String repositoryUrl,
                                       File tempBaseDir) {
        Map<String, List<String>> modulesData = new TreeMap(String.CASE_INSENSITIVE_ORDER)
        modulesData.put(moduleName, [version])
        repoContainsModules(repository, modulesData)

        println "✓ Module ${moduleName} ${version} validated successfully"

        validateModuleContentDetails(moduleName, version, expectedClassPath, expectedSourcePath,
                repository, repositoryUrl, tempBaseDir)
    }

    static void validateModuleContentDetails(String moduleName, String version, String expectedClassPath,
                                             String expectedSourcePath, String repository, String repositoryUrl,
                                             File tempBaseDir) {
        String baseUrl = "${repositoryUrl}${repository}"

        String groupPath = moduleName.replace('.', '/')
        String artifactId = moduleName.substring(moduleName.lastIndexOf('.') + 1)

        String jarUrl = "${baseUrl}/${groupPath}/${version}/${artifactId}-${version}.jar"
        String zipUrl = "${baseUrl}/${groupPath}/${version}/${artifactId}-${version}.zip"

        File tempDir = new File(tempBaseDir, "temp-validation-${artifactId}-${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            File jarFile = download(jarUrl, tempDir, "${artifactId}-${version}.jar")
            File zipFile = download(zipUrl, tempDir, "${artifactId}-${version}.zip")

            validateJarContainsClass(jarFile, expectedClassPath)
            validateZipContainsFile(zipFile, expectedSourcePath)


        } finally {
            if (tempDir.exists()) {
                tempDir.deleteDir()
            }
        }
    }

    static void validateJarContainsClass(File jarFile, String expectedClassPath) {
        assert jarFile.exists() : "Downloaded JAR file does not exist: ${jarFile.absolutePath}"

        JarFile jar = new JarFile(jarFile)
        try {
            def entries = jar.entries().toList()
            println "JAR contains ${entries.size()} entries"

            println "Sample JAR entries:"
            entries.take(10).each { entry ->
                println "  - ${entry.name}"
            }

            def foundEntry = entries.find { it.name == expectedClassPath }
            assert foundEntry : "Class ${expectedClassPath} not found in JAR. Available entries: ${entries.collect { it.name }.take(20)}"

            println "✓ Found expected class: ${expectedClassPath}"
        } finally {
            jar.close()
        }
    }

    static void validateZipContainsFile(File zipFile, String expectedFilePath) {
        assert zipFile.exists() : "Downloaded ZIP file does not exist: ${zipFile.absolutePath}"

        ZipFile zip = new ZipFile(zipFile)
        try {
            def entries = zip.entries().toList()
            println "ZIP contains ${entries.size()} entries"

            println "Sample ZIP entries:"
            entries.take(10).each { entry ->
                println "  - ${entry.name}"
            }

            def foundEntry = entries.find { it.name == expectedFilePath }
            assert foundEntry : "File ${expectedFilePath} not found in ZIP. Available entries: ${entries.collect { it.name }.take(20)}"

            println "✓ Found expected file: ${expectedFilePath}"
        } finally {
            zip.close()
        }
    }

}
