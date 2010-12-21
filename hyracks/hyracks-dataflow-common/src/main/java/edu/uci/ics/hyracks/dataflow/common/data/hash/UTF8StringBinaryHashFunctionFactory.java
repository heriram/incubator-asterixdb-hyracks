/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.dataflow.common.data.hash;

import edu.uci.ics.hyracks.api.dataflow.value.IBinaryHashFunction;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.dataflow.common.data.util.StringUtils;

public class UTF8StringBinaryHashFunctionFactory implements IBinaryHashFunctionFactory {
    public static final UTF8StringBinaryHashFunctionFactory INSTANCE = new UTF8StringBinaryHashFunctionFactory();

    private static final long serialVersionUID = 1L;

    private UTF8StringBinaryHashFunctionFactory() {
    }

    @Override
    public IBinaryHashFunction createBinaryHashFunction() {
        return new IBinaryHashFunction() {
            @Override
            public int hash(byte[] bytes, int offset, int length) {
                int h = 0;
                int utflen = StringUtils.getUTFLen(bytes, offset);
                int sStart = offset + 2;
                int c = 0;

                while (c < utflen) {
                    char ch = StringUtils.charAt(bytes, sStart + c);
                    h = 31 * h + ch;
                    c += StringUtils.charSize(bytes, sStart + c);
                }
                return h;
            }
        };
    }
}