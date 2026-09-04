@org.springframework.modulith.ApplicationModule(
        displayName = "Notifications",
        allowedDependencies = {"learning::events", "platform::security", "sharedkernel::error"}
)
package com.ailearning.platform.notification;
