package com.etendoerp.gradle.tests;

import com.etendoerp.legacy.interactive.ProgressSuppressorSpec;
import com.etendoerp.legacy.interactive.utils.PropertyParserSpec;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        ProgressSuppressorSpec.class,
        PropertyParserSpec.class
})
public class EtendoPluginUnitTests {
}
