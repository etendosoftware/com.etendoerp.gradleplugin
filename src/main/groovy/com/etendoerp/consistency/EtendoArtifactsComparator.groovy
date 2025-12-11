package com.etendoerp.consistency

import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.legacy.dependencies.container.DependencyType
import org.gradle.api.Project
import org.gradle.util.VersionNumber

import javax.annotation.Nullable

/**
 * Class used to contain a local module (in SOURCES o JAR)
 * and the representation of the installed one in the database (may not be installed)
 */
class EtendoArtifactsComparator {

    Project project

    // Module already installed in the database (could not be defined)
    ArtifactDependency installedModule

    // Module to be updated
    ArtifactDependency localModule

    // Contains the version status of the modules
    VersionStatus versionStatus

    int comparisonResult

    EtendoArtifactsComparator(Project project, ArtifactDependency localModule, @Nullable ArtifactDependency installedModule) {
        this.project = project
        this.localModule = localModule
        this.installedModule = installedModule
        this.versionStatus = VersionStatus.UNDEFINED
    }

    EtendoArtifactsComparator(Project project, ArtifactDependency localModule) {
        this.project = project
        this.localModule = localModule
        this.versionStatus = VersionStatus.UNDEFINED
    }

    /**
     * Compares the modules versions using the 'localModule' as the base.
     * The 'localModule' compare to 'installedModule' is:
     *  MAJOR - MINOR - EQUAL
     *
     *  Ex:
     *  localModule:1.0.1 compare to installedModule:1.0.0 is MAJOR (1)
     *  localModule:1.0.0 compare to installedModule:1.0.1 is MINOR (-1)
     *  localModule:1.0.0 compare to installedModule:1.0.0 is EQUAL (0)
     *
     */
    void loadVersionStatus() {
        if (!localModule || !installedModule || localModule.versionParser == null || installedModule.versionParser == null) {
            this.versionStatus = VersionStatus.UNDEFINED
            return
        }

        try {
            VersionNumber localModule = VersionNumber.parse(localModule.versionParser)
            VersionNumber installedModule = VersionNumber.parse(installedModule.versionParser)

            this.comparisonResult = localModule.compareTo(installedModule)
            this.versionStatus = VersionStatus.EQUAL
            if (this.comparisonResult > 0) {
                this.versionStatus = VersionStatus.MAJOR
            } else if (this.comparisonResult < 0)  {
                this.versionStatus = VersionStatus.MINOR
            }

        } catch (Exception e) {
            this.versionStatus = VersionStatus.UNDEFINED
            project.logger.error("* Error comparing artifact versions")
            project.logger.error("${getLocalModuleInfo()}")
            project.logger.error("${getInstalledModuleInfo()}")
            project.logger.error("* ERROR: ${e.message}")
        }
    }

    String getInfo() {
        String message = ""
        message += "${getLocalModuleInfo()}"
        message += "${getInstalledModuleInfo()}"
        message += "* Version status: The local version compared to the installed version is '${versionStatus}' \n"

        return message
    }

    String getLocalModuleInfo() {
        if (!this.localModule) {
            return "* Local module not defined. \n"
        }
        String type = getArtifactType()
        return "* Local ${type}: ${this.localModule.moduleName}:${this.localModule.versionParser} \n"
    }

    String getInstalledModuleInfo() {
        if (!this.installedModule) {
            return "* Installed module not defined. \n"
        }
        String type = getArtifactType()
        return "* Installed ${type}: ${this.installedModule.moduleName}:${this.installedModule.versionParser} \n"
    }

    String getArtifactType() {
        if (!this.localModule || !this.localModule.type) {
            return "artifact"
        }

        DependencyType type = localModule.type
        if (type == DependencyType.ETENDOCOREZIP || type == DependencyType.ETENDOCOREJAR) {
            return "CORE"
        }

        return "module"
    }

}
