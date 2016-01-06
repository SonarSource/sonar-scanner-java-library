/*
 * SonarQube Runner - API
 * Copyright (C) 2011-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonar.runner.api;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IssueTest {

  @Test
  public void increase_coverage() {
    Issue issue = Issue.builder()
      .setAssigneeLogin("assignee")
      .setAssigneeName("assigneeName")
      .setComponentKey("comp")
      .setEndLine(10)
      .setEndLineOffset(11)
      .setKey("key")
      .setMessage("msg")
      .setNew(true)
      .setResolution("resolution")
      .setRuleKey("ruleKey")
      .setRuleName("ruleName")
      .setSeverity("severity")
      .setStartLine(1)
      .setStartLineOffset(2)
      .setStatus("status")
      .build();

    assertThat(issue.getAssigneeLogin()).isEqualTo("assignee");
    assertThat(issue.getAssigneeName()).isEqualTo("assigneeName");
    assertThat(issue.getComponentKey()).isEqualTo("comp");
    assertThat(issue.getEndLine()).isEqualTo(10);
    assertThat(issue.getEndLineOffset()).isEqualTo(11);
    assertThat(issue.getKey()).isEqualTo("key");
    assertThat(issue.getMessage()).isEqualTo("msg");
    assertThat(issue.isNew()).isTrue();
    assertThat(issue.getResolution()).isEqualTo("resolution");
    assertThat(issue.getRuleKey()).isEqualTo("ruleKey");
    assertThat(issue.getRuleName()).isEqualTo("ruleName");
    assertThat(issue.getSeverity()).isEqualTo("severity");
    assertThat(issue.getStartLine()).isEqualTo(1);
    assertThat(issue.getStartLineOffset()).isEqualTo(2);
    assertThat(issue.getStatus()).isEqualTo("status");
  }

}
