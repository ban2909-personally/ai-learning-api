@org.springframework.modulith.ApplicationModule(
        displayName = "Catalog",
        allowedDependencies = {
            "sharedkernel::error",
            "sharedkernel::pagination",
            "identity::access",
            "identity::contract"
        })
package com.ailearning.platform.catalog;
