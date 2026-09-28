@org.springframework.modulith.ApplicationModule(
        displayName = "Community",
        allowedDependencies = {
            "identity::access",
            "identity::contract",
            "sharedkernel::error",
            "platform::storage"
        })
package com.ailearning.platform.community;
