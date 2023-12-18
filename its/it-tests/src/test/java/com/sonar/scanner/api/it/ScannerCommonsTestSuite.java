/*
 * SonarQube Scanner Commons - ITs
 * Copyright (C) 2011-2023 SonarSource SA
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

import com.sonar.orchestrator.http.HttpMethod;
import com.sonar.orchestrator.junit4.OrchestratorRule;
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
public class ScannerCommonsTestSuite {
  private static final String SONAR_RUNTIME_VERSION = "sonar.runtimeVersion";

  @ClassRule
  public static final OrchestratorRule ORCHESTRATOR = OrchestratorRule.builderEnv()
    .setSonarVersion(getSystemPropertyOrFail(SONAR_RUNTIME_VERSION))
    .useDefaultAdminCredentialsForBuilds(true)
    // We need to use a plugin compatible with both SonarQube DEV & SonarQube version defined in .cirrus.yml (currently SQ 7.9)
    .addPlugin(MavenLocation.of("org.sonarsource.javascript", "sonar-javascript-plugin", "7.4.4.15624"))
    .build();

  private static String getSystemPropertyOrFail(String orchestratorPropertiesSource) {
    String propertyValue = System.getProperty(orchestratorPropertiesSource);
    if (StringUtils.isEmpty(propertyValue)) {
      fail(orchestratorPropertiesSource + " system property must be defined");
    }
    return propertyValue;
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
