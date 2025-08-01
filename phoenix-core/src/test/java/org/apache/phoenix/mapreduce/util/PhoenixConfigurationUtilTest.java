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
package org.apache.phoenix.mapreduce.util;

import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.mapreduce.Job;
import org.apache.phoenix.mapreduce.index.IndexScrutinyTool.SourceTable;
import org.apache.phoenix.mapreduce.util.PhoenixConfigurationUtil.MRJobType;
import org.apache.phoenix.mapreduce.util.PhoenixConfigurationUtil.SchemaType;
import org.apache.phoenix.query.BaseConnectionlessQueryTest;
import org.apache.phoenix.util.PropertiesUtil;
import org.apache.phoenix.util.SchemaUtil;
import org.apache.phoenix.util.TestUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test for {@link PhoenixConfigurationUtil}
 */
public class PhoenixConfigurationUtilTest extends BaseConnectionlessQueryTest {
  private static final String ORIGINAL_CLUSTER_QUORUM = "myzookeeperhost";
  private static final String OVERRIDE_CLUSTER_QUORUM = "myoverridezookeeperhost";

  protected static String TEST_URL = TestUtil.PHOENIX_CONNECTIONLESS_JDBC_URL;

  @Test
  /**
   * This test reproduces the bug filed in PHOENIX-2310. When invoking
   * PhoenixConfigurationUtil.getUpsertStatement(), if upserting into a Phoenix View and the View
   * DDL had recently been issued such that MetdataClient cache had been updated as a result of the
   * create table versus from data in SYSTEM.CATALOG, the Upsert statement would contain the
   * Object.toString() classname + hashcode instead of the correct cf.column_name representation
   * which would cause the calling Pig script to fail.
   */
  public void testUpsertStatementOnNewViewWithReferencedCols() throws Exception {

    // Arrange
    Connection conn =
      DriverManager.getConnection(getUrl(), PropertiesUtil.deepCopy(TestUtil.TEST_PROPERTIES));

    try {
      final String tableName = "TEST_TABLE_WITH_VIEW";
      final String viewName = "TEST_VIEW";
      String ddl = "CREATE TABLE " + tableName
        + "  (a_string varchar not null, a_binary varbinary not null, col1 integer"
        + "  CONSTRAINT pk PRIMARY KEY (a_string, a_binary))\n";
      conn.createStatement().execute(ddl);
      String viewDdl = "CREATE VIEW " + viewName + "  AS SELECT * FROM " + tableName + "\n";
      conn.createStatement().execute(viewDdl);
      final Configuration configuration = new Configuration();
      PhoenixConfigurationUtil.setOutputClusterUrl(configuration, TEST_URL);
      PhoenixConfigurationUtil.setOutputTableName(configuration, viewName);
      PhoenixConfigurationUtil.setPhysicalTableName(configuration, viewName);
      PhoenixConfigurationUtil.setUpsertColumnNames(configuration,
        new String[] { "A_STRING", "A_BINARY", "COL1" });

      // Act
      final String upserStatement = PhoenixConfigurationUtil.getUpsertStatement(configuration);

      // Assert
      final String expectedUpsertStatement = "UPSERT  INTO " + viewName
        + " (\"A_STRING\", \"A_BINARY\", \"0\".\"COL1\") VALUES (?, ?, ?)";
      assertEquals(expectedUpsertStatement, upserStatement);
    } finally {
      conn.close();
    }
  }

  @Test
  public void testUpsertStatementOnNewTableWithReferencedCols() throws Exception {

    // Arrange
    Connection conn =
      DriverManager.getConnection(getUrl(), PropertiesUtil.deepCopy(TestUtil.TEST_PROPERTIES));

    try {
      final String tableName = "TEST_TABLE_WITH_REF_COLS";
      String ddl = "CREATE TABLE " + tableName
        + "  (a_string varchar not null, a_binary varbinary not null, col1 integer"
        + "  CONSTRAINT pk PRIMARY KEY (a_string, a_binary))\n";
      conn.createStatement().execute(ddl);
      final Configuration configuration = new Configuration();
      PhoenixConfigurationUtil.setOutputClusterUrl(configuration, TEST_URL);
      PhoenixConfigurationUtil.setOutputTableName(configuration, tableName);
      PhoenixConfigurationUtil.setPhysicalTableName(configuration, tableName);
      PhoenixConfigurationUtil.setUpsertColumnNames(configuration,
        new String[] { "A_STRING", "A_BINARY", "COL1" });

      // Act
      final String upserStatement = PhoenixConfigurationUtil.getUpsertStatement(configuration);

      // Assert
      final String expectedUpsertStatement = "UPSERT  INTO " + tableName
        + " (\"A_STRING\", \"A_BINARY\", \"0\".\"COL1\") VALUES (?, ?, ?)";
      assertEquals(expectedUpsertStatement, upserStatement);
    } finally {
      conn.close();
    }
  }

  @Test
  public void testUpsertStatement() throws Exception {
    Connection conn =
      DriverManager.getConnection(getUrl(), PropertiesUtil.deepCopy(TestUtil.TEST_PROPERTIES));
    final String tableName = "TEST_TABLE";
    try {
      String ddl = "CREATE TABLE " + tableName
        + "  (a_string varchar not null, a_binary varbinary not null, col1 integer"
        + "  CONSTRAINT pk PRIMARY KEY (a_string, a_binary))\n";
      conn.createStatement().execute(ddl);
      final Configuration configuration = new Configuration();
      PhoenixConfigurationUtil.setOutputClusterUrl(configuration, TEST_URL);
      PhoenixConfigurationUtil.setOutputTableName(configuration, tableName);
      PhoenixConfigurationUtil.setPhysicalTableName(configuration, tableName);
      final String upserStatement = PhoenixConfigurationUtil.getUpsertStatement(configuration);
      final String expectedUpsertStatement = "UPSERT INTO " + tableName + " VALUES (?, ?, ?)";
      assertEquals(expectedUpsertStatement, upserStatement);
    } finally {
      conn.close();
    }
  }

  @Test
  public void testSelectStatement() throws Exception {
    Connection conn =
      DriverManager.getConnection(getUrl(), PropertiesUtil.deepCopy(TestUtil.TEST_PROPERTIES));
    final String tableName = "TEST_TABLE";
    try {
      String ddl = "CREATE TABLE " + tableName
        + "  (a_string varchar not null, a_binary varbinary not null, col1 integer"
        + "  CONSTRAINT pk PRIMARY KEY (a_string, a_binary))\n";
      conn.createStatement().execute(ddl);
      final Configuration configuration = new Configuration();
      PhoenixConfigurationUtil.setInputClusterUrl(configuration, TEST_URL);
      PhoenixConfigurationUtil.setInputTableName(configuration, tableName);
      final String selectStatement = PhoenixConfigurationUtil.getSelectStatement(configuration);
      final String expectedSelectStatement =
        "SELECT \"A_STRING\" , \"A_BINARY\" , \"0\".\"COL1\" FROM " + tableName;
      assertEquals(expectedSelectStatement, selectStatement);
    } finally {
      conn.close();
    }
  }

  @Test
  public void testSelectStatementWithSchema() throws Exception {
    Connection conn =
      DriverManager.getConnection(getUrl(), PropertiesUtil.deepCopy(TestUtil.TEST_PROPERTIES));
    final String tableName = "TEST_TABLE";
    final String schemaName = SchemaUtil.getEscapedArgument("schema");
    final String fullTableName = SchemaUtil.getTableName(schemaName, tableName);
    try {
      String ddl = "CREATE TABLE " + fullTableName
        + "  (a_string varchar not null, a_binary varbinary not null, col1 integer"
        + "  CONSTRAINT pk PRIMARY KEY (a_string, a_binary))\n";
      conn.createStatement().execute(ddl);
      final Configuration configuration = new Configuration();
      PhoenixConfigurationUtil.setInputClusterUrl(configuration, TEST_URL);
      PhoenixConfigurationUtil.setInputTableName(configuration, fullTableName);
      final String selectStatement = PhoenixConfigurationUtil.getSelectStatement(configuration);
      final String expectedSelectStatement =
        "SELECT \"A_STRING\" , \"A_BINARY\" , \"0\".\"COL1\" FROM " + fullTableName;
      assertEquals(expectedSelectStatement, selectStatement);
    } finally {
      conn.close();
    }
  }

  @Test
  public void testSelectStatementForSpecificColumns() throws Exception {
    Connection conn =
      DriverManager.getConnection(getUrl(), PropertiesUtil.deepCopy(TestUtil.TEST_PROPERTIES));
    final String tableName = "TEST_TABLE";
    try {
      String ddl = "CREATE TABLE " + tableName
        + "  (a_string varchar not null, a_binary varbinary not null, col1 integer"
        + "  CONSTRAINT pk PRIMARY KEY (a_string, a_binary))\n";
      conn.createStatement().execute(ddl);
      final Configuration configuration = new Configuration();
      PhoenixConfigurationUtil.setInputClusterUrl(configuration, TEST_URL);
      PhoenixConfigurationUtil.setInputTableName(configuration, tableName);
      PhoenixConfigurationUtil.setSelectColumnNames(configuration, new String[] { "A_BINARY" });
      final String selectStatement = PhoenixConfigurationUtil.getSelectStatement(configuration);
      final String expectedSelectStatement = "SELECT \"A_BINARY\" FROM " + tableName;
      assertEquals(expectedSelectStatement, selectStatement);
    } finally {
      conn.close();
    }
  }

  @Test
  public void testSelectStatementForArrayTypes() throws Exception {
    Connection conn =
      DriverManager.getConnection(getUrl(), PropertiesUtil.deepCopy(TestUtil.TEST_PROPERTIES));
    final String tableName = "TEST_TABLE";
    try {
      String ddl =
        "CREATE TABLE " + tableName + "  (ID BIGINT NOT NULL PRIMARY KEY, VCARRAY VARCHAR[])\n";
      conn.createStatement().execute(ddl);
      final Configuration configuration = new Configuration();
      PhoenixConfigurationUtil.setInputClusterUrl(configuration, TEST_URL);
      PhoenixConfigurationUtil.setSelectColumnNames(configuration,
        new String[] { "ID", "VCARRAY" });
      PhoenixConfigurationUtil.setSchemaType(configuration, SchemaType.QUERY);
      PhoenixConfigurationUtil.setInputTableName(configuration, tableName);
      final String selectStatement = PhoenixConfigurationUtil.getSelectStatement(configuration);
      final String expectedSelectStatement = "SELECT \"ID\" , \"0\".\"VCARRAY\" FROM " + tableName;
      assertEquals(expectedSelectStatement, selectStatement);
    } finally {
      conn.close();
    }
  }

  @Test
  public void testInputClusterOverride() throws Exception {
    final Configuration configuration = new Configuration();
    configuration.set(HConstants.ZOOKEEPER_QUORUM, ORIGINAL_CLUSTER_QUORUM);
    String zkQuorum = PhoenixConfigurationUtilHelper.getInputCluster(configuration);
    assertEquals(zkQuorum, ORIGINAL_CLUSTER_QUORUM);

    configuration.set(PhoenixConfigurationUtilHelper.MAPREDUCE_INPUT_CLUSTER_QUORUM,
      OVERRIDE_CLUSTER_QUORUM);
    String zkQuorumOverride = PhoenixConfigurationUtilHelper.getInputCluster(configuration);
    assertEquals(zkQuorumOverride, OVERRIDE_CLUSTER_QUORUM);

    final Configuration configuration2 = new Configuration();
    PhoenixConfigurationUtil.setInputCluster(configuration2, OVERRIDE_CLUSTER_QUORUM);
    String zkQuorumOverride2 = PhoenixConfigurationUtilHelper.getInputCluster(configuration2);
    assertEquals(zkQuorumOverride2, OVERRIDE_CLUSTER_QUORUM);

    final Job job = Job.getInstance();
    PhoenixMapReduceUtil.setInputCluster(job, OVERRIDE_CLUSTER_QUORUM);
    Configuration configuration3 = job.getConfiguration();
    String zkQuorumOverride3 = PhoenixConfigurationUtilHelper.getInputCluster(configuration3);
    assertEquals(zkQuorumOverride3, OVERRIDE_CLUSTER_QUORUM);

  }

  @Test
  public void testOutputClusterOverride() throws Exception {
    final Configuration configuration = new Configuration();
    configuration.set(HConstants.ZOOKEEPER_QUORUM, ORIGINAL_CLUSTER_QUORUM);
    String zkQuorum = PhoenixConfigurationUtilHelper.getOutputCluster(configuration);
    assertEquals(zkQuorum, ORIGINAL_CLUSTER_QUORUM);

    configuration.set(PhoenixConfigurationUtilHelper.MAPREDUCE_OUTPUT_CLUSTER_QUORUM,
      OVERRIDE_CLUSTER_QUORUM);
    String zkQuorumOverride = PhoenixConfigurationUtilHelper.getOutputCluster(configuration);
    assertEquals(zkQuorumOverride, OVERRIDE_CLUSTER_QUORUM);

    final Configuration configuration2 = new Configuration();
    PhoenixConfigurationUtil.setOutputCluster(configuration2, OVERRIDE_CLUSTER_QUORUM);
    String zkQuorumOverride2 = PhoenixConfigurationUtilHelper.getOutputCluster(configuration2);
    assertEquals(zkQuorumOverride2, OVERRIDE_CLUSTER_QUORUM);

    final Job job = Job.getInstance();
    PhoenixMapReduceUtil.setOutputCluster(job, OVERRIDE_CLUSTER_QUORUM);
    Configuration configuration3 = job.getConfiguration();
    String zkQuorumOverride3 = PhoenixConfigurationUtilHelper.getOutputCluster(configuration3);
    assertEquals(zkQuorumOverride3, OVERRIDE_CLUSTER_QUORUM);

  }

  @Test
  public void testMrJobTypeOverride() throws Exception {
    final Job job = Job.getInstance();
    Configuration configuration = job.getConfiguration();
    MRJobType mrJobType =
      PhoenixConfigurationUtil.getMRJobType(configuration, MRJobType.QUERY.name());
    assertEquals(MRJobType.QUERY.name(), mrJobType.name());

    PhoenixConfigurationUtil.setMRJobType(configuration, MRJobType.UPDATE_STATS);
    mrJobType = PhoenixConfigurationUtil.getMRJobType(configuration, MRJobType.QUERY.name());
    assertEquals(MRJobType.UPDATE_STATS.name(), mrJobType.name());

  }

  @Test
  public void testTimeRangeOverride() {
    final Configuration configuration = new Configuration();
    Long startTime = 1L;
    Long endTime = 2L;

    PhoenixConfigurationUtil.setIndexToolStartTime(configuration, startTime);
    PhoenixConfigurationUtil.setCurrentScnValue(configuration, endTime);
    Assert.assertEquals(startTime.longValue(),
      Long.parseLong(PhoenixConfigurationUtil.getIndexToolStartTime(configuration)));
    Assert.assertEquals(endTime.longValue(),
      Long.parseLong(PhoenixConfigurationUtil.getCurrentScnValue(configuration)));

  }

  @Test
  public void testLastVerifyTimeConfig() {
    final Configuration configuration = new Configuration();
    Long lastVerifyTime = 2L;

    PhoenixConfigurationUtil.setIndexToolLastVerifyTime(configuration, lastVerifyTime);
    Assert.assertEquals(lastVerifyTime.longValue(),
      Long.parseLong(PhoenixConfigurationUtil.getIndexToolLastVerifyTime(configuration)));

  }

  @Test
  public void testIndexToolSourceConfig() {
    final Configuration conf = new Configuration();

    // by default source is data table
    SourceTable sourceTable = PhoenixConfigurationUtil.getIndexToolSourceTable(conf);
    Assert.assertEquals(sourceTable, SourceTable.DATA_TABLE_SOURCE);

    PhoenixConfigurationUtil.setIndexToolSourceTable(conf, SourceTable.INDEX_TABLE_SOURCE);
    sourceTable = PhoenixConfigurationUtil.getIndexToolSourceTable(conf);
    Assert.assertEquals(sourceTable, SourceTable.INDEX_TABLE_SOURCE);

    PhoenixConfigurationUtil.setIndexToolSourceTable(conf, SourceTable.DATA_TABLE_SOURCE);
    sourceTable = PhoenixConfigurationUtil.getIndexToolSourceTable(conf);
    Assert.assertEquals(sourceTable, SourceTable.DATA_TABLE_SOURCE);
  }
}
