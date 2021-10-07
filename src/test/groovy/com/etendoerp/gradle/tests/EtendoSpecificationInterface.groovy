package com.etendoerp.gradle.tests

import com.etendoerp.gradle.utils.DBCleanupMode

trait EtendoSpecificationTrait {
    DBCleanupMode cleanDatabase() {
        return DBCleanupMode.ALWAYS
    }
}
