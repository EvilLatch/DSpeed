package com.lachlanbland.dspeed;

import android.content.Context;
import android.graphics.Color;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by lblan on 5/28/2017.
 */

public class SensorData {

    TextView mSpeedView;
    TextView mStrokeRateView;
    TextView mStrokeRateViewZeroCross;
    TextView mHitsView;
    TextView mDistanceView;
    GraphView mGraphView;
    GraphView mRateGraphView;
    GraphView mSpeedGraphView;
    final int numPeaks = 5;
    int currPeak = 0;
    float peaks[] = new float[numPeaks];
    float peakThreshold = 0.5f;
    float minPeakThreshold = 0.3f;
    int peakIndex = 0;
    float currVal = 0.0f;
    float mmaSlow = 0.0f;
    float mmaSuperSlow = 0.0f;
    boolean mmaBelow = false;
    long lastHit = 0;
    float lastStrokeRate = 0.0f;
    float currSpeed = 0.0f;
    float timeOffset = 0.0f;
    float lastTime = 0.0f;
    long prevZeroCross = 0;
    long zeroCross = 0;
    boolean belowZero = false;
    boolean autoStartStop = true;
    public boolean altHitStrategy = true;
    long riseAboveThreshold = 0;
    long lastSample = System.currentTimeMillis();
    float lowPass[] = {0.0f, 0.0f, 0.0f};
    boolean newPeak = false;
    int numHits = 0;
    float accumulatedDist = 0.0f;
    CSVWriter csvWriter = null;

    CSVWriter csvWriterSpeed = null;
    private boolean fileOpen = false;
    File file = null;
    File fileSpeed = null;
    long openTime = 0;
    float xAxis[] = {1.0f, 0.0f, 0.0f};
    float yAxis[] = {0.0f, 1.0f, 0.0f};
    float zAxis[] = {0.0f, 0.0f, 1.0f};
    float xyAxis[] = {0.707f, 0.707f, 0.0f};
    float xzAxis[] = {0.707f, 0.0f, 0.707f};
    float yzAxis[] = {0.0f, 0.707f, 0.707f};
    float xAccum = 0;
    float yAccum = 0;
    float zAccum = 0;
    float xyAccum = 0;
    float xzAccum = 0;
    float yzAccum = 0;
    LineGraphSeries<DataPoint> seriesX = new LineGraphSeries<>();
    LineGraphSeries<DataPoint> seriesY = new LineGraphSeries<>();
    LineGraphSeries<DataPoint> seriesZ = new LineGraphSeries<>();
    LineGraphSeries<DataPoint> seriesSpeed = new LineGraphSeries<>();
    LineGraphSeries<DataPoint> seriesRate = new LineGraphSeries<>();

    static SensorData singleton = new SensorData();

    public void SensorData()
    {
        for(int i = 0; i < numPeaks; i++)
        {
            peaks[i] = peakThreshold;
        }
        //openDataDumpWrite();
    }

    public void addDistance(float distance)
    {
        if(MainActivity.chronoStarted)
        {
            accumulatedDist += distance;
        }
    }

    public void resetDistance()
    {
        accumulatedDist = 0.0f;
    }

    public void openDataDumpWrite() {
        if(fileOpen == false)
        {
            openTime = System.currentTimeMillis();
            File exportDir = new File(Environment.getExternalStorageDirectory(), "DSpeed");
            Log.d("Saving Accel Data", exportDir + ": ");
            Calendar c = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yy.MM.dd HH-mm ");
            String strDate = sdf.format(c.getTime());
            file = new File(exportDir, "Raw_" + strDate + ".csv");
            fileSpeed = new File(exportDir, "Motion_" + strDate + ".csv");
            try {
                csvWriter = new CSVWriter(new FileWriter(file));
                csvWriterSpeed = new CSVWriter(new FileWriter(fileSpeed));
                fileOpen = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void closeDataDumpWrite() {
        try {
            fileOpen = false;
            csvWriter.close();
            csvWriterSpeed.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void appendToFile(float accel[], long timeVal)
    {
        if(fileOpen)
        {
            timeVal -= openTime;
            String arrStr[] = new String[4];
            float seconds = timeVal / 1000.0f;
            arrStr[0] = String.format("%.3f", seconds);
            arrStr[1] = String.format("%.2f",accel[1]);
            arrStr[2] = String.format("%.2f",lastStrokeRate);
            arrStr[3] = String.format("%.3f",accel[0]);

            csvWriter.writeNext(arrStr);
            //Log.d("Saving Accel Data", arrStr[0] + arrStr[1] + arrStr[2]);
        }
    }

    public static SensorData Get()
    {
        return singleton;
    }

    void setSpeedView(TextView view)
    {
        mSpeedView = view;
        seriesX.setColor(Color.MAGENTA);
        seriesY.setColor(Color.RED);
        seriesZ.setColor(Color.BLUE);
        seriesRate.setColor(Color.argb(255, 38, 205, 38));
        seriesSpeed.setColor(Color.argb(255, 205, 38, 38));
    }

    void setGraphView(GraphView view)
    {
        mGraphView = view;
    }
    void setRateGraphView(GraphView view)
    {
        mRateGraphView = view;
    }
    void setSpeed(float speed)
    {
        String speedTextOut = String.format("%.1f", speed);
        currSpeed = speed;
        if(mSpeedView != null)
        {
            mSpeedView.setText(speedTextOut);
        }

    }

    void setAccel(float accel[])
    {
        accel = processAccel(accel);
        accumulatePrimaryMotion(accel);
        accel[1] = -1.0f * accel[1];
        accel[0] = accel[1];
        currVal = 0.93f * currVal + 0.07f * accel[1];
        accel[1] = currVal;
        double timeVal = System.currentTimeMillis();
        appendToFile(accel, System.currentTimeMillis());

        peaks[peakIndex] = Math.max(peaks[peakIndex], currVal);
        mmaSlow = 0.8f * mmaSlow + 0.2f * accel[1];
        mmaSuperSlow = 0.95f * mmaSuperSlow + 0.05f * accel[1];
        DataPoint newDataX = new DataPoint(timeVal, mmaSlow);
        seriesX.appendData(newDataX, true, 2000);
        DataPoint newDataY = new DataPoint(timeVal, mmaSuperSlow);
        seriesY.appendData(newDataY, true, 2000);

        detectHits();
    }

    float[] processAccel(float[] input)
    {
        long timeVal = System.currentTimeMillis();
        long delta = timeVal - lastSample;
        lastSample = timeVal;
        float updateFactor = (float) delta / 10000.0f; // full update over 10s
        updateFactor = Math.min(1.0f, updateFactor);
        lowPass[0] = updateFactor * input[0] + (1 - updateFactor) * lowPass[0];
        lowPass[1] = updateFactor * input[1] + (1 - updateFactor) * lowPass[1];
        lowPass[2] = updateFactor * input[2] + (1 - updateFactor) * lowPass[2];

        input[0] = input[0] - lowPass[0];
        input[1] = input[1] - lowPass[1];
        input[2] = input[2] - lowPass[2];

        return input;
    }

    void init(Context mContext)
    {
        //mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        //mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    void OnResume()
    {
        //mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    void detectHits()
    {
        long currTime = System.currentTimeMillis();
        // to detect a hit, we need to be above the threshold, mmaSlow above mmaSuperSlow and mmaSlow must have previously been below mmaSuperSlow

        // Are we above the threshold?
        if(belowZero && currVal > 0.0f)
        {
            zeroCross = currTime;
            // First time above?
        }
        belowZero = currVal < 0.0f;
        if(currVal > peakThreshold && (currTime - lastHit) > 350)
        {
            if(newPeak == true && mmaBelow == true)
            {
                riseAboveThreshold = currTime;
                newPeak = false;
                mmaBelow = false;
                hitTime(currTime);
            }
        }
        // for the first time?
        if(currVal < peakThreshold)
        {
            if(newPeak == false && mmaBelow == false)
            {
                //currTime = (currTime + riseAboveThreshold) / 2;

                newPeak = true;
                peakIndex = (peakIndex +1) % numPeaks;
                generatePeakThreshold();
                peaks[peakIndex] = peakThreshold * 0.6f;
            }
        }
        if(currVal < 0.0f)
        {
            mmaBelow = true;
        }
        // Did we just leave being above the threshold?
        // -- Generate a 'hit'
        // --  Update the peak value, reset currPeak, calculate new threshold

        if((currTime - lastHit) > 2000)
        {
            lastStrokeRate = 0.0f;
            String speedTextOut = String.format("%.1f", lastStrokeRate);
            if(mStrokeRateView != null)
            {
                mStrokeRateView.setText(speedTextOut);
            }
        }

        if((currTime - lastHit) > 4000)
        {
            // it's been 4 seconds since a hit - set SR to 0
            if(autoStartStop == true && MainActivity.chronoStarted == true)
            {
                MainActivity.StopTiming();
                closeDataDumpWrite();
            }
        }
    }

    private void generatePeakThreshold() {
        peakThreshold = 0.0f;
        float avgMultiplier = 1.0f / numPeaks;
        for(int i = 0; i < numPeaks; i++)
        {
            peakThreshold += 0.4f * peaks[i] * avgMultiplier;
        }
        peakThreshold = Math.max(peakThreshold, minPeakThreshold);
        //setSpeed(peakThreshold);
    }

    private void hitTime(long hitTime) {
        float strokeRate = 60000f / (float)(hitTime - lastHit);
        float strokeRateZeroCross = 60000f / (float)(zeroCross - prevZeroCross);
        lastHit = hitTime;
        prevZeroCross = zeroCross;
        //String speedTextOut = String.format("%.0f", (strokeRate + lastStrokeRate) / 2);
        float outputRate = 0.5f * strokeRate + 0.5f * strokeRateZeroCross;
        if(lastStrokeRate > 2.0f)
        {
            outputRate = 0.5f* outputRate + 0.5f * lastStrokeRate;
        }
        String speedTextOut = String.format("%.0f", outputRate);
        lastStrokeRate = outputRate;
        if(mStrokeRateView != null)
        {
            mStrokeRateView.setText(speedTextOut);
        }
        if(mStrokeRateViewZeroCross != null)
        {
            speedTextOut = String.format("%.0f", strokeRateZeroCross);
            mStrokeRateViewZeroCross.setText(speedTextOut);
        }
        numHits++;
        if(mHitsView != null)
        {
            mHitsView.setText(String.format("%d", numHits));
        }
        if(autoStartStop == true && MainActivity.chronoStarted == false)
        {
            MainActivity.StartTiming();
            openDataDumpWrite();
        }
    }

    void recordPoint(float time)
    {

        lastTime = time;
        time += timeOffset;
        DataPoint newDataZ = new DataPoint(time, lastStrokeRate);
        seriesRate.appendData(newDataZ, true, 6000);
        newDataZ = new DataPoint(time, currSpeed);
        seriesSpeed.appendData(newDataZ, true, 6000);
        if(mSpeedGraphView != null)
        {
            ResetGraphAxies(time);
        }
        if(fileOpen)
        {
            String arrStr[] = new String[9];
            arrStr[0] = String.format("%.3f", lastTime);
            arrStr[1] = String.format("%.2f",currSpeed);
            arrStr[2] = String.format("%.2f",lastStrokeRate);
            arrStr[3] = String.format("%.2f", xAccum);
            arrStr[4] = String.format("%.2f", yAccum);
            arrStr[5] = String.format("%.2f", zAccum);
            arrStr[6] = String.format("%.2f", xyAccum);
            arrStr[7] = String.format("%.2f", xzAccum);
            arrStr[8] = String.format("%.2f", yzAccum);

            csvWriterSpeed.writeNext(arrStr);
            //Log.d("Saving Accel Data", arrStr[0] + arrStr[1] + arrStr[2]);
        }

    }

    public void ResetGraphAxies(float time) {
        if(mSpeedGraphView != null)
        {
            mSpeedGraphView.getViewport().setYAxisBoundsManual(true);
            mSpeedGraphView.getGridLabelRenderer().setHorizontalLabelsVisible(true);
            mSpeedGraphView.getGridLabelRenderer().setHighlightZeroLines(false);
            mSpeedGraphView.getViewport().setMinX(time - 60.0);
            mSpeedGraphView.getViewport().setMaxX(time);
            mSpeedGraphView.getViewport().setScalable(true);
            //mSpeedGraphView.getViewport().setMinY(0.0f);
            //mSpeedGraphView.getViewport().setMaxY(18.0f);
            mSpeedGraphView.getSecondScale().setMinY(20.0d);
            mSpeedGraphView.getSecondScale().setMaxY(80.0d);
            mSpeedGraphView.getGridLabelRenderer().setVerticalLabelsSecondScaleColor(Color.argb(255,20, 160, 20));
            mSpeedGraphView.getGridLabelRenderer().setVerticalLabelsColor(Color.argb(255,160, 20, 20));
        }
    }

    void resetData()
    {
        resetDistance();
        numHits = 1;
        if(mHitsView != null)
        {
            mHitsView.setText(String.format("%d", numHits));
        }
        timeOffset += lastTime;
        lastTime = 0.0f;
        DataPoint newDataZ = new DataPoint(timeOffset, 0.0f);
        seriesRate.appendData(newDataZ, true, 6000);
        seriesSpeed.appendData(newDataZ, true, 6000);
        ResetGraphAxies(timeOffset);
    }

    void cutTimePeriod()
    {
        double lastStored = lastTime + timeOffset + 0.1f;
        DataPoint newDataZ = new DataPoint(lastStored, 0.0f);
        seriesRate.appendData(newDataZ, true, 6000);
        seriesSpeed.appendData(newDataZ, true, 6000);
        timeOffset += 5.0f;
    }

    void adjustHorizontalScale(float factor)
    {
        mSpeedGraphView.getViewport().setXAxisBoundsManual(true);
        mSpeedGraphView.getViewport().setScrollable(false);
        mSpeedGraphView.getViewport().setScalable(false);
        float minX =  (float) mSpeedGraphView.getViewport().getMinX(false);
        float maxX =  (float) mSpeedGraphView.getViewport().getMaxX(false);
        float mid = (minX + maxX) / 2.0f;
        float halfrange = (maxX - minX) * factor * 0.5f;
        mSpeedGraphView.getViewport().setMinX(mid - halfrange);
        mSpeedGraphView.getViewport().setMaxX(mid + halfrange);

        mSpeedGraphView.addSeries(seriesZ);
        //Toast.makeText(mSpeedGraphView.getContext(), "About button pressed", Toast.LENGTH_SHORT).show();
        mSpeedGraphView.removeSeries(seriesZ);

    }

    void adjustHorizontalPosition(float factor)
    {
        float minX =  (float) mSpeedGraphView.getViewport().getMinX(false);
        float maxX =  (float) mSpeedGraphView.getViewport().getMaxX(false);
        float range = maxX - minX;
        mSpeedGraphView.getViewport().setMinX(minX + factor * range);
        mSpeedGraphView.getViewport().setMaxX(maxX + factor * range);
        mSpeedGraphView.addSeries(seriesZ);
        //Toast.makeText(mSpeedGraphView.getContext(), "About button pressed", Toast.LENGTH_SHORT).show();
        mSpeedGraphView.removeSeries(seriesZ);
    }

    void accumulatePrimaryMotion(float input[])
    {
        // for each axis, dot with input and square and average
        float temp = vec3dot(input, xAxis);
        xAccum = 0.99f * xAccum + 0.1f * temp*temp;
        temp = vec3dot(input, yAxis);
        yAccum = 0.99f * yAccum + 0.1f * temp*temp;
        temp = vec3dot(input, xAxis);
        zAccum = 0.99f * zAccum + 0.1f * temp*temp;
        temp = vec3dot(input, xyAxis);
        xyAccum = 0.99f * xyAccum + 0.1f * temp*temp;
        temp = vec3dot(input, xzAxis);
        xzAccum = 0.99f * xzAccum + 0.1f * temp*temp;
        temp = vec3dot(input, yzAxis);
        yzAccum = 0.99f * yzAccum + 0.1f * temp*temp;
    }

    static float vec3dot(float input[], float axis[])
    {
        return input[0] * axis[0] + input[1] * axis[1] + input[2] * axis[2];
    }
}
