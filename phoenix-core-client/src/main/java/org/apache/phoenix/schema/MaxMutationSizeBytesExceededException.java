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
package org.apache.phoenix.schema;

import java.sql.SQLException;
import org.apache.phoenix.exception.SQLExceptionCode;
import org.apache.phoenix.exception.SQLExceptionInfo;
import org.apache.phoenix.exception.SQLExceptionInfo.Builder;

/**
 * Exception thrown when MutationState size is bigger than maximum allowed number of Bytes
 */

public class MaxMutationSizeBytesExceededException extends SQLException {

  private static final long serialVersionUID = 1L;
  private static SQLExceptionCode code = SQLExceptionCode.MAX_MUTATION_SIZE_BYTES_EXCEEDED;

  public MaxMutationSizeBytesExceededException() {
    super(new Builder(code).build().toString(), code.getSQLState(), code.getErrorCode(), null);
  }

  public MaxMutationSizeBytesExceededException(long maxMutationSizeBytes, long mutationSizeBytes) {
    super(
      new SQLExceptionInfo.Builder(code).setMaxMutationSizeBytes(maxMutationSizeBytes)
        .setMutationSizeBytes(mutationSizeBytes).build().toString(),
      code.getSQLState(), code.getErrorCode(), null);
  }

}
