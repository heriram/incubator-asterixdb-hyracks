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
package edu.uci.ics.hyracks.dataflow.std.sort;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.concurrent.ArrayBlockingQueue;

import edu.uci.ics.hyracks.api.comm.IFrameReader;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import edu.uci.ics.hyracks.dataflow.common.comm.util.FrameUtils;
import edu.uci.ics.hyracks.dataflow.std.util.ReferenceEntry;
import edu.uci.ics.hyracks.dataflow.std.util.SelectionTree;
import edu.uci.ics.hyracks.dataflow.std.util.SelectionTree.Entry;

public class PredictingFrameReaderCollection {
    private final int[] sortFields;
    private final IBinaryComparatorFactory[] comparatorFactories;
    private final IFrameReader[] runCursors;
    private final IFrameReader[] fileReaders;
    private Comparator<ReferenceEntry> comparator;

    private final FrameTupleAccessor[] tupleAccessors;

    // Each entry corresponding to the last tuple corresponding to the frame of the current run being sorted.
    private final BackQueueEntry[] backQueueEntries;

    // Holds the reference to the last-tuple of each frame being sorted.
    private SelectionTree backQueue;

    /* The two blocking queues which hold the buffer and the runIndex of the corresponding buffer frame which are
     * used for prediction. The empty queue contains all the buffers that are available for prediction and the
     * full queue contains all the populated buffers.
     */
    private ArrayBlockingQueue<PredictionBuffer> emptyPredictionQueue;
    private final ForecastQueues forecastQueues;

    /* The code snippet involved the locks array initialization and synchronization is taken from Stack Overflow.
     * The question is at: http://stackoverflow.com/questions/7751997/how-to-synchronize-single-element-of-integer-array
     * The snippet used is from the answer by RAY.
     * RAY's profile page is at: http://stackoverflow.com/users/453513/ray
     */
    private final Object[] locks;

    private final Thread predictorThread;

    // Constructor for PredictingFrameReaderCollection.
    public PredictingFrameReaderCollection(IHyracksTaskContext ctx, int[] sortFields,
            IBinaryComparatorFactory[] comparatorFactories, RecordDescriptor recordDesc, IFrameReader[] runs,
            int predictionFramesLimit) {
        this.sortFields = sortFields;
        this.comparatorFactories = comparatorFactories;
        this.fileReaders = new IFrameReader[runs.length];

        this.runCursors = new IFrameReader[runs.length];
        for (int i = 0; i < runs.length; i++) {
            fileReaders[i] = runs[i];
            runCursors[i] = new PredictingFrameReader(fileReaders[i], i, this);
        }

        // As a guard, initialize the comparator is set to null, so accesses won't raise a compilation error.
        this.comparator = null;

        this.emptyPredictionQueue = new ArrayBlockingQueue<PredictionBuffer>(predictionFramesLimit);

        for (int i = 0; i < predictionFramesLimit; i++) {
            this.emptyPredictionQueue.add(new PredictionBuffer(ctx, this.fileReaders));
        }

        forecastQueues = new ForecastQueues(runs.length);

        tupleAccessors = new FrameTupleAccessor[runs.length];
        backQueueEntries = new BackQueueEntry[runs.length];

        locks = new Object[runs.length];

        for (int i = 0; i < runs.length; i++) {
            tupleAccessors[i] = new FrameTupleAccessor(ctx.getFrameSize(), recordDesc);
            backQueueEntries[i] = new BackQueueEntry(i, tupleAccessors[i], -1);
            locks[i] = new Object();
        }

        // Start the predictor thread.
        predictorThread = new Thread(new PredictorThread());
    }

    public IFrameReader[] getRunCursors() {
        return runCursors;
    }

    public void setComparator(Comparator<ReferenceEntry> comparator) {
        this.comparator = new MergerComparator(sortFields, comparatorFactories);

        buildBackQueue();
        predictorThread.start();
    }

    public boolean getFrame(ByteBuffer buffer, int runIndex) throws HyracksDataException {
        boolean success;
        if (comparator == null) {
            // For the initial fetch we will have to read from the file and then set the tuple accessors etc.
            success = fileReaders[runIndex].nextFrame(buffer);
            tupleAccessors[runIndex].reset(buffer);
            backQueueEntries[runIndex].setTupleIndex(tupleAccessors[runIndex].getTupleCount() - 1);
        } else {
            try {
                success = forecastQueues.deque(buffer, runIndex);
            } catch (InterruptedException e) {
                // When the main thread is interrupted, we can't do much, just return.
                return false;
            }
        }
        return success;
    }

    private void buildBackQueue() {
        /* We first populate the heap and then build the heap.
         * NOTE: Building the heap is an O( n log(n) ) operation, but populating the heap is an O( n )
         * operation. So heap building dominates the populating stage, so we can afford to run the
         * populating stage as a pre-processing step.
         */
        backQueue = new SelectionTree(backQueueEntries);
    }

    private class BackQueueEntry extends ReferenceEntry implements Entry {
        private boolean canAdvance;

        public BackQueueEntry(int runIndex, FrameTupleAccessor fta, int tupleIndex) {
            super(runIndex, fta, tupleIndex);
            canAdvance = true;
        }

        @Override
        public int compareTo(Entry o) {
            return comparator.compare(this, (BackQueueEntry) o);
        }

        @Override
        public boolean advance() {
            return canAdvance;
        }

        public void update(PredictionBuffer predBuffer) throws HyracksDataException, InterruptedException {
            canAdvance = forecastQueues.enqueue(this, predBuffer);
        }
    }

    private class ForecastQueues {
        private final PredictionBuffer[] heads;
        private final PredictionBuffer[] tails;

        public ForecastQueues(int numQueues) {
            heads = new PredictionBuffer[numQueues];
            tails = new PredictionBuffer[numQueues];

            for (int i = 0; i < numQueues; i++) {
                heads[i] = null;
                tails[i] = null;
            }
        }

        public boolean enqueue(ReferenceEntry entry, PredictionBuffer predBuffer) throws HyracksDataException,
                InterruptedException {
            PredictionBuffer prevTail;
            /* We always keep pre-fetching the next frame for the predicted run as long as we have
             * an empty buffer to predict, i.e. the emptyPredictionQueue is NOT empty. Since
             * emptyPredictionQueue is implemented as an ArrayBlockingQueue object this
             * automatically blocks this thread and handles the signalling when the
             * emptyPredictionQueue is empty so as not to proceed.
             */
            int runIndex = entry.getRunid();
            FrameTupleAccessor fta = entry.getAccessor();

            boolean hasBuffer = predBuffer.nextRunFrame(runIndex);
            if (!hasBuffer) {
                emptyPredictionQueue.put(predBuffer);
                return false;
            }

            synchronized (locks[runIndex]) {
                prevTail = tails[runIndex];
                tails[runIndex] = predBuffer;
                fta.reset(predBuffer.getBuffer());
                entry.setTupleIndex(fta.getTupleCount() - 1);
                if (heads[runIndex] == null) {
                    heads[runIndex] = predBuffer;
                    locks[runIndex].notifyAll();
                } else {
                    prevTail.setNext(predBuffer);
                }
            }

            return true;
        }

        public boolean deque(ByteBuffer buffer, int runIndex) throws InterruptedException {
            FrameTupleAccessor fta = null;
            PredictionBuffer head = null;

            synchronized (locks[runIndex]) {
                fta = tupleAccessors[runIndex];

                if ((heads[runIndex] == null) && (backQueueEntries[runIndex] == null)) {
                    return false;
                }

                while (heads[runIndex] == null) {
                    locks[runIndex].wait();
                    if (backQueueEntries[runIndex] == null) {
                        return false;
                    }
                }

                head = heads[runIndex];
                PredictionBuffer headNext = head.getNext();
                head.setNext(null);
                head.transfer(buffer);

                if (headNext == null) {
                    fta.reset(buffer);
                }
                heads[runIndex] = headNext;

                emptyPredictionQueue.put(head);
            }
            return true;
        }
    }

    public class PredictionBuffer {
        private final IFrameReader[] fileReaders;
        private final ByteBuffer buffer;
        private PredictionBuffer next;
        private boolean hasBuffer;

        public PredictionBuffer(IHyracksTaskContext ctx, IFrameReader[] fileReaders) {
            this.buffer = ctx.allocateFrame();
            this.fileReaders = fileReaders;
            this.next = null;
            this.hasBuffer = false;
        }

        public boolean nextRunFrame(int runIndex) throws HyracksDataException {
            buffer.clear();
            hasBuffer = fileReaders[runIndex].nextFrame(buffer);
            return hasBuffer;
        }

        public void transfer(ByteBuffer buffer) {
            FrameUtils.copy(this.buffer, buffer);
            hasBuffer = false;
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }

        public PredictionBuffer getNext() {
            return next;
        }

        public void setNext(PredictionBuffer next) {
            this.next = next;
        }
    }

    private class PredictorThread implements Runnable {
        public void run() {
            int runIndex;
            BackQueueEntry entry;
            PredictionBuffer predBuffer;

            while (!backQueue.isEmpty()) {
                entry = (BackQueueEntry) backQueue.peek();
                runIndex = entry.getRunid();

                try {
                    predBuffer = emptyPredictionQueue.take();

                    entry.update(predBuffer);
                    backQueue.pop();

                    if (backQueueEntries[runIndex] == null) {
                        synchronized (locks[runIndex]) {
                            locks[runIndex].notifyAll();
                        }
                    }
                } catch (HyracksDataException e) {
                    /* Reading from the file failed, but we are inside a thread and cannot do much, so
                     * just pass. This will be caught later when the same frame is attempted to be read
                     * again.
                     */
                } catch (InterruptedException e) {
                    // Since the thread was interrupted, we just bail out.
                    return;
                }
            }
        }
    }

}