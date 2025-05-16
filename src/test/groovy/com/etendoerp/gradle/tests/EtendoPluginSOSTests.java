package com.etendoerp.gradle.tests;

import com.etendoerp.gradle.jars.consistency.CoreUpdateOldVersionTest;
import com.etendoerp.gradle.jars.consistency.compilationtasks.CompilationTasksConsistencyVerificationTest;
import com.etendoerp.gradle.jars.core.coreinjars.JarCoreCompilationTasksTest;
import com.etendoerp.gradle.jars.core.coreinjars.JarCoreModulesInstallTest;
import com.etendoerp.gradle.jars.core.coreinjars.JarCoreModulesUpdateTest;
import com.etendoerp.gradle.jars.publication.NexusPublicationTest;
import com.etendoerp.gradle.jars.publication.publishall.PublishAllTest;
import com.etendoerp.gradle.jars.resolution.coreconflicts.CoreConflictsTest;
import com.etendoerp.gradle.jars.resolution.modules.CoreTransitiveJarModulesResolutionTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        JarCoreCompilationTasksTest.class,
        CoreUpdateOldVersionTest.class,

        JarCoreModulesInstallTest.class,
        JarCoreModulesUpdateTest.class,

        NexusPublicationTest.class,
        PublishAllTest.class,

        CoreConflictsTest.class,
        CoreTransitiveJarModulesResolutionTest.class,

        CompilationTasksConsistencyVerificationTest.class
})
public class EtendoPluginSOSTests {

}