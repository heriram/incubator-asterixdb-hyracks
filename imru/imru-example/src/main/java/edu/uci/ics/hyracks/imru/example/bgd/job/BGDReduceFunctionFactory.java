package edu.uci.ics.hyracks.imru.example.bgd.job;

import java.nio.ByteBuffer;

import edu.uci.ics.hyracks.api.comm.IFrameTupleAccessor;
import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.data.std.primitive.FloatPointable;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAppender;
import edu.uci.ics.hyracks.dataflow.common.comm.util.FrameUtils;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.FloatSerializerDeserializer;
import edu.uci.ics.hyracks.imru.api.IOneByOneReduceFunction;
import edu.uci.ics.hyracks.imru.api.IReduceFunction;
import edu.uci.ics.hyracks.imru.api.IReduceFunctionFactory;
import edu.uci.ics.hyracks.imru.example.bgd.data.FragmentableFloatArray;
import edu.uci.ics.hyracks.imru.example.bgd.data.RecordDescriptorUtils;

public class BGDReduceFunctionFactory implements IReduceFunctionFactory {

    private final int numFeatures;

    public BGDReduceFunctionFactory(int numFeatures) {
        this.numFeatures = numFeatures;
    }

    @Override
    public IReduceFunction createReduceFunction(final IHyracksTaskContext ctx) {
        return new IOneByOneReduceFunction() {

            private IFrameWriter writer;
            private IFrameTupleAccessor accessor;
            private boolean readLoss;
            private float loss;
            private FragmentableFloatArray gradientAcc;

            @Override
            public void setFrameWriter(IFrameWriter writer) {
                this.writer = writer;
            }

            @Override
            public void open() throws HyracksDataException {
                // Accessor to read loss frame
                gradientAcc = new FragmentableFloatArray(new float[numFeatures]);
                loss = 0.0f;
            }

            @Override
            public void close() throws HyracksDataException {
                // Write Loss
                ArrayTupleBuilder lossBuilder = new ArrayTupleBuilder(1);
                FrameTupleAppender appender = new FrameTupleAppender(ctx.getFrameSize());
                ByteBuffer outFrame = ctx.allocateFrame();
                appender.reset(outFrame, true);
                lossBuilder.reset();
                lossBuilder.addField(FloatSerializerDeserializer.INSTANCE, loss);
                appender.append(lossBuilder.getFieldEndOffsets(), lossBuilder.getByteArray(), 0, lossBuilder.getSize());
                FrameUtils.flushFrame(outFrame, writer);
                // Write gradient
                gradientAcc.writeFragments(writer, ctx);
            }

            @Override
            public void reduce(ByteBuffer chunk) throws HyracksDataException {
                if (!readLoss) {
                    accessor = new FrameTupleAccessor(ctx.getFrameSize(), RecordDescriptorUtils.getDummyRecordDescriptor(1));
                    accessor.reset(chunk);
                    loss += FloatPointable.getFloat(
                            chunk.array(),
                            accessor.getTupleStartOffset(0) + accessor.getFieldSlotsLength()
                                    + accessor.getFieldStartOffset(0, 0));
                    accessor = new FrameTupleAccessor(ctx.getFrameSize(), RecordDescriptorUtils.getDummyRecordDescriptor(2));
                    readLoss = true;
                } else {
                    accessor.reset(chunk);
                    for (int i = 0; i < accessor.getTupleCount(); i++) {
                        FragmentableFloatArray gradientFragment = FragmentableFloatArray.readFromFrame(accessor, i);
                        gradientAcc.accumulate(gradientFragment);
                    }
                }
            }
        };
    }

}
