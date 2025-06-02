/*
 * SonarQube Scanner API - ITs
 * Copyright (C) 2011-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package com.sonar.scanner.api.it;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.http.HttpMethod;
import com.sonar.orchestrator.locator.MavenLocation;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.apache.commons.lang.StringUtils;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import static org.assertj.core.api.Assertions.fail;

@RunWith(Suite.class)
@SuiteClasses({ProxyTest.class, SSLTest.class, PropertiesTest.class})
public class ScannerApiTestSuite {
  private static final String SONAR_RUNTIME_VERSION = "sonar.runtimeVersion";

  @ClassRule
  public static final Orchestrator ORCHESTRATOR = Orchestrator.builderEnv()
    .setSonarVersion(getSystemPropertyOrFail(SONAR_RUNTIME_VERSION))
    .useDefaultAdminCredentialsForBuilds(true)
    // The scanner api should still be compatible with 7.9
    .addPlugin(MavenLocation.of("org.sonarsource.javascript", "sonar-javascript-plugin", "7.0.1.14561"))
    .build();

  private static String getSystemPropertyOrFail(String orchestratorPropertiesSource) {
    String propertyValue = System.getProperty(orchestratorPropertiesSource);
    if (StringUtils.isEmpty(propertyValue)) {
      fail(orchestratorPropertiesSource + " system property must be defined");
    }
    return propertyValue;
  }

  public static void resetData(Orchestrator orchestrator) {
    Instant instant = Instant.now();

    // The expected format is yyyy-MM-dd.
    String currentDateTime = DateTimeFormatter.ISO_LOCAL_DATE
      .withZone(ZoneId.of("UTC"))
      .format(instant);

    orchestrator.getServer()
      .newHttpCall("/api/projects/bulk_delete")
      .setAdminCredentials()
      .setMethod(HttpMethod.POST)
      .setParams("analyzedBefore", currentDateTime)
      .execute();
  }

}
