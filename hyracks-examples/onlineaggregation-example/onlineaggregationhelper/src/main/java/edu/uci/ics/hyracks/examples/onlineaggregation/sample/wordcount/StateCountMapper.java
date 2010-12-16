package edu.uci.ics.hyracks.examples.onlineaggregation.sample.wordcount;

import java.io.IOException;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

public class StateCountMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
    private static final IntWritable ONE = new IntWritable(1);

    private Text word = new Text();
    
    static enum BListing{id, name, street, city, state, zip, phone, cat, url, chainid};
    

    @Override
    public void map(LongWritable key, Text value, Context ctx) throws IOException, InterruptedException {
    	System.err.println("VALUE: " + value.toString());
    	String[] tuple = value.toString().split(",");
    	if (tuple.length < BListing.state.ordinal()) {
    		word.set(tuple[BListing.state.ordinal()]);
    		ctx.write(word, ONE);
    	}
    }
}