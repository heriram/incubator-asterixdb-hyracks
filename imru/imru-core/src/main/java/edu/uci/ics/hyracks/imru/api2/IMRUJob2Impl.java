package edu.uci.ics.hyracks.imru.api2;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.util.JavaSerializationUtils;
import edu.uci.ics.hyracks.control.nc.application.NCApplicationContext;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import edu.uci.ics.hyracks.dataflow.common.comm.util.ByteBufferInputStream;
import edu.uci.ics.hyracks.imru.api.IModel;
import edu.uci.ics.hyracks.imru.example.utils.R;

public class IMRUJob2Impl<Model extends IModel, T extends Serializable>
        implements IMRUJob2<Model> {
    IMRUJob<Model, T> job;
    private static ExecutorService threadPool = Executors.newCachedThreadPool();

    public IMRUJob2Impl(IMRUJob<Model, T> job) {
        this.job = job;
    }

    @Override
    public int getCachedDataFrameSize() {
        return job.getCachedDataFrameSize();
    }

    @Override
    public Model initModel() {
        return job.initModel();
    }

    @Override
    public void map(Iterator<ByteBuffer> input, Model model,
            OutputStream output, int cachedDataFrameSize)
            throws HyracksDataException {
        FrameTupleAccessor accessor = new FrameTupleAccessor(
                cachedDataFrameSize, new RecordDescriptor(
                        new ISerializerDeserializer[job.getFieldCount()]));
        TupleReader reader = new TupleReader(input, accessor,
                new ByteBufferInputStream());
        try {
            reader.nextTuple();
            T reduceResult;
            T firstResult = job.map(reader, model, cachedDataFrameSize);
            if (!reader.hasNextTuple()) {
                reduceResult = firstResult;
            } else {
                final ASyncIO<T> io = new ASyncIO<T>();
                Future<T> future = threadPool.submit(new Callable<T>() {
                    @Override
                    public T call() {
                        Iterator<T> input = io.getInput();
                        try {
                            return job.reduce(input);
                        } catch (HyracksDataException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }
                });
                io.add(firstResult);
                while (reader.hasNextTuple()) {
                    reader.nextTuple();
                    T result = job.map(reader, model, cachedDataFrameSize);
                    io.add(result);
                }
                io.close();
                reduceResult = future.get();
            }
            byte[] objectData = JavaSerializationUtils.serialize(reduceResult);
            output.write(objectData);
            output.close();
        } catch (Exception e) {
            throw new HyracksDataException(e);
        }
    }

    @Override
    public void parse(IHyracksTaskContext ctx, InputStream in,
            IFrameWriter writer) throws IOException {
        TupleWriter tupleWriter = new TupleWriter(ctx, writer, job
                .getFieldCount());
        job.parse(ctx, in, tupleWriter);
        tupleWriter.close();
    }

    @Override
    public void reduce(final IHyracksTaskContext ctx,
            final Iterator<byte[]> input, OutputStream output)
            throws HyracksDataException {
        Iterator<T> iterator = new Iterator<T>() {
            @Override
            public void remove() {
            }

            @Override
            public boolean hasNext() {
                return input.hasNext();
            }

            @Override
            public T next() {
                byte[] objectData = input.next();
                if (objectData == null)
                    return null;
                NCApplicationContext appContext = (NCApplicationContext) ctx
                        .getJobletContext().getApplicationContext();
                try {
                    return (T) appContext.deserialize(objectData);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        T object = job.reduce(iterator);
        byte[] objectData;
        try {
            objectData = JavaSerializationUtils.serialize(object);
            output.write(objectData);
            output.close();
        } catch (IOException e) {
            throw new HyracksDataException(e);
        }
    }

    @Override
    public boolean shouldTerminate(Model model) {
        return job.shouldTerminate(model);
    }

    @Override
    public void update(final IHyracksTaskContext ctx,
            final Iterator<byte[]> input, Model model)
            throws HyracksDataException {
        Iterator<T> iterator = new Iterator<T>() {
            @Override
            public void remove() {
            }

            @Override
            public boolean hasNext() {
                return input.hasNext();
            }

            @Override
            public T next() {
                byte[] objectData = input.next();
                if (objectData == null)
                    return null;
                NCApplicationContext appContext = (NCApplicationContext) ctx
                        .getJobletContext().getApplicationContext();
                try {
                    return (T) appContext.deserialize(objectData);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        job.update(iterator, model);
    }
}