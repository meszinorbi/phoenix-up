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
package org.apache.phoenix.expression;

import java.util.List;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.phoenix.query.QueryConstants;
import org.apache.phoenix.schema.tuple.Tuple;
import org.apache.phoenix.schema.types.PDataType;
import org.apache.phoenix.schema.types.PDate;
import org.apache.phoenix.schema.types.PLong;

/**
 * Subtract expression implementation
 * @since 0.1
 */
public class LongSubtractExpression extends SubtractExpression {
  public LongSubtractExpression() {
  }

  public LongSubtractExpression(List<Expression> children) {
    super(children);
  }

  @Override
  public boolean evaluate(Tuple tuple, ImmutableBytesWritable ptr) {
    long finalResult = 0;

    for (int i = 0; i < children.size(); i++) {
      Expression child = children.get(i);
      if (!child.evaluate(tuple, ptr) || ptr.getLength() == 0) {
        return false;
      }
      PDataType childType = child.getDataType();
      boolean isDate = childType.isCoercibleTo(PDate.INSTANCE);
      long childvalue = childType.getCodec().decodeLong(ptr, child.getSortOrder());
      if (i == 0) {
        finalResult = childvalue;
      } else {
        finalResult -= childvalue;
        /*
         * Special case for date subtraction - note that only first two expression may be dates. We
         * need to convert the date to a unit of "days" because that's what sql expects.
         */
        if (isDate) {
          finalResult /= QueryConstants.MILLIS_IN_DAY;
        }
      }
    }
    byte[] resultPtr = new byte[getDataType().getByteSize()];
    getDataType().getCodec().encodeLong(finalResult, resultPtr, 0);
    ptr.set(resultPtr);
    return true;
  }

  @Override
  public final PDataType getDataType() {
    return PLong.INSTANCE;
  }

  @Override
  public ArithmeticExpression clone(List<Expression> children) {
    return new LongSubtractExpression(children);
  }

}
