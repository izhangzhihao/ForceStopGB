lib/AndroidHiddenAPI.jar: \
            AndroidHiddenAPI/android/app/ActivityManager.java \
            AndroidHiddenAPI/android/app/ActivityThread.java \
            AndroidHiddenAPI/android/content/pm/PackageParser.java \
            AndroidHiddenAPI/android/os/Process.java \
            AndroidHiddenAPI/android/os/ServiceManager.java \
            AndroidHiddenAPI/android/os/UserHandle.java \
            AndroidHiddenAPI/android/os/SystemProperties.java \
            AndroidHiddenAPI/android/app/ActionBar.java \
            AndroidHiddenAPI/android/app/AppGlobals.java \
            AndroidHiddenAPI/android/content/pm/IPackageManager.java \
            AndroidHiddenAPI/android/app/INotificationManager.java \
            AndroidHiddenAPI/android/app/usage/IUsageStatsManager.java

	javac \
            AndroidHiddenAPI/android/app/*.java \
            AndroidHiddenAPI/android/content/pm/*.java \
            AndroidHiddenAPI/android/os/*.java \
            AndroidHiddenAPI/android/content/*.java \
            AndroidHiddenAPI/android/content/res/*.java \
            AndroidHiddenAPI/android/app/AppGlobals.java \
            AndroidHiddenAPI/android/content/pm/IPackageManager.java \
            AndroidHiddenAPI/android/app/INotificationManager.java \
            AndroidHiddenAPI/android/app/usage/*.java

	cd AndroidHiddenAPI; jar -cvf ../lib/AndroidHiddenAPI.jar \
            android/app/ActivityManager.class \
            android/app/ActivityThread*.class \
            android/app/IApplicationThread.class \
            android/content/pm/PackageParser*.class \
            android/os/Process.class \
            android/os/ServiceManager.class \
            android/os/SystemProperties.class \
            android/os/UserHandle.class \
            android/app/ActionBar.class \
            android/app/AppGlobals.class \
            android/content/pm/IPackageManager.class \
            android/app/INotificationManager*.class \
            android/app/usage/*UsageStatsManager*.class

clean:
	@rm -rf lib/AndroidHiddenAPI.jar
	@find AndroidHiddenAPI/ -name "*.class" -exec rm -fv {} \;
