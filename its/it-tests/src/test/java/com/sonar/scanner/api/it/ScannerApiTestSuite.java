/*
 * SonarQube Scanner API - ITs
 * Copyright (C) 2011-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.sonar.scanner.api.it;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.http.HttpMethod;
import com.sonar.orchestrator.locator.MavenLocation;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
    // The scanner api should still be compatible with previous LTS 6.7, and not the 7.9
    // at the time of writing, so the installed plugins should be compatible with
    // both 6.7 and 8.x. The latest releases of analysers drop the compatibility with
    // 6.7, that's why versions are hardcoded here.
    .addPlugin(MavenLocation.of("org.sonarsource.javascript", "sonar-javascript-plugin", "5.2.1.7778"))
    .build();

  private static String getSystemPropertyOrFail(String orchestratorPropertiesSource) {
    String propertyValue = System.getProperty(orchestratorPropertiesSource);
    if (StringUtils.isEmpty(propertyValue)) {
      fail(orchestratorPropertiesSource + " system property must be defined");
    }
    return propertyValue;
  }

  public static void resetData(Orchestrator orchestrator) {
    // We add one day to ensure that today's entries are deleted.
    Instant instant = Instant.now().plus(1, ChronoUnit.DAYS);

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
