package com.etendoerp.dependencies

import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.legacy.utils.NexusUtils
import org.gradle.api.GradleException
import org.gradle.api.Project

class EtendoCoreDependencies {

    static final String DEPENDENCIES_LIST_COMPILATION = "dependenciesListCOMPILATION"
    static final String DEPENDENCIES_LIST_MODULES_CORE = "dependenciesListMODULESCORE"
    static final String MODULES_CORE_DEPENDENCIES_TO_VALIDATE = "modulesCoreDependenciesToValidate"
    static final String MODULES_CORE_DEPENDENCIES_TO_RESOLVE = "modulesCoreDependenciesToResolve"

    static void loadCoreCompilationDependencies(Project project) {
        // Listing jar files - scope: COMPILATION
        // Total artifacts      = 88
        // Resolved artifacts   = 88
        // Unresolved artifacts = 0

        def (nexusUser, nexusPassword) = NexusUtils.getCredentials(project)

        project.repositories {
            mavenCentral()
            maven {
                url = 'https://repo.futit.cloud/repository/etendo-public-jars'
                credentials {
                    username = "${nexusUser}"
                    password = "${nexusPassword}"
                }
            }
        }

        // Listing only resolved artifacts.
        def dependenciesList = loadDependenciesFromFile(project)
        project.dependencies {
            dependenciesList.each { dependency ->
                implementation (dependency) { transitive = false }
            }
        }
    }

    static List<String> loadDependenciesFromFile(Project project) {
        def dependenciesList = loadArtifactsList(project, DEPENDENCIES_LIST_COMPILATION)

        if (!dependenciesList) {
            project.logger.error("Artifacts list is empty.")
        }

        return dependenciesList
    }

    static List<String> loadModulesCoreDependenciesFromFile(Project project) {
        return loadArtifactsList(project, DEPENDENCIES_LIST_MODULES_CORE)
    }

    static Map loadArtifactsLists(Project project) {
        File depsFile = project.file('artifacts.list.COMPILATION.gradle')
        if (!depsFile.exists()) {
            project.logger.error("The file ${depsFile.path} does not exist.")
            return [:]
        }

        Binding binding = new Binding()
        def extMap = [:]
        binding.setVariable 'ext', { Closure closure ->
            closure.delegate = extMap
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            closure()
        }

        GroovyShell shell = new GroovyShell(binding)
        shell.evaluate(depsFile)

        return extMap
    }

    static List<String> loadArtifactsList(Project project, String listName) {
        def extMap = loadArtifactsLists(project)
        def dependenciesList = extMap[listName] ?: []

        if (!(dependenciesList instanceof List)) {
            throw new GradleException("The '${listName}' property in artifacts.list.COMPILATION.gradle must be a List<String>.")
        }

        return dependenciesList as List<String>
    }

    static void loadModulesCoreDependencies(Project project) {
        def modulesCoreDependencies = loadModulesCoreDependenciesFromFile(project)
        if (!modulesCoreDependencies) {
            project.ext.set(MODULES_CORE_DEPENDENCIES_TO_VALIDATE, [] as Set)
            project.ext.set(MODULES_CORE_DEPENDENCIES_TO_RESOLVE, [] as List)
            return
        }

        def compilationDependencies = loadDependenciesFromFile(project)
        validateNoDuplicatesWithCompilation(project, modulesCoreDependencies, compilationDependencies)

        Set<String> dependenciesToValidate = new TreeSet<>(String.CASE_INSENSITIVE_ORDER)
        List<String> dependenciesToResolve = []

        project.dependencies {
            modulesCoreDependencies.each { String dependency ->
                validateModulesCoreDependencyNotation(dependency)

                String moduleName = getModuleName(dependency)
                if (isSourceModuleAvailable(project, moduleName)) {
                    project.logger.warn("Module '${moduleName}' is declared in ${DEPENDENCIES_LIST_MODULES_CORE} but exists in modules_core. Using source module and skipping JAR module dependency '${dependency}'.")
                    return
                }

                dependenciesToValidate.add(moduleName)
                dependenciesToResolve.add(dependency)
                implementation(dependency) { transitive = true }
            }
        }

        project.ext.set(MODULES_CORE_DEPENDENCIES_TO_VALIDATE, dependenciesToValidate)
        project.ext.set(MODULES_CORE_DEPENDENCIES_TO_RESOLVE, dependenciesToResolve)
    }

    static void validateNoDuplicatesWithCompilation(Project project, List<String> modulesCoreDependencies, List<String> compilationDependencies) {
        Set<String> compilation = new TreeSet<>(String.CASE_INSENSITIVE_ORDER)
        compilationDependencies.each { compilation.add(normalizeDependencyKey(it)) }

        modulesCoreDependencies.each { String dependency ->
            String dependencyKey = normalizeDependencyKey(dependency)
            if (compilation.contains(dependencyKey)) {
                throw new GradleException("Dependency '${dependency}' is declared in both ${DEPENDENCIES_LIST_MODULES_CORE} and ${DEPENDENCIES_LIST_COMPILATION}. Modules core dependencies must be removed from ${DEPENDENCIES_LIST_COMPILATION}.")
            }
        }
    }

    static void validateModulesCoreDependencyNotation(String dependency) {
        if (!dependency || dependency.isBlank()) {
            throw new GradleException("${DEPENDENCIES_LIST_MODULES_CORE} contains an empty dependency entry.")
        }
        if (dependency.contains("@")) {
            throw new GradleException("${DEPENDENCIES_LIST_MODULES_CORE} only supports JAR module dependencies. Remove the explicit extension from '${dependency}'.")
        }
        def parts = dependency.split(":")
        if (parts.length != 3 || parts.any { it == null || it.isBlank() }) {
            throw new GradleException("Invalid dependency '${dependency}' in ${DEPENDENCIES_LIST_MODULES_CORE}. Expected format: 'group:artifact:version'.")
        }
    }

    static String normalizeDependencyKey(String dependency) {
        def dependencyWithoutExtension = dependency.contains("@") ? dependency.substring(0, dependency.indexOf("@")) : dependency
        def parts = dependencyWithoutExtension.split(":")
        if (parts.length < 2) {
            return dependencyWithoutExtension
        }
        return "${parts[0]}:${parts[1]}"
    }

    static String getModuleName(String dependency) {
        def parts = dependency.split(":")
        return ArtifactDependency.buildModuleName(parts[0], parts[1])
    }

    static boolean isSourceModuleAvailable(Project project, String moduleName) {
        File modulesCoreDir = new File(project.rootDir, "modules_core")
        if (!modulesCoreDir.exists()) {
            return false
        }
        return modulesCoreDir.listFiles()?.any { it.isDirectory() && it.name.equalsIgnoreCase(moduleName) } ?: false
    }

    static void loadCoreTestDependencies(Project project) {
        // Listing jar files - scope: TEST
        // Total artifacts      = 29
        // Resolved artifacts   = 29
        // Unresolved artifacts = 0

        def (nexusUser, nexusPassword) = NexusUtils.getCredentials(project)

        project.repositories {
            mavenCentral()
            maven {
                url = 'https://repo.futit.cloud/repository/etendo-public-jars'
                credentials {
                    username = "${nexusUser}"
                    password = "${nexusPassword}"
                }
            }
        }

        // Listing only resolved artifacts.
        project.dependencies {
            testImplementation('org.hamcrest:hamcrest-all:1.3') { transitive = false }
            testImplementation('junit:junit:4.12') { transitive = false }
            testImplementation('org.jboss.arquillian.core:arquillian-core-impl-base:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.junit:arquillian-junit-container:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.test:arquillian-test-api:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.config:arquillian-config-api:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.container:arquillian-container-impl-base:1.4.1.Final') { transitive = false }
            testImplementation('com.etendoerp:jboss-ejb3-api:3.1.0') { transitive = false }
            testImplementation('org.jboss.arquillian.container:arquillian-weld-embedded:2.0.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.junit:arquillian-junit-core:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.container:arquillian-container-test-api:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.container:arquillian-container-test-spi:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.container:arquillian-container-spi:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.shrinkwrap:shrinkwrap-spi:1.2.6') { transitive = false }
            testImplementation('org.jboss.arquillian.config:arquillian-config-spi:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.testenricher:arquillian-testenricher-cdi:1.4.1.Final') { transitive = false }
            testImplementation('org.reflections:reflections:0.9.11') { transitive = false }
            testImplementation('org.jboss.arquillian.core:arquillian-core-spi:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.shrinkwrap.descriptors:shrinkwrap-descriptors-spi:2.0.0') { transitive = false }
            testImplementation('org.jboss.shrinkwrap.descriptors:shrinkwrap-descriptors-api-base:2.0.0') { transitive = false }
            testImplementation('org.jboss.arquillian.test:arquillian-test-spi:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.config:arquillian-config-impl-base:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.shrinkwrap:shrinkwrap-api:1.2.6') { transitive = false }
            testImplementation('org.jboss.arquillian.core:arquillian-core-api:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.shrinkwrap:shrinkwrap-impl-base:1.2.6') { transitive = false }
            testImplementation('org.jboss.arquillian.test:arquillian-test-impl-base:1.4.1.Final') { transitive = false }
            testImplementation('org.eu.ingwar.tools:arquillian-suite-extension:1.2.0') { transitive = false }
            testImplementation('org.jboss.spec.javax.el:jboss-el-api_3.0_spec:1.0.13.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.container:arquillian-container-test-impl-base:1.4.1.Final') { transitive = false }
        }
    }

}
