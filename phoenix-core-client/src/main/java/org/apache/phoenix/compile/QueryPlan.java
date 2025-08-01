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
package org.apache.phoenix.compile;

import java.sql.SQLException;
import java.util.List;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.phoenix.compile.GroupByCompiler.GroupBy;
import org.apache.phoenix.compile.OrderByCompiler.OrderBy;
import org.apache.phoenix.execute.SortMergeJoinPlan;
import org.apache.phoenix.execute.visitor.QueryPlanVisitor;
import org.apache.phoenix.iterate.ParallelScanGrouper;
import org.apache.phoenix.iterate.ResultIterator;
import org.apache.phoenix.optimize.Cost;
import org.apache.phoenix.parse.FilterableStatement;
import org.apache.phoenix.parse.SelectStatement;
import org.apache.phoenix.query.KeyRange;
import org.apache.phoenix.schema.TableRef;

/**
 * Interface for an executable query plan
 * @since 0.1
 */
public interface QueryPlan extends StatementPlan {
  /**
   * Get a result iterator to iterate over the results
   * @return result iterator for iterating over the results
   */
  public ResultIterator iterator() throws SQLException;

  public ResultIterator iterator(ParallelScanGrouper scanGrouper) throws SQLException;

  public ResultIterator iterator(ParallelScanGrouper scanGrouper, Scan scan) throws SQLException;

  public long getEstimatedSize();

  public Cost getCost();

  // TODO: change once joins are supported
  TableRef getTableRef();

  /**
   * Returns projector used to formulate resultSet row
   */
  RowProjector getProjector();

  Integer getLimit();

  Integer getOffset();

  /**
   * Return the compiled Order By clause of {@link SelectStatement}.
   */
  OrderBy getOrderBy();

  GroupBy getGroupBy();

  List<KeyRange> getSplits();

  List<List<Scan>> getScans();

  FilterableStatement getStatement();

  public boolean isDegenerate();

  public boolean isRowKeyOrdered();

  boolean isApplicable();

  /**
   * @return whether underlying {@link ResultScanner} can be picked up in a round-robin fashion.
   *         Generally, selecting scanners in such a fashion is possible if rows don't have to be
   *         returned back in a certain order.
   */
  public boolean useRoundRobinIterator() throws SQLException;

  <T> T accept(QueryPlanVisitor<T> visitor);

  /**
   * <pre>
   * Get the actual OrderBys of this queryPlan, which may be different from {@link #getOrderBy()},
   * because {@link #getOrderBy()} is only the compiled result of {@link SelectStatement}.
   * The return type is List because we can get multiple OrderBys for the query result of {@link SortMergeJoinPlan},
   * eg. for the sql:
   * SELECT  * FROM T1 JOIN T2 ON T1.a = T2.a and T1.b = T2.b
   * The result of the sort-merge-join is sorted on (T1.a, T1.b) and (T2.a, T2.b) at the same time.
   * </pre>
   */
  public List<OrderBy> getOutputOrderBys();
}
