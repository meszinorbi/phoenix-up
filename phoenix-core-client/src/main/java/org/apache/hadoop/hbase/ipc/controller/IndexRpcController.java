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
package org.apache.hadoop.hbase.ipc.controller;

import com.google.protobuf.RpcController;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.ipc.DelegatingHBaseRpcController;
import org.apache.hadoop.hbase.ipc.HBaseRpcController;
import org.apache.phoenix.query.QueryServices;
import org.apache.phoenix.query.QueryServicesOptions;
import org.apache.phoenix.util.IndexUtil;

/**
 * {@link RpcController} that sets the appropriate priority of RPC calls destined for Phoenix index
 * tables.
 */
class IndexRpcController extends DelegatingHBaseRpcController {

  private final int priority;
  private final String tracingTableName;

  public IndexRpcController(HBaseRpcController delegate, Configuration conf) {
    super(delegate);
    this.priority = IndexUtil.getIndexPriority(conf);
    this.tracingTableName = conf.get(QueryServices.TRACING_STATS_TABLE_NAME_ATTRIB,
      QueryServicesOptions.DEFAULT_TRACING_STATS_TABLE_NAME);
  }

  @Override
  public void setPriority(final TableName tn) {
    if (!tn.isSystemTable() && !tn.getNameAsString().equals(tracingTableName)) {
      setPriority(this.priority);
    } else {
      super.setPriority(tn);
    }
  }

}
