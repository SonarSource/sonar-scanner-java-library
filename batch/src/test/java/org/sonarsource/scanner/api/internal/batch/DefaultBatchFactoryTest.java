/*
 * SonarQube Scanner API - Batch
 * Copyright (C) 2011-2017 SonarSource SA
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
package org.sonarsource.scanner.api.internal.batch;

import java.util.Properties;
import org.junit.Test;
import org.sonar.batch.bootstrapper.Batch;
import org.sonarsource.scanner.api.internal.batch.BatchFactory;
import org.sonarsource.scanner.api.internal.batch.DefaultBatchFactory;

import static org.fest.assertions.Assertions.assertThat;

public class DefaultBatchFactoryTest {

  private Properties props = new Properties();
  private BatchFactory factory = new DefaultBatchFactory();

  @Test
  public void should_create_batch() {
    props.setProperty("sonar.projectBaseDir", "src/test/java_sample");
    props.setProperty("sonar.projectKey", "sample");
    props.setProperty("sonar.projectName", "Sample");
    props.setProperty("sonar.projectVersion", "1.0");
    props.setProperty("sonar.sources", "src");
    Batch batch = factory.createBatch(props, null, null);

    assertThat(batch).isNotNull();
  }
}
