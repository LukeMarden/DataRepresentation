package com.example.datarepresentation;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;


public class DatePresentation extends Activity implements SensorEventListener {
    Interpreter linearModel;
    Boolean running;
    private SensorManager sensorManager;
    private TextView timer;
    private TextView prediction;
    private TextView pace;
    private EditText target;
    private Button startButton;
    private Button stopButton;
    private FileWriter writer;
    int stepCounter;
    int predictionValue;
    long startTimeMilli;
    final String TAG = "SensorLog";
    int time = 0;

    long MillisecondTime, StartTime, TimeBuff, UpdateTime = 0L ;
    Handler handler;
    int Seconds, Minutes, MilliSeconds ;
    double[] values = new double[3];

    @Override
    public void onCreate(Bundle savedInstanceState){
        PackageManager pm = getPackageManager();
        try {
            linearModel = new Interpreter(loadModelFile());
        }catch (Exception ex){
            ex.printStackTrace();
        }
        System.out.println("doInference(new double[]{3,13,2}) = " + doInference(new double[]{3,13,2}));

        super.onCreate(savedInstanceState);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        handler = new Handler() ;
        setContentView(R.layout.activity_main);//Layout loaded from activity_main.xml
        timer = findViewById(R.id.timer);
        prediction = findViewById(R.id.prediction);
        prediction.setText("00:00:00");
        timer.setText("00:00:00");
        pace = findViewById(R.id.pace);
        pace.setBackgroundColor(Color.YELLOW);
        target = findViewById(R.id.target);

        startButton = findViewById(R.id.start);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStartClicked();
            }
        });
        stopButton = findViewById(R.id.stop);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStopClicked();
            }
        });
        running=false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
//        System.out.println("Minutes = " + (Minutes*60+Seconds));
//        System.out.println("time = " + time);
        if (Minutes*60+Seconds==time){
            switch(event.sensor.getType()) {
                case Sensor.TYPE_STEP_DETECTOR:
                    values[0]++;
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                    values[1]+=(Math.sqrt(
                            event.values[0]*event.values[0]+
                            event.values[1]*event.values[1]+
                            event.values[2]*event.values[2])
                    );
                    values[1]/=2;
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    values[2]+=(Math.sqrt(
                            event.values[0]*event.values[0]+
                            event.values[1]*event.values[1]+
                            event.values[2]*event.values[2])
                    );
                    values[2]/=2;
                    break;
            }
        }
        else{
            if(values[0]==0||values[1]==0||values[2]==0){
                prediction.setText("00:00:00");
            }
            else{
                predictionValue = Math.round(doInference(values));
                prediction.setText("00:" + String.valueOf(predictionValue/60) + ":" + String.valueOf(predictionValue%60));
                if(predictionValue>1.1*Double.parseDouble(target.getText().toString())){ //too slow
                    pace.setBackgroundColor(Color.GREEN);
                    pace.setText("SPEED UP!");
                }
                else if(predictionValue<0.9*Double.parseDouble(target.getText().toString())){ //too fast
                    pace.setBackgroundColor(Color.RED);
                    pace.setText("SLOW DOWN!");
                }
                else{
                    pace.setBackgroundColor(Color.YELLOW);
                    pace.setText("GOOD JOB!");
                }
            }
            switch(event.sensor.getType()) {
                case Sensor.TYPE_STEP_DETECTOR:
                    values[0]=1;
                    values[1]=0;
                    values[2]=0;
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                    values[0]=0;
                    values[1]=(Math.sqrt(
                            event.values[0]*event.values[0]+
                                    event.values[1]*event.values[1]+
                                    event.values[2]*event.values[2])
                    );
                    values[2]=0;
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    values[0]=0;
                    values[1]=0;
                    values[2]=(Math.sqrt(
                            event.values[0]*event.values[0]+
                                    event.values[1]*event.values[1]+
                                    event.values[2]*event.values[2])
                    );
                    break;
            }
            time = Minutes*60+Seconds;
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    protected void onStartClicked() {
        if (target.getText().toString().matches("")) {
            Toast.makeText(this, "Please enter a target time", Toast.LENGTH_SHORT).show();
            return;
        }//activates if the user tries to start the timer without a target time
        //taken from https://stackoverflow.com/questions/6290531/how-do-i-check-if-my-edittext-fields-are-empty
        target.setVisibility(View.INVISIBLE);
        startTimeMilli = System.currentTimeMillis();
        if (!running){
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR),
                    SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                    SensorManager.SENSOR_DELAY_UI);

            stepCounter = 0;
            StartTime = SystemClock.uptimeMillis();
            TimeUnit.MILLISECONDS.toSeconds(StartTime);
            handler.postDelayed(runnable, 0);
            running = true;

        }
    }

    protected void onStopClicked() {
        if(running){
            target.setVisibility(View.VISIBLE);
            sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR));
            sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
            sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
            String[] sensors = {Sensor.STRING_TYPE_ACCELEROMETER, Sensor.STRING_TYPE_STEP_DETECTOR, Sensor.STRING_TYPE_GYROSCOPE};

            TimeBuff += MillisecondTime;
            MillisecondTime = 0L ;
            StartTime = 0L ;
            TimeBuff = 0L ;
            UpdateTime = 0L ;
            Seconds = 0 ;
            Minutes = 0 ;
            MilliSeconds = 0 ;
            stepCounter = 0;

            prediction.setText("00:00:00");
            timer.setText("00:00:00");
            pace.setBackgroundColor(Color.YELLOW);
            handler.removeCallbacks(runnable);
            running=false;
        }

    }

    public Runnable runnable = new Runnable() {

        public void run() {

            MillisecondTime = SystemClock.uptimeMillis() - StartTime;

            UpdateTime = TimeBuff + MillisecondTime;

            Seconds = (int) (UpdateTime / 1000);

            Minutes = Seconds / 60;

            Seconds = Seconds % 60;

            MilliSeconds = (int) (UpdateTime % 1000);

            timer.setText("" + Minutes + ":"
                    + String.format("%02d", Seconds) + ":"
                    + String.format("%03d", MilliSeconds));

            handler.postDelayed(this, 0);
        }

    };


//    https://medium.com/analytics-vidhya/running-ml-models-in-android-using-tensorflow-lite-e549209287f0
    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor=this.getAssets().openFd("linear.tflite");
        FileInputStream inputStream=new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();
        long startOffset=fileDescriptor.getStartOffset();
        long declareLength=fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startOffset,declareLength);
    }

//    https://medium.com/analytics-vidhya/running-ml-models-in-android-using-tensorflow-lite-e549209287f0
    private float doInference(double[] inputs) {
        float[] inputVal=new float[3];
        inputVal[0]=(float)inputs[0];
        inputVal[1]=(float)inputs[1];
        inputVal[2]=(float)inputs[2];
        float[][] output=new float[1][1];
        linearModel.run(inputVal,output);
        return output[0][0];
    }

}
