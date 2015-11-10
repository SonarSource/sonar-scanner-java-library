SonarQube Scanner API [![Build Status](https://travis-ci.org/SonarSource/sonar-runner.svg?branch=master)](https://travis-ci.org/SonarSource/sonar-runner)
=========================

Common library used by many Scanners (SQ Scanner, SQ Scanner for Maven, SQ Scanner for Gradle, SQ Scanner for Ant, ...) and by SonarLint. Used to programmatically run SQ analysis.

Issue Tracker:
http://jira.sonarsource.com/browse/SONARUNNER

Release:
sonar-runner-api need to be signed for use in SonarLint for Eclipse. So you need to pass following properties during perform:
mvn release:perform -Djarsigner.keystore=<path to keystore.jks> -Djarsigner.storepass=<password>
