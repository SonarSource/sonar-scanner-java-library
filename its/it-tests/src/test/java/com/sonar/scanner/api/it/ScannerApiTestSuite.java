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

import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.http.HttpMethod;
import com.sonar.orchestrator.junit4.OrchestratorRule;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import static org.assertj.core.api.Assertions.fail;

@RunWith(Suite.class)
@SuiteClasses({ProxyTest.class, SSLTest.class, PropertiesTest.class})
public class ScannerApiTestSuite {
  private static final String SONAR_RUNTIME_VERSION = "sonar.runtimeVersion";

  public static final String LATEST_RELEASE = "LATEST_RELEASE";
  @ClassRule
  public static final OrchestratorRule ORCHESTRATOR = OrchestratorRule.builderEnv()
    .setSonarVersion(getServerVersion())
    .setEdition(getServerVersion().equals(LATEST_RELEASE) ? Edition.COMMUNITY : Edition.DEVELOPER)
    .useDefaultAdminCredentialsForBuilds(true)
    .defaultForceAuthentication()
    .addBundledPluginToKeep("sonar-javascript")
    .build();

  private static String getServerVersion() {
    return System.getProperty(SONAR_RUNTIME_VERSION, LATEST_RELEASE);
  }

  public static void resetData(OrchestratorRule orchestrator) {
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
