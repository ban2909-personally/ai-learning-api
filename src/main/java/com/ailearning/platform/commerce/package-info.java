@org.springframework.modulith.ApplicationModule(
        displayName = "Commerce",
        allowedDependencies = {
                "catalog::contract",
                "catalog::published-course",
                "identity::user-lookup",
                "learning::enrollment-access",
                "platform::security",
                "sharedkernel::error"
        }
)
package com.ailearning.platform.commerce;
