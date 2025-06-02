/*
 * SonarQube Scanner API - Batch
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
package org.sonarsource.scanner.api.internal.batch;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.sonar.batch.bootstrapper.Batch;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultBatchFactoryTest {

  private Map<String, String> props = new HashMap<>();
  private BatchFactory factory = new DefaultBatchFactory();

  @Test
  public void should_create_batch() {
    props.put("sonar.projectBaseDir", "src/test/java_sample");
    props.put("sonar.projectKey", "sample");
    props.put("sonar.projectName", "Sample");
    props.put("sonar.projectVersion", "1.0");
    props.put("sonar.sources", "src");
    Batch batch = factory.createBatch(props, (m, l) -> {
    });

    assertThat(batch).isNotNull();
  }
}
