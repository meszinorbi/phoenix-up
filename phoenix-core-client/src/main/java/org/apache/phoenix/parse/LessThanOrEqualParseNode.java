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
package org.apache.phoenix.parse;

import org.apache.hadoop.hbase.CompareOperator;

/**
 * Node representing the less than or equal to operator {@code (<=) } in SQL
 * @since 0.1
 */
public class LessThanOrEqualParseNode extends ComparisonParseNode {

  LessThanOrEqualParseNode(ParseNode lhs, ParseNode rhs) {
    super(lhs, rhs);
  }

  @Override
  public CompareOperator getFilterOp() {
    return CompareOperator.LESS_OR_EQUAL;
  }

  @Override
  public CompareOperator getInvertFilterOp() {
    return CompareOperator.GREATER_OR_EQUAL;
  }
}
