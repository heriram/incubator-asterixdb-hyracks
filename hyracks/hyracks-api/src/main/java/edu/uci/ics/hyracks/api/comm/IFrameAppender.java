/*
 * Copyright 2009-2013 by The Regents of the University of California
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  you may obtain a copy of the License from
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package edu.uci.ics.hyracks.api.comm;

import java.nio.ByteBuffer;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;

public interface IFrameAppender {
    /**
     * Reset to attach to a new frame.
     *
     * @param frame the new frame
     * @param clear indicate whether we need to clear this new frame
     * @throws HyracksDataException
     */
    void reset(IFrame frame, boolean clear) throws HyracksDataException;

    /**
     * Get how many tuples in current frame.
     *
     * @return
     */
    int getTupleCount();

    /**
     * Get the ByteBuffer which contains the frame data.
     *
     * @return
     */
    ByteBuffer getBuffer();

    /**
     * Flush the frame content to the given writer.
     * Clear the inner buffer after flush if {@code clear} is <code>true</code>.
     *
     * @param outWriter the output writer
     * @param clear     indicate whether to clear the inside frame after flushed or not.
     * @throws HyracksDataException
     */
    void flush(IFrameWriter outWriter, boolean clear) throws HyracksDataException;
}
