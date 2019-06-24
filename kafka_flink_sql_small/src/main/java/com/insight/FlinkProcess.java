//Read data from kafka and write to postgresql

package com.insight;


import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.operators.JoinOperator;
import org.apache.flink.api.common.accumulators.IntCounter;
import org.apache.flink.api.common.functions.*;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.tuple.*;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.WindowedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer010;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer010;
import org.apache.flink.util.Collector;
import org.apache.flink.streaming.api.datastream.*;
import org.apache.flink.streaming.connectors.cassandra.CassandraSink;
//import org.apache.flink.api.java.io.jdbc.JDBCOutputFormat;
import org.apache.flink.types.Row;
import org.apache.flink.api.java.tuple.*;



//import com.insight.HostURLs.*;


public class FlinkProcess {
    public static void main(String[] args) throws Exception {
        double[] w30= new double[]{      //The x^T w+ b, prediction the classification 0,1,2..29
                -0.30989978,  0.07340665, -0.00070422,  0.01139284,  0.53738595,
                0.07076653, -0.05718759, -0.03139225, -0.16933235, -0.1310537 ,
                -0.47741088,  0.1240793 , -0.24477353, -0.30605179, -0.70085175,
                -0.13303649, -0.25741649, -0.04488725, -0.01285778, -0.02545355,
                -0.1440509 ,  0.15047008,  0.0355463 ,  0.004384  ,  0.06221661,
                -0.13903214,  0.00915609, -0.09659167,  0.0929125 ,  0.06944448};
        double b= -6.78630449;

        // create execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // configure event-time characteristics
        //env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        // generate a Watermark every second
        //env.getConfig().setAutoWatermarkInterval(1000);

        // create new property of Flink
        // set Zookeeper and Kafka url
        Properties properties = new Properties();
        properties.setProperty("zookeeper.connect", "ec2-34-218-113-11.us-west-2.compute.amazonaws.com:2181,ec2-35-161-124-47.us-west-2.compute.amazonaws.com:2181,ec2-52-34-133-46.us-west-2.compute.amazonaws.com:2181");
        properties.setProperty("bootstrap.servers", "ec2-34-218-113-11.us-west-2.compute.amazonaws.com:9092,ec2-35-161-124-47.us-west-2.compute.amazonaws.com:9092,ec2-52-34-133-46.us-west-2.compute.amazonaws.com:9092");
        properties.setProperty("group.id", "myGroup");                 // Consumer group ID
        //properties.setProperty("auto.offset.reset", "earliest");       // Always read topic from start

        // read 'topic' from Kafka producer: Kafka topic is "wikiInput"
        FlinkKafkaConsumer010<String> kafkaConsumer = new FlinkKafkaConsumer010<>("transactions_forward",
                new SimpleStringSchema(), properties);

        // convert kafka stream to data stream
        DataStream<String> rawInputStream = env.addSource(kafkaConsumer);

        //Tranfrom: string -> Row*****************************
        DataStream<Row> transformStream= rawInputStream.map(new MapFunction<String, Row>() {
            @Override
            public Row map(String value) {

                Row row = new Row(38);
                String[] inputs = value.split(",");
                double weightedSum = 0;
                double timeProduced = Double.parseDouble( inputs[33] );
                long epoch = System.currentTimeMillis();
                double timeProcessed = (double) epoch / 1000.00;
                double latency = timeProcessed - timeProduced;
                System.out.println("The latency is"+ latency );

                for (int i = 0; i <= 37; ++i) {
                    if (i <= 1) {
                        row.setField(i, Integer.parseInt(inputs[i]) ); //key and index
                    }
                    else if (i == 2) {
                        row.setField(i, inputs[i]);
                    } //the phone number
                    else if (i <= 32) { //30 features, 3, 4,..32, get the x_i, calculate weightedSum, write x_i to row
                        double x_i = Double.parseDouble(inputs[i]);
                        weightedSum += x_i * w30[i - 3];
                        row.setField(i, x_i);
                    }
                    else if (i == 33) {
                        row.setField(i, timeProduced);
                    }
                    else if (i == 34) {
                        row.setField(i, timeProcessed);
                    }
                    else if(i==35) {
                        row.setField(i, latency);
                    }
                    else if (i == 36) {
                        weightedSum+= b;
                        System.out.println("The prediction is"+ weightedSum );
                        if (weightedSum > 0) {
                            row.setField(i, 1);
                        }
                        else {
                            row.setField(i, -1);
                        }
                    }
                    else{
                        row.setField(i, 9);//reply of constomer
                    }
                }
                return row;
            }
        });

        //Sink: to postgresql ****************************************
        transformStream.addSink(new PostgreSQLSink() );

        System.out.println("I am running");
        env.execute("Kafka to Flink to PostgreSQL Streaming");

    }


}