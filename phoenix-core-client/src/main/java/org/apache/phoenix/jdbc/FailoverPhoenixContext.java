/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.jdbc;

import java.util.Properties;

/**
 * FailoverPhoenixContext holds the properties and HAGroup Info for a failover phoenix connection.
 */
public class FailoverPhoenixContext {

  private final Properties properties;
  private final HighAvailabilityGroup haGroup;
  private final HAURLInfo haurlInfo;

  FailoverPhoenixContext(Properties properties, HighAvailabilityGroup haGroup,
    HAURLInfo haurlInfo) {
    this.properties = properties;
    this.haGroup = haGroup;
    this.haurlInfo = haurlInfo;
  }

  public Properties getProperties() {
    return properties;
  }

  public HighAvailabilityGroup getHAGroup() {
    return haGroup;
  }

  public HAURLInfo getHAURLInfo() {
    return haurlInfo;
  }
}
