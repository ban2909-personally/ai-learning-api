@org.springframework.modulith.ApplicationModule(
        displayName = "Organizations",
        allowedDependencies = {
                "identity::user-lookup",
                "platform::security",
                "sharedkernel::error"
        }
)
package com.ailearning.platform.organization;
