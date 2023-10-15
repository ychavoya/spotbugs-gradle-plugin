/*
 * Copyright 2021 SpotBugs team
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.spotbugs.snom

import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class KotlinBuildScriptFunctionalTest extends BaseFunctionalTest {
    File buildFile

    def setup() {
        buildFile = new File(rootDir, 'build.gradle.kts')
        buildFile << """
import com.github.spotbugs.snom.assign
plugins {
  `java`
  id("com.github.spotbugs")
}

version = 1.0

repositories {
    mavenCentral()
}
        """
        File sourceDir = rootDir.toPath().resolve("src").resolve("main").resolve("java").toFile()
        sourceDir.mkdirs()
        File sourceFile = new File(sourceDir, "Foo.java")
        sourceFile << """
public class Foo {
    public static void main(String... args) {
        System.out.println("Hello, SpotBugs!");
    }
}
"""
    }

    @IgnoreIf({
        def current = System.getProperty('snom.test.functional.gradle', GradleVersion.current().version)
        return GradleVersion.version(current) < GradleVersion.version("8.2") })
    def "can set params to SpotBugsExtension"() {
        setup:
        buildFile << """
spotbugs {
    ignoreFailures = false
    showStackTraces = true
    showProgress = true
    effort = "DEFAULT"
    reportLevel = "DEFAULT"
    visitors = listOf("FindSqlInjection", "SwitchFallthrough")
    omitVisitors = listOf("FindNonShortCircuit")
    reportsDir = file("\$buildDir/spotbugs")
    includeFilter = file("include.xml")
    excludeFilter = file("exclude.xml")
    baselineFile = file("baseline.xml")
    onlyAnalyze = listOf("com.foobar.MyClass", "com.foobar.mypkg.*")
    maxHeapSize = "1g"
    extraArgs = listOf("-nested:false")
    jvmArgs = listOf("-Duser.language=ja")
}
"""
        new File(rootDir, "include.xml") << "<FindBugsFilter />"
        new File(rootDir, "exclude.xml") << "<FindBugsFilter />"
        new File(rootDir, "baseline.xml") << "<BugCollection />"

        when:
        def result = gradleRunner
                .withArguments('check')
                .build()

        then:
        result.task(":spotbugsMain").outcome == SUCCESS
    }

    def "can add plugins by spotbugsPlugins configuration"() {
        setup:
        buildFile << """
dependencies {
    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.11.0")
}
"""

        when:
        def result = gradleRunner
                .withArguments('check', "-Pcom.github.spotbugs.snom.javaexec-in-worker=false")
                .build()

        then:
        result.task(":spotbugsMain").outcome == SUCCESS
        result.output.contains("Applying com.h3xstream.findsecbugs.PredictableRandomDetector to Foo")
        !result.output.contains("Trying to add already registered factory")
    }

    def "can use the specified SpotBugs version"() {
        setup:
        buildFile << """
dependencies {
    spotbugs("com.github.spotbugs:spotbugs:4.0.0-beta4")
}"""
        when:
        BuildResult result = gradleRunner
                .withArguments(":spotbugsMain")
                .build()

        then:
        result.task(":classes").outcome == SUCCESS
        result.output.contains("SpotBugs 4.0.0-beta4") || result.output.contains("spotbugs-4.0.0-beta4.jar")
    }

    @IgnoreIf({
        def current = System.getProperty('snom.test.functional.gradle', GradleVersion.current().version)
        return GradleVersion.version(current) < GradleVersion.version("8.2") })
    def "can generate spotbugs.html in configured outputLocation"() {
        buildFile << """
tasks.spotbugsMain {
    reports.create("html") {
        required = true
        outputLocation = file("\$buildDir/reports/spotbugs.html")
        setStylesheet("fancy-hist.xsl")
    }
}
"""
        when:
        def result = gradleRunner
                .withArguments('check')
                .build()

        then:
        result.task(":spotbugsMain").outcome == SUCCESS
        File report = rootDir.toPath().resolve("build").resolve("reports").resolve("spotbugs.html").toFile()
        report.isFile()
    }

    def "can use toolVersion to get the SpotBugs version"() {
        setup:
        buildFile << """
dependencies {
    spotbugs("com.github.spotbugs:spotbugs:4.0.2")
    compileOnly("com.github.spotbugs:spotbugs-annotations:\${spotbugs.toolVersion.get()}")
}
"""
        when:
        BuildResult result = gradleRunner
                .withArguments(":spotbugsMain")
                .build()

        then:
        result.task(":spotbugsMain").outcome == SUCCESS
        result.output.contains("com.github.spotbugs:spotbugs-annotations:4.0.2")
    }
}
