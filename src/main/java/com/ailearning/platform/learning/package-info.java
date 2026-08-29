@org.springframework.modulith.ApplicationModule(
        displayName = "Learning",
        allowedDependencies = {"catalog::contract", "catalog::published-course", "catalog::learning-content", "identity::user-lookup", "sharedkernel::error"}
)
package com.ailearning.platform.learning;
