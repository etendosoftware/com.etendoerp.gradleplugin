package com.etendoerp.consistency

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.legacy.dependencies.container.DependencyType
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import groovy.sql.GroovyRowResult
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.logging.LogLevel
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import com.etendoerp.core.CoreType

class EtendoArtifactsConsistencyContainerSpec extends Specification {

    @TempDir
    Path tempDir

    def project

    def setup() {
        project = ProjectBuilder.builder().build()
    }

    EtendoArtifactsConsistencyContainer newContainer() {
        if (!project.extensions.findByType(EtendoPluginExtension)) {
            project.extensions.create("etendo", EtendoPluginExtension)
        }
        def coreMetadata = Stub(com.etendoerp.core.CoreMetadata)
        return new EtendoArtifactsConsistencyContainer(project, coreMetadata)
    }

    def "isIgnoredArtifact checks extension list"() {
        given:
        def container = newContainer()
        def extension = project.extensions.findByType(EtendoPluginExtension)
        extension.ignoredArtifacts = ["com.test.module"]

        expect:
        container.isIgnoredArtifact("com.test.module")
        !container.isIgnoredArtifact("com.other.module")
    }

    def "validateArtifact throws on minor version when not ignored"() {
        given:
        def container = newContainer()
        def local = new ArtifactDependency(project, "com.test", "m", "1.0.0")
        local.versionParser = "1.0.0"
        local.type = DependencyType.ETENDOJARMODULE

        def installed = new ArtifactDependency(project, "com.test", "m", "2.0.0")
        installed.versionParser = "2.0.0"

        container.installedArtifacts = [(local.moduleName): installed]

        when:
        container.validateArtifact(local)

        then:
        thrown(ArtifactInconsistentException)
    }

    def "validateArtifact allows minor version when ignored"() {
        given:
        def container = newContainer()
        def extension = project.extensions.findByType(EtendoPluginExtension)
        def local = new ArtifactDependency(project, "com.test", "m", "1.0.0")
        local.versionParser = "1.0.0"
        local.type = DependencyType.ETENDOJARMODULE
        extension.ignoredArtifacts = [local.moduleName]

        def installed = new ArtifactDependency(project, "com.test", "m", "2.0.0")
        installed.versionParser = "2.0.0"

        container.installedArtifacts = [(local.moduleName): installed]

        expect:
        container.validateArtifact(local)
    }

    def "loadInstalledArtifactsMap splits core and modules"() {
        given:
        def container = newContainer()
        def coreRow = new GroovyRowResult([javapackage: EtendoArtifactsConsistencyContainer.CORE_MODULE, version: "1.0.0"])
        def moduleRow = new GroovyRowResult([javapackage: "com.test.module", version: "2.0.0"])
        def map = [
                (EtendoArtifactsConsistencyContainer.CORE_MODULE): coreRow,
                "com.test.module": moduleRow
        ]

        when:
        container.loadInstalledArtifactsMap(map)

        then:
        container.installedCoreArtifact != null
        container.installedArtifacts.containsKey("com.test.module")
    }

    def "verifyModuleComparatorConsistency returns true only for EQUAL"() {
        given:
        def local = new ArtifactDependency(project, "com.test", "m", "1.0.0")
        local.versionParser = "1.0.0"
        def installed = new ArtifactDependency(project, "com.test", "m", "1.0.0")
        installed.versionParser = "1.0.0"
        def comparator = new EtendoArtifactsComparator(project, local, installed)
        comparator.loadVersionStatus()

        expect:
        newContainer().verifyModuleComparatorConsistency(comparator)

        when:
        installed.versionParser = "2.0.0"
        comparator.loadVersionStatus()

        then:
        !newContainer().verifyModuleComparatorConsistency(comparator)
    }

    def "loadInstalledArtifacts returns false when connection fails"() {
        given:
        def container = newContainer()
        def db = Mock(com.etendoerp.connections.DatabaseConnection)
        GroovyMock(com.etendoerp.connections.DatabaseConnection, global: true)
        new com.etendoerp.connections.DatabaseConnection(_ as org.gradle.api.Project) >> db
        db.loadDatabaseConnection() >> false

        expect:
        !container.loadInstalledArtifacts()
        !container.artifactsLoaded
    }

    def "loadInstalledArtifacts returns false when no modules found"() {
        given:
        def container = newContainer()
        def db = Mock(com.etendoerp.connections.DatabaseConnection)
        GroovyMock(com.etendoerp.connections.DatabaseConnection, global: true)
        new com.etendoerp.connections.DatabaseConnection(_ as org.gradle.api.Project) >> db
        db.loadDatabaseConnection() >> true
        container.metaClass.getMapOfModules = { -> [:] }

        expect:
        !container.loadInstalledArtifacts()
        !container.artifactsLoaded
    }

    def "getMapOfModules returns empty when no database connection"() {
        given:
        def container = newContainer()
        container.databaseConnection = null

        expect:
        container.getMapOfModules().isEmpty()
    }

    def "getMapOfModules maps rows by javapackage"() {
        given:
        def container = newContainer()
        def db = Mock(com.etendoerp.connections.DatabaseConnection)
        container.databaseConnection = db
        def row1 = new GroovyRowResult([javapackage: "com.test.mod", version: "1.0.0"])
        def row2 = new GroovyRowResult([javapackage: null, version: "2.0.0"])
        db.executeSelectQuery(_) >> [row1, row2]

        when:
        def map = container.getMapOfModules()

        then:
        map.size() == 1
        map.containsKey("com.test.mod")
    }

    def "loadLocalArtifactsComparator returns empty when location missing"() {
        given:
        def container = newContainer()

        expect:
        container.loadLocalArtifactsComparator(new File(tempDir.toFile(), "missing").absolutePath, DependencyType.ETENDOJARMODULE).isEmpty()
    }

    def "loadLocalArtifactsComparator loads comparators for subdirs"() {
        given:
        def container = newContainer()
        def root = tempDir.toFile()
        new File(root, "a").mkdirs()
        new File(root, "b").mkdirs()
        container.metaClass.loadComparator = { File f, DependencyType t ->
            def local = new ArtifactDependency(project, "com.test", f.name, "1.0.0")
            local.versionParser = "1.0.0"
            return [local.moduleName, new EtendoArtifactsComparator(project, local)]
        }

        when:
        def result = container.loadLocalArtifactsComparator(root.absolutePath, DependencyType.ETENDOJARMODULE)

        then:
        result.size() == 2
    }

    def "loadComparator returns comparator when metadata is available"() {
        given:
        def container = newContainer()
        GroovyMock(EtendoArtifactMetadata, global: true)
        def meta = Stub(EtendoArtifactMetadata) {
            loadMetadataFile(_) >> true
            getGroup() >> "com.test"
            getName() >> "mod"
            getVersion() >> "1.0.0"
        }
        new EtendoArtifactMetadata(_, _) >> meta
        def dir = new File(tempDir.toFile(), "mod")
        dir.mkdirs()

        when:
        def result = container.loadComparator(dir, DependencyType.ETENDOJARMODULE)

        then:
        result[0] == "com.test.mod"
        result[1] instanceof EtendoArtifactsComparator
    }

    def "loadComparator returns nulls when metadata missing"() {
        given:
        def container = newContainer()
        GroovyMock(EtendoArtifactMetadata, global: true)
        def meta = Stub(EtendoArtifactMetadata) {
            loadMetadataFile(_) >> false
        }
        new EtendoArtifactMetadata(_, _) >> meta
        def dir = new File(tempDir.toFile(), "mod2")
        dir.mkdirs()

        when:
        def result = container.loadComparator(dir, DependencyType.ETENDOJARMODULE)

        then:
        result[0] == null
        result[1] == null
    }

    def "loadComparators triggers jar, source and core loaders"() {
        given:
        def container = newContainer()
        def calls = []
        container.metaClass.loadJarArtifactsComparator = { calls << "jar" }
        container.metaClass.loadSourceArtifactsComparator = { calls << "source" }
        container.metaClass.loadCoreArtifactComparator = { calls << "core" }

        when:
        container.loadComparators()

        then:
        calls.containsAll(["jar", "source", "core"])
    }

    def "loadJarArtifactsComparator and loadSourceArtifactsComparator set maps"() {
        given:
        def container = newContainer()
        container.metaClass.loadLocalArtifactsComparator = { String loc, DependencyType t ->
            return [(t.name()): null]
        }

        when:
        container.loadJarArtifactsComparator()
        container.loadSourceArtifactsComparator()

        then:
        container.etendoJarModuleArtifactsComparator.containsKey("ETENDOJARMODULE")
        container.etendoZipModuleArtifactsComparator.containsKey("ETENDOZIPMODULE")
    }

    def "loadCoreArtifactComparator sets comparator for JAR core"() {
        given:
        def coreMetadata = Stub(com.etendoerp.core.CoreMetadata) {
            getCoreType() >> CoreType.JAR
        }
        def container = new EtendoArtifactsConsistencyContainer(project, coreMetadata)
        container.metaClass.loadComparator = { File f, DependencyType t ->
            def local = new ArtifactDependency(project, "com.test", "core", "1.0.0")
            local.versionParser = "1.0.0"
            return [local.moduleName, new EtendoArtifactsComparator(project, local)]
        }

        when:
        container.loadCoreArtifactComparator()

        then:
        container.etendoCoreArtifactComparator != null
    }

    def "modulesConsistencyStatus aggregates comparator results"() {
        given:
        def container = newContainer()
        container.metaClass.verifyModuleComparatorConsistency = { EtendoArtifactsComparator c ->
            return c.localModule?.name != "bad"
        }
        def ok = new EtendoArtifactsComparator(project, new ArtifactDependency(project, "com.test", "ok", "1.0.0"))
        def bad = new EtendoArtifactsComparator(project, new ArtifactDependency(project, "com.test", "bad", "1.0.0"))
        def map = ["ok": ok, "bad": bad]

        expect:
        !container.modulesConsistencyStatus(map)
    }

    def "sourceModulesConsistency and jarModulesConsistency update flags"() {
        given:
        def container = newContainer()
        container.etendoZipModuleArtifactsComparator = ["x": new EtendoArtifactsComparator(project, new ArtifactDependency(project, "c", "s", "1"))]
        container.etendoJarModuleArtifactsComparator = ["y": new EtendoArtifactsComparator(project, new ArtifactDependency(project, "c", "j", "1"))]
        container.metaClass.modulesConsistencyStatus = { Map m -> m.keySet().contains("x") }

        when:
        container.sourceModulesConsistency()
        container.jarModulesConsistency()

        then:
        container.etendoZipModulesConsistent == true
        container.etendoJarModulesConsistent == false
    }

    def "coreArtifactConsistency updates flag"() {
        given:
        def container = newContainer()
        container.etendoCoreArtifactComparator = new EtendoArtifactsComparator(project, new ArtifactDependency(project, "c", "core", "1"))
        container.metaClass.verifyModuleComparatorConsistency = { EtendoArtifactsComparator c -> false }

        when:
        container.coreArtifactConsistency()

        then:
        container.etendoCoreArtifactConsistent == false
    }

    def "runArtifactConsistency calls consistency checks"() {
        given:
        def container = newContainer()
        def calls = []
        container.metaClass.loadComparators = { calls << "load" }
        container.metaClass.coreArtifactConsistency = { calls << "core" }
        container.metaClass.jarModulesConsistency = { calls << "jar" }
        container.metaClass.sourceModulesConsistency = { calls << "source" }

        when:
        container.runArtifactConsistency()

        then:
        calls.containsAll(["load", "core", "jar", "source"])
    }

    def "verifyConsistency throws when inconsistent and not ignored"() {
        given:
        if (!project.extensions.findByType(EtendoPluginExtension)) {
            project.extensions.create("etendo", EtendoPluginExtension)
        }
        def coreMetadata = Stub(com.etendoerp.core.CoreMetadata) {
            getCoreType() >> CoreType.JAR
        }
        def container = new EtendoArtifactsConsistencyContainer(project, coreMetadata)
        def extension = project.extensions.findByType(EtendoPluginExtension)
        extension.ignoreConsistencyVerification = false
        container.artifactsLoaded = true
        container.etendoCoreArtifactConsistent = false
        container.etendoJarModulesConsistent = false

        when:
        container.verifyConsistency(LogLevel.ERROR)

        then:
        thrown(ArtifactInconsistentException)
    }

    def "verifyConsistency returns early when ignore flag or artifacts not loaded"() {
        given:
        def container = newContainer()
        def extension = project.extensions.findByType(EtendoPluginExtension)
        extension.ignoreConsistencyVerification = true
        container.artifactsLoaded = false

        expect:
        container.verifyConsistency(LogLevel.INFO) == null
    }
}
