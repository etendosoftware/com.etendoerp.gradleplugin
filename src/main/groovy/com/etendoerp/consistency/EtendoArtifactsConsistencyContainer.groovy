package com.etendoerp.consistency

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.connections.DatabaseConnection
import com.etendoerp.core.CoreMetadata
import com.etendoerp.core.CoreType
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.legacy.dependencies.container.DependencyType
import groovy.io.FileType
import groovy.sql.GroovyRowResult
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

/**
 * This class is used to perform the version consistency verification.
 * At first, the 'installedArtifacts' should be loaded from the database if exists, containing a map with
 * the installed modules.
 *
 * Then the 'local' artifacts should be loaded, with is respective installed version.
 * This will be used to perform the version consistency verification.
 *
 * A JAR artifact to be extracted should not contain a 'MINOR' version compared with the installed one.
 * And a module is consistent ONLY if the 'local' version is EQUAL with the one installed in the database.
 *
 */
class EtendoArtifactsConsistencyContainer {

    static final String CORE_MODULE = "org.openbravo"

    Project project
    EtendoPluginExtension extension

    CoreMetadata coreMetadata
    DatabaseConnection databaseConnection
    Map<String, ArtifactDependency> installedArtifacts
    ArtifactDependency installedCoreArtifact

    Map<String, EtendoArtifactsComparator> etendoJarModuleArtifactsComparator
    Map<String, EtendoArtifactsComparator> etendoZipModuleArtifactsComparator

    EtendoArtifactsComparator etendoCoreArtifactComparator

    Boolean etendoJarModulesConsistent
    static final String VALIDATION_ERROR_MESSAGE = "* Error validating the artifact"

    static final String JAR_MODULES_CONSISTENT_ERROR = "* The modules in JAR must not have inconsistencies between versions."

    Boolean etendoZipModulesConsistent
    static final String ZIP_MODULES_CONSISTENT_ERROR = "* The modules in SOURCES must not have inconsistencies between versions."

    Boolean etendoCoreArtifactConsistent
    static final String CORE_ARTIFACT_CONSISTENT_ERROR = "* The CORE artifact must not have inconsistencies between versions."

    static final String CONSISTENCY_ERROR_MESSAGE = "* The environment must not have inconsistencies between versions."

    static final String INCONSISTENCY_ERROR_MESSAGE = "*** Local artifacts compared with the installed ones differs on versions. Run with '--info' to obtain more information."

    boolean artifactsLoaded = false

    EtendoArtifactsConsistencyContainer(Project project, CoreMetadata coreMetadata) {
        this.project = project
        this.coreMetadata = coreMetadata
        this.extension = project.extensions.findByType(EtendoPluginExtension)
        this.installedArtifacts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
    }

    boolean loadInstalledArtifacts() {
        project.logger.info("* Starting loading installed modules for the version consistency verification.")

        try {
            this.databaseConnection = new DatabaseConnection(project)
            def validConnection = databaseConnection.loadDatabaseConnection()
            if (!validConnection) {
                project.logger.info("* The connection with the database could not be established. Skipping version consistency verification.")
                this.artifactsLoaded = false
                return this.artifactsLoaded
            }

            Map installedModules = getMapOfModules()

            if (!installedModules || installedModules.isEmpty()) {
                project.logger.info("* The installed modules could not be loaded. Skipping version consistency verification.")
                this.artifactsLoaded = false
                return this.artifactsLoaded
            }

            loadInstalledArtifactsMap(installedModules)

            this.artifactsLoaded = true
            return true
        } catch (Exception e) {
            project.logger.info("* WARNING: The installed modules could not be loaded. Skipping version consistency verification.")
            project.logger.info("* MESSAGE: ${e.message}")
            this.artifactsLoaded = false
            return false
        }
    }

    void loadInstalledArtifactsMap(Map<String, GroovyRowResult> modulesMap) {
        this.installedArtifacts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
        for (def entry in modulesMap) {
            String javaPackage = entry.key
            GroovyRowResult rowResult = entry.value
            String version = rowResult.version as String

            ArtifactDependency artifactDependency = new ArtifactDependency(project, javaPackage, version)
            artifactDependency.versionParser = version

            // Check if the module is the CORE
            if (javaPackage == CORE_MODULE) {
                this.installedCoreArtifact = artifactDependency
            } else {
                this.installedArtifacts.put(javaPackage, artifactDependency)
            }

        }
    }

    Map<String, GroovyRowResult> getMapOfModules() {
        Map<String, GroovyRowResult> map = new HashMap<>()

        if (!databaseConnection) {
            return map
        }

        String qry = "select * from ad_module"
        def rowResult
        try {
            rowResult = databaseConnection.executeSelectQuery(qry)
        } catch (Exception e) {
            project.logger.info("* WARNING: The modules from the database could not be loaded to perform the version consistency verification.")
            project.logger.info("* MESSAGE: ${e.message}")
        }

        if (rowResult) {
            for (GroovyRowResult row : rowResult) {
                def javaPackage = row.javapackage as String
                if (javaPackage) {
                    map.put(javaPackage, row)
                }
            }
        }

        return map
    }

    boolean isIgnoredArtifact(String moduleName) {
        def extension = project.extensions.findByType(EtendoPluginExtension)
        List ignoredArtifacts = extension.ignoredArtifacts
        return ignoredArtifacts.stream().anyMatch({
            return it.equalsIgnoreCase(moduleName)
        })
    }

    boolean validateArtifact(ArtifactDependency localArtifact) {
        boolean isValid = false
        String errorMsg = ""
        try {
            (isValid, errorMsg) = isValidArtifact(localArtifact)
        } catch (Exception e) {
            project.logger.error("* Error validating the artifact ${localArtifact?.moduleName}")
            project.logger.error("* ERROR: ${e.message}")
            return false
        }

        if (!isValid) {
            throw new ArtifactInconsistentException(errorMsg)
        }

        return isValid
    }

    def isValidArtifact(ArtifactDependency localArtifact) {
        String errorMsg = ""

        // The 'installedArtifacts' is not loaded or there is not installed modules.
        if (!this.installedArtifacts || this.installedArtifacts.isEmpty()) {
            return [true, errorMsg]
        }

        def moduleName = localArtifact.moduleName
        ArtifactDependency installedArtifact = null

        if (localArtifact.type == DependencyType.ETENDOCOREJAR) {
            installedArtifact = this.installedCoreArtifact
        } else if (localArtifact.type == DependencyType.ETENDOJARMODULE && this.installedArtifacts.containsKey(moduleName)) {
            installedArtifact = this.installedArtifacts.get(moduleName)
        }

        // The artifact is not installed
        if (!installedArtifact) {
            return [true, errorMsg]
        }

        EtendoArtifactsComparator comparator = new EtendoArtifactsComparator(project, localArtifact, installedArtifact)
        comparator.loadVersionStatus()

        // Fail on MINOR version
        if (comparator.versionStatus == VersionStatus.MINOR) {

            String warningMessage = "* The local version to update is '${comparator.versionStatus}' to the installed one. \n"

            if (isIgnoredArtifact(moduleName)) {
                String warningMsg = ""
                warningMsg += "******************** WARNING ON IGNORED MODULE ******************** \n"
                warningMsg += "${warningMessage}"
                warningMsg += "${comparator.getInfo()}"
                warningMsg += "*******************************************************************"
                project.logger.warn(warningMsg)
                return  [true, errorMsg]
            }

            errorMsg += "************************************************************ \n"
            errorMsg += "${VALIDATION_ERROR_MESSAGE} '${localArtifact.moduleName}'    \n"
            errorMsg += "${warningMessage}"
            errorMsg += "${comparator.getInfo()}"
            errorMsg += "------------------------------------------------------------ \n"
            errorMsg += "${EtendoPluginExtension.ignoredArtifactsMessage(moduleName)}"
            errorMsg += "************************************************************ \n"

            return [false, errorMsg]
        }
        return  [true, errorMsg]
    }

    /**
     * Load the 'local' artifacts (in sources or jar) with is respective installed version in the database (if exists)
     *
     * @param location - The location to search local modules (could be Sources or JARs)
     * @param dependencyType - The type of local dependency (zip or jar)
     * @return a Map between the module name and a EtendoArtifactComparator containing the 'local' module and the installed version if exists
     */
    Map<String, EtendoArtifactsComparator> loadLocalArtifactsComparator(String location, DependencyType dependencyType) {
        Map<String, EtendoArtifactsComparator> comparatorMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)

        File locationToSearch = new File(location)

        if (!locationToSearch.exists()) {
            project.logger.info("* The location to search local modules '${location}' does not exists.")
            return comparatorMap
        }

        List<File> localModules = new ArrayList<>()

        // Add the local modules
        locationToSearch.traverse(type: FileType.DIRECTORIES, maxDepth: 0) {
            localModules.add(it)
        }

        for (File localModuleLocation : localModules) {
            def (String moduleName, EtendoArtifactsComparator comparator) = loadComparator(localModuleLocation, dependencyType)

            if (moduleName && comparator) {
                comparatorMap.put(moduleName, comparator)
            }
        }

        return  comparatorMap
    }

    /**
     * Load a 'local' artifact from the location passed and tries to perform the version comparison.
     * @param artifactLocation
     * @param dependencyType
     * @return a Tuple between the module name and the EtendoArtifactMetadata containing the local module and the installed version if exists.
     */
    def loadComparator(File artifactLocation, DependencyType dependencyType) {
        EtendoArtifactsComparator comparator = null
        String moduleName = null

        if (!artifactLocation.exists()) {
            return [moduleName, comparator]
        }

        EtendoArtifactMetadata moduleMetadata = new EtendoArtifactMetadata(project, dependencyType)

        String group = null
        String name = null
        String version = null

        // Load the artifact information from the metadata file
        if (moduleMetadata.loadMetadataFile(artifactLocation.absolutePath)) {
            group = moduleMetadata.group
            name = moduleMetadata.name
            version = moduleMetadata.version
        }

        // Load the version from the AD_MODULE.xml file
        if (dependencyType == DependencyType.ETENDOCOREJAR || dependencyType == DependencyType.ETENDOCOREZIP) {
            moduleMetadata.loadMetadataUsingXML(artifactLocation.absolutePath)
            CoreMetadata coreMetadata = this.project.findProperty(CoreMetadata.CORE_METADATA_PROPERTY) as CoreMetadata
            if (coreMetadata) {
                group = group ?: coreMetadata.getCoreGroup()
                name = name ?: coreMetadata.getCoreName()
            }
            version = moduleMetadata.version
        }

        // return on artifact information not defined
        if (!group || !name || !version) {
            return [moduleName, comparator]
        }

        ArtifactDependency localModule = new ArtifactDependency(project, group, name, version)
        localModule.versionParser = version
        localModule.locationFile = new File(artifactLocation.absolutePath)

        localModule.type = dependencyType

        // Look if the module is installed
        moduleName = localModule.moduleName
        ArtifactDependency installedArtifact = null

        // Filter the Core artifact or modules artifacts
        if (dependencyType == DependencyType.ETENDOCOREJAR || dependencyType == DependencyType.ETENDOCOREZIP && this.installedCoreArtifact) {
            installedArtifact = this.installedCoreArtifact
        } else if (this.installedArtifacts && !this.installedArtifacts.isEmpty() && this.installedArtifacts.containsKey(moduleName)) {
            installedArtifact = this.installedArtifacts.get(moduleName)
        }

        comparator = new EtendoArtifactsComparator(project, localModule, installedArtifact)

        // Trigger the comparator to compare the versions
        comparator.loadVersionStatus()

        return [moduleName, comparator]
    }

    /**
     * Load the 'local' artifacts.
     * The artifacts could be:
     * In SOURCES (modules) dir.
     * In JARs (build/etendo/modules) dir.
     * The CORE in SOURCES or JAR.
     */
    void loadComparators() {
        try {
            // Load jar artifact comparator
            loadJarArtifactsComparator()

            // Load sources artifact comparator
            loadSourceArtifactsComparator()

            // Load core artifact comparator
            loadCoreArtifactComparator()
        } catch (Exception e) {
            project.logger.info("* Warning: The artifacts comparators could not be loaded to perform the artifacts consistency.")
            project.logger.info("* MESSAGE: ${e.message}")
        }
    }

    void loadJarArtifactsComparator() {
        final String location = project.buildDir.absolutePath + File.separator + "etendo" + File.separator + "modules"
        this.etendoJarModuleArtifactsComparator = loadLocalArtifactsComparator(location, DependencyType.ETENDOJARMODULE)
    }

    void loadSourceArtifactsComparator() {
        final String location = project.rootDir.absolutePath + File.separator + "modules"
        this.etendoZipModuleArtifactsComparator = loadLocalArtifactsComparator(location, DependencyType.ETENDOZIPMODULE)
    }

    /**
     * Load the core artifact depending on if is in SOURCES or JAR
     */
    void loadCoreArtifactComparator() {
        CoreType coreType = coreMetadata.coreType

        String location = null
        DependencyType dependencyType

        if (!coreType || coreType == CoreType.UNDEFINED) {
            project.logger.info("* The core comparator could not be loaded because the core is UNDEFINED.")
            return
        }

        // Default set to SOURCES
        location = project.rootDir.absolutePath
        dependencyType = DependencyType.ETENDOCOREZIP

        if (coreType == CoreType.JAR) {
            location = project.buildDir.absolutePath + File.separator + "etendo"
            dependencyType = DependencyType.ETENDOCOREJAR
        }

        if (location) {
            File locationFile = new File(location)
            def (String moduleName, EtendoArtifactsComparator comparator) = loadComparator(locationFile, dependencyType)
            if (comparator) {
                this.etendoCoreArtifactComparator = comparator
            }
        }
    }


    /**
     * Verifies that the 'local' module is consistent with the installed one (if exists)
     * @param artifactComparator
     * @return True if the version is consistent (EQUAL or UNDEFINED), false otherwise (if MINOR or MAJOR)
     */
    boolean verifyModuleComparatorConsistency(EtendoArtifactsComparator artifactComparator) {
        boolean isConsistent = true
        String infoMessage = ""

        // Log the information about the module
        infoMessage += "************* ARTIFACT STATUS ************* \n"
        infoMessage += "${artifactComparator.getInfo()}"

        VersionStatus status = artifactComparator.versionStatus

        // Check that the version is UNDEFINED (not installed) or EQUAL(installed with the same version)
        if (status != VersionStatus.EQUAL) {
            isConsistent = false
            infoMessage += "* The version status is inconsistent between the artifacts. \n"
        }
        if (status == VersionStatus.UNDEFINED) {
            infoMessage += "* The module is not installed. \n"
        }

        infoMessage += "******************************************* \n"
        project.logger.info("${infoMessage}")

        return isConsistent
    }

    /**
     * Verifies that all the modules passed are consistent
     * @param comparatorMap
     * @return True if all the modules are consistent, False otherwise.
     */
    boolean modulesConsistencyStatus(Map<String, EtendoArtifactsComparator> comparatorMap) {
        boolean allModulesConsistent = true

        for (def comparator in comparatorMap) {
            if (!verifyModuleComparatorConsistency(comparator.value)) {
                allModulesConsistent = false
            }
        }
        return allModulesConsistent
    }

    void sourceModulesConsistency() {
        if (!this.etendoZipModuleArtifactsComparator) {
            project.logger.info("* The SOURCE artifact consistency could not be ran because the comparator is not loaded.")
            return
        }

        project.logger.info("")
        project.logger.info("* Running SOURCES modules consistency verification.")
        boolean allModulesConsistent = modulesConsistencyStatus(this.etendoZipModuleArtifactsComparator)
        this.etendoZipModulesConsistent = allModulesConsistent

        if (!allModulesConsistent) {
            project.logger.warn("*** WARNING: The module in SOURCES contain inconsistencies between versions. *** \n${INCONSISTENCY_ERROR_MESSAGE}")
        }
    }

    void jarModulesConsistency() {
        if (!this.etendoJarModuleArtifactsComparator) {
            project.logger.info("* The JARs artifact consistency could not be ran because the comparator is not loaded.")
            return
        }

        project.logger.info("")
        project.logger.info("* Running JARs modules consistency verification.")
        boolean allModulesConsistent = modulesConsistencyStatus(this.etendoJarModuleArtifactsComparator)

        this.etendoJarModulesConsistent = allModulesConsistent

        if (!allModulesConsistent) {
            project.logger.warn("*** WARNING: The modules in JARs contain inconsistencies between versions. *** \n${INCONSISTENCY_ERROR_MESSAGE}")
        }
    }

    void coreArtifactConsistency() {
        if (!this.etendoCoreArtifactComparator) {
            project.logger.info("* The CORE artifact consistency could not be ran because the core comparator is not loaded.")
            return
        }
        project.logger.info("")
        project.logger.info("* Running CORE consistency verification.")

        def isCoreConsistent = verifyModuleComparatorConsistency(this.etendoCoreArtifactComparator)
        this.etendoCoreArtifactConsistent = isCoreConsistent

        if (!isCoreConsistent) {
            project.getLogger().warn("*** WARNING: The CORE artifact contain inconsistencies between versions. *** \n${INCONSISTENCY_ERROR_MESSAGE}")
        }
    }

    /**
     * This method load the comparators and run
     * the artifact consistency verification for the
     * CORE, JAR and SOURCES artifacts.
     */
    void runArtifactConsistency() {
        loadComparators()
        try {
            // Verify Core consistency
            coreArtifactConsistency()

            // Verify JARs modules consistency
            jarModulesConsistency()

            // Verify SOURCE modules consistency
            sourceModulesConsistency()
        } catch (ArtifactInconsistentException ae) {
          throw ae
        } catch (Exception e) {
            project.logger.info("* WARNING: The artifacts consistency verification could not be executed.")
            project.logger.info("* MESSAGE: ${e.message}")
        }
    }

    void verifyConsistency(LogLevel logLevel) {
        boolean ignoreVerification = extension.ignoreConsistencyVerification
        boolean inconsistent = false
        String errorMsg = "${CONSISTENCY_ERROR_MESSAGE} \n"

        project.logger.info("")
        if (this.etendoCoreArtifactConsistent == Boolean.FALSE) {
            project.logger.log(logLevel, CORE_ARTIFACT_CONSISTENT_ERROR)
            // Only throw when the core is in JAR
            if (coreMetadata.coreType == CoreType.JAR) {
                errorMsg += "${CORE_ARTIFACT_CONSISTENT_ERROR} \n"
                inconsistent = true
            }
        }

        if (this.etendoJarModulesConsistent == Boolean.FALSE) {
            project.logger.log(logLevel, JAR_MODULES_CONSISTENT_ERROR)
            errorMsg += "${JAR_MODULES_CONSISTENT_ERROR} \n"
            inconsistent = true
        }

        if (ignoreVerification) {
            project.logger.log(logLevel, "* WARNING - Ignoring versions consistency verification")
            return
        }

        if (!this.artifactsLoaded) {
            project.logger.info("********************* WARNING *************************")
            project.logger.info("* The installed modules are NOT loaded. Ignoring versions consistency verification.")
            project.logger.info("*******************************************************")
            return
        }

        if (inconsistent && logLevel == LogLevel.ERROR) {
            errorMsg += EtendoPluginExtension.ignoreConsistencyVerificationMessage()
            throw new ArtifactInconsistentException(errorMsg)
        }

    }

}

