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
package org.apache.phoenix.util;

import static org.apache.phoenix.query.QueryConstants.AGG_TIMESTAMP;
import static org.apache.phoenix.query.QueryConstants.GROUPED_AGGREGATOR_VALUE_BYTES;
import static org.apache.phoenix.query.QueryConstants.SINGLE_COLUMN;
import static org.apache.phoenix.query.QueryConstants.SINGLE_COLUMN_FAMILY;
import static org.apache.phoenix.query.QueryConstants.VALUE_COLUMN_FAMILY;
import static org.apache.phoenix.query.QueryConstants.VALUE_COLUMN_QUALIFIER;

import java.io.DataOutput;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.DoNotRetryIOException;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.WritableUtils;
import org.apache.phoenix.execute.TupleProjector;
import org.apache.phoenix.expression.Expression;
import org.apache.phoenix.hbase.index.util.ImmutableBytesPtr;
import org.apache.phoenix.jdbc.PhoenixPrefetchedResultSet;
import org.apache.phoenix.jdbc.PhoenixResultSet;
import org.apache.phoenix.parse.TableName;
import org.apache.phoenix.query.QueryConstants;
import org.apache.phoenix.schema.PTable;
import org.apache.phoenix.schema.SortOrder;
import org.apache.phoenix.schema.tuple.ResultTuple;
import org.apache.phoenix.schema.tuple.Tuple;
import org.apache.phoenix.schema.types.PInteger;
import org.apache.phoenix.schema.types.PVarbinaryEncoded;

/**
 * Utilities for Tuple
 * @since 0.1
 */
public class TupleUtil {
  private TupleUtil() {
  }

  public static boolean equals(Tuple t1, Tuple t2, ImmutableBytesWritable ptr) {
    t1.getKey(ptr);
    byte[] buf = ptr.get();
    int offset = ptr.getOffset();
    int length = ptr.getLength();
    t2.getKey(ptr);
    return Bytes.compareTo(buf, offset, length, ptr.get(), ptr.getOffset(), ptr.getLength()) == 0;
  }

  public static int compare(Tuple t1, Tuple t2, ImmutableBytesWritable ptr) {
    return compare(t1, t2, ptr, 0);
  }

  public static int compare(Tuple t1, Tuple t2, ImmutableBytesWritable ptr, int keyOffset) {
    t1.getKey(ptr);
    byte[] buf = ptr.get();
    int offset = ptr.getOffset() + keyOffset;
    int length = ptr.getLength() - keyOffset;
    t2.getKey(ptr);
    return Bytes.compareTo(buf, offset, length, ptr.get(), ptr.getOffset() + keyOffset,
      ptr.getLength() - keyOffset);
  }

  /**
   * Set ptr to point to the value contained in the first KeyValue without exploding Result into
   * KeyValue array.
   */
  public static void getAggregateValue(Tuple r, ImmutableBytesWritable ptr) {
    if (r.size() == 1) {
      Cell kv = r.getValue(0); // Just one KV for aggregation
      if (
        Bytes.compareTo(SINGLE_COLUMN_FAMILY, 0, SINGLE_COLUMN_FAMILY.length, kv.getFamilyArray(),
          kv.getFamilyOffset(), kv.getFamilyLength()) == 0
      ) {
        if (
          Bytes.compareTo(SINGLE_COLUMN, 0, SINGLE_COLUMN.length, kv.getQualifierArray(),
            kv.getQualifierOffset(), kv.getQualifierLength()) == 0
        ) {
          ptr.set(kv.getValueArray(), kv.getValueOffset(), kv.getValueLength());
          return;
        }
      }
    }
    throw new IllegalStateException(
      "Expected single, aggregated KeyValue from coprocessor, but instead received " + r
        + ". Ensure aggregating coprocessors are loaded correctly on server");
  }

  public static Tuple getAggregateGroupTuple(Tuple tuple) {
    if (tuple == null) {
      return null;
    }
    if (tuple.size() == 1) {
      Cell kv = tuple.getValue(0);
      if (
        Bytes.compareTo(GROUPED_AGGREGATOR_VALUE_BYTES, 0, GROUPED_AGGREGATOR_VALUE_BYTES.length,
          kv.getFamilyArray(), kv.getFamilyOffset(), kv.getFamilyLength()) == 0
      ) {
        if (
          Bytes.compareTo(GROUPED_AGGREGATOR_VALUE_BYTES, 0, GROUPED_AGGREGATOR_VALUE_BYTES.length,
            kv.getQualifierArray(), kv.getQualifierOffset(), kv.getQualifierLength()) == 0
        ) {
          byte[] kvValue = new byte[kv.getValueLength()];
          System.arraycopy(kv.getValueArray(), kv.getValueOffset(), kvValue, 0, kvValue.length);
          int sizeOfAggregateGroupValue =
            PInteger.INSTANCE.getCodec().decodeInt(kvValue, 0, SortOrder.ASC);
          Cell result = PhoenixKeyValueUtil.newKeyValue(kvValue, Bytes.SIZEOF_INT,
            sizeOfAggregateGroupValue, SINGLE_COLUMN_FAMILY, SINGLE_COLUMN, AGG_TIMESTAMP, kvValue,
            Bytes.SIZEOF_INT + sizeOfAggregateGroupValue,
            kvValue.length - Bytes.SIZEOF_INT - sizeOfAggregateGroupValue);
          return new ResultTuple(Result.create(Collections.singletonList(result)));
        }
      }
    }
    return tuple;
  }

  /**
   * Concatenate results evaluated against a list of expressions
   * @param result the tuple for expression evaluation
   * @return the concatenated byte array as ImmutableBytesWritable
   */
  public static ImmutableBytesPtr getConcatenatedValue(Tuple result, List<Expression> expressions)
    throws IOException {
    ImmutableBytesPtr value = new ImmutableBytesPtr(ByteUtil.EMPTY_BYTE_ARRAY);
    Expression expression = expressions.get(0);
    boolean evaluated = expression.evaluate(result, value);

    if (expressions.size() == 1) {
      if (!evaluated) {
        value.set(ByteUtil.EMPTY_BYTE_ARRAY);
      }
      return value;
    } else {
      TrustedByteArrayOutputStream output =
        new TrustedByteArrayOutputStream(value.getLength() * expressions.size());
      try {
        if (evaluated) {
          output.write(value.get(), value.getOffset(), value.getLength());
        }
        for (int i = 1; i < expressions.size(); i++) {
          if (!expression.getDataType().isFixedWidth()) {
            output.write(SchemaUtil.getSeparatorBytes(expression.getDataType(), true,
              value.getLength() == 0, expression.getSortOrder()));
          }
          expression = expressions.get(i);
          if (expression.evaluate(result, value)) {
            output.write(value.get(), value.getOffset(), value.getLength());
          } else if (i < expressions.size() - 1 && expression.getDataType().isFixedWidth()) {
            // This should never happen, because any non terminating nullable fixed width type (i.e.
            // INT or LONG) is
            // converted to a variable length type (i.e. DECIMAL) to allow an empty byte array to
            // represent null.
            throw new DoNotRetryIOException(
              "Non terminating null value found for fixed width expression (" + expression
                + ") in row: " + result);
          }
        }
        // Write trailing separator if last expression was variable length and descending
        if (!expression.getDataType().isFixedWidth()) {
          if (expression.getDataType() != PVarbinaryEncoded.INSTANCE) {
            if (
              SchemaUtil.getSeparatorByte(true, value.getLength() == 0, expression)
                  == QueryConstants.DESC_SEPARATOR_BYTE
            ) {
              output.write(QueryConstants.DESC_SEPARATOR_BYTE);
            }
          } else {
            byte[] sepBytes = SchemaUtil.getSeparatorBytesForVarBinaryEncoded(true,
              value.getLength() == 0, expression.getSortOrder());
            if (sepBytes == QueryConstants.DESC_VARBINARY_ENCODED_SEPARATOR_BYTES) {
              output.write(QueryConstants.DESC_VARBINARY_ENCODED_SEPARATOR_BYTES);
            }
          }
        }
        byte[] outputBytes = output.getBuffer();
        value.set(outputBytes, 0, output.size());
        return value;
      } finally {
        output.close();
      }
    }
  }

  public static int write(Tuple result, DataOutput out) throws IOException {
    int size = 0;
    for (int i = 0; i < result.size(); i++) {
      KeyValue kv = PhoenixKeyValueUtil.maybeCopyCell(result.getValue(i));
      size += kv.getLength();
      size += Bytes.SIZEOF_INT; // kv.getLength
    }

    WritableUtils.writeVInt(out, size);
    for (int i = 0; i < result.size(); i++) {
      KeyValue kv = PhoenixKeyValueUtil.maybeCopyCell(result.getValue(i));
      out.writeInt(kv.getLength());
      out.write(kv.getBuffer(), kv.getOffset(), kv.getLength());
    }
    return size;
  }

  /**
   * Convert the given Tuple containing list of Cells to ResultSet with similar effect as if SELECT
   * * FROM <table-name> is queried.
   * @param toProject Tuple to be projected.
   * @param tableName Table name.
   * @param conn      Phoenix Connection object.
   * @return ResultSet for the give single row.
   * @throws SQLException If any SQL operation fails.
   */
  public static ResultSet getResultSet(Tuple toProject, TableName tableName, Connection conn)
    throws SQLException {
    if (tableName == null) {
      return null;
    }
    try (PhoenixResultSet resultSet =
      (PhoenixResultSet) conn.createStatement().executeQuery("SELECT * FROM " + tableName)) {
      PTable pTable = resultSet.getStatement().getQueryPlan().getContext().getResolver().getTables()
        .get(0).getTable();
      TupleProjector tupleProjector = new TupleProjector(pTable);
      // NOTE - Tuple result projection does not work well with local index and causes
      // this assertion to get get triggered: CellUtil.matchingRows(kvs[0], kvs[kvs.length-1])
      // as the Tuple contains cells of the local index too, so filter out those cells.
      Cell firstCell = null;
      List<Cell> cells = new ArrayList<>(toProject.size());
      for (int i = 0; i < toProject.size(); ++i) {
        Cell cell = toProject.getValue(i);
        if (firstCell == null) {
          firstCell = cell;
        } else {
          if (
            !CellUtil.matchingRows(firstCell, firstCell.getRowLength(), cell, cell.getRowLength())
          ) {
            continue;
          }
        }
        cells.add(cell);
      }
      toProject = new ResultTuple(Result.create(cells));
      // Project results for ResultSet.
      Tuple tuple = tupleProjector.projectResults(toProject, true);
      // Use new CF:CQ that can be correctly used by ResultSet.
      Cell newCell = tuple.getValue(VALUE_COLUMN_FAMILY, VALUE_COLUMN_QUALIFIER);
      ResultSet newResultSet = new PhoenixPrefetchedResultSet(resultSet.getRowProjector(),
        resultSet.getContext(), Collections
          .singletonList(new ResultTuple(Result.create(Collections.singletonList(newCell)))));
      newResultSet.next();
      return newResultSet;
    }
  }
}
