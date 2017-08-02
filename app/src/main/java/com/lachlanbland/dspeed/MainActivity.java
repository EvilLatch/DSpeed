package com.lachlanbland.dspeed;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.SystemClock;
import android.support.design.widget.TabLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;

import java.util.HashMap;
import java.util.Map;

import static android.os.Looper.getMainLooper;
import static com.lachlanbland.dspeed.R.id.fab;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener, DeviceListFragment.DeviceActionListener {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    public static final String TAG = "DSpeed";
    private ViewPager mViewPager;
    static private SensorManager mSensorManager;
    static private LocationManager mLocationManager;
    static private Sensor mAccelSensor;
    static private Sensor mVelocitySensor;
    static public String speedText;
    static public Chronometer chrono;
    static public Chronometer chronoPaused;
    static public boolean chronoStarted = false;
    static public Button left;
    static public Button right;
    static public Button zoomIn;
    static public Button zoomOut;
    MyBroadcastReceiver mReceiver;
    boolean gpsEnabled = false;
    SensorData sensorData = SensorData.Get();
    private final IntentFilter intentFilter = new IntentFilter();
    WifiP2pManager mManager;
    private boolean retryChannel = false;
    boolean mP2PWifiEnabled = true;
    final int SERVER_PORT = 18686;
    WifiP2pManager.Channel mChannel;
    Location lastLocation = null;
    final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE = 1;
    final int MY_PERMISSIONS_REQUEST_READ_STORAGE = 2;
    final int MY_PERMISSIONS_REQUEST_INTERNET = 3;
    final int MY_PERMISSIONS_REQUEST_READ_PHONE = 4;
    final int MY_PERMISSIONS_REQUEST_CORSE_LOCATION = 5;
    final int MY_PERMISSIONS_REQUEST_FINE_LOCATION = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        CheckSetPermissions();
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager()
                        .findFragmentById(R.id.frag_list);
                fragment.onInitiateDiscovery();
                mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(MainActivity.this, "Discovery Initiated",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(MainActivity.this, "Discovery Failed : " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
                //sensorData.peakThreshold += 0.25f;
                //if (sensorData.peakThreshold > 4.0f) {
                //    sensorData.peakThreshold = 0.25f;
                //}
                //Snackbar.make(view, "Setting threshold to: " + sensorData.peakThreshold, Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        //li=new currSpeed();
        if (gpsEnabled) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }

        //  Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        //mReceiver = new MyBroadcastReceiver(this);
        this.onLocationChanged(null);
        onResume();

    }

    public void setIsWifiP2pEnabled(boolean enabled)
    {
        mP2PWifiEnabled = enabled;
    }

    public void startRegistration() {
        //  Create a string map containing information about your service.
        Map record = new HashMap();
        record.put("listenport", String.valueOf(SERVER_PORT));
        record.put("buddyname", "John Doe" + (int) (Math.random() * 1000));
        record.put("available", "visible");

        // Service information.  Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        WifiP2pDnsSdServiceInfo serviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance("_test", "_presence._tcp", record);

        // Add the local service, sending the service info, network channel,
        // and listener that will be used to indicate success or failure of
        // the request.
        mManager.addLocalService(mChannel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                // Command successful! Code isn't necessarily needed here,
                // Unless you want to update the UI or add logging statements.
            }

            @Override
            public void onFailure(int arg0) {
                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
            }
        });
    }



    private void CheckSetPermissions() {
        int writeCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int readCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE);
        int internetCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.INTERNET);
        int phoneCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_PHONE_STATE);
        int coarseCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION);
        int fineCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        if(writeCheck != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_STORAGE);
        }
        if(readCheck != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_READ_STORAGE);
        }
        if(internetCheck != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.INTERNET},
                    MY_PERMISSIONS_REQUEST_INTERNET);
        }
        if(phoneCheck != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE},
                    MY_PERMISSIONS_REQUEST_READ_PHONE);
        }
        if(coarseCheck != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    MY_PERMISSIONS_REQUEST_CORSE_LOCATION);
        }
        if(fineCheck != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_FINE_LOCATION);
        }
        else {
            gpsEnabled = true;
        }
        Log.d("Permissions Check Write", String.valueOf(writeCheck));
        Log.d("Permissions Check Read", String.valueOf(readCheck));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
            case MY_PERMISSIONS_REQUEST_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    gpsEnabled = true;
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {
                    gpsEnabled = false;
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
    @Override
    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelSensor, SensorManager.SENSOR_DELAY_GAME);
        if(mReceiver == null)
        {
            mReceiver = new MyBroadcastReceiver(mManager, mChannel, this);
            registerReceiver(mReceiver, intentFilter);
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        if(mReceiver != null)
        {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }

    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
        //SensorEvent.values[0]	Acceleration force along the x axis (excluding gravity).	m/s2
        //SensorEvent.values[1]	Acceleration force along the y axis (excluding gravity).
        //SensorEvent.values[2]	Acceleration force along the z axis (excluding gravity).
        sensorData.setAccel(event.values);
    }
    @Override
    public void onLocationChanged(Location location) {

        if (location == null) {
            // if you can't get currSpeed because reasons :)
            speedText = "00 km/h";
            float speed = 0.0f;
            sensorData.setSpeed(speed);
            lastLocation = null;
        } else {
            //int currSpeed=(int) ((location.getSpeed()) is the standard which returns meters per second. In this example i converted it to kilometers per hour

            float speed = (float) ((location.getSpeed() * 3600) / 1000);
            if(lastLocation != null)
            {
                float distance = location.distanceTo(lastLocation);
                sensorData.addDistance(distance);
                if(SensorData.Get().mDistanceView != null)
                {
                    SensorData.Get().mDistanceView.setText(String.format("%.0f", sensorData.accumulatedDist));
                }
            }
            lastLocation = location;
            sensorData.setSpeed(speed);
            //String.format(speedText, "0.2f km/h", currSpeed);
            //speedText = currSpeed + " km/h";
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
    }
    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub
    }
    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            TextView textView = (TextView) rootView.findViewById(R.id.section_label);
            //textView.setText(getString(R.string.section_format, getArguments().getInt(ARG_SECTION_NUMBER)));
            TextView speedText = (TextView) rootView.findViewById(R.id.speed);
            SensorData.Get().mStrokeRateView = (TextView) rootView.findViewById(R.id.strokeRate);
            SensorData.Get().mStrokeRateViewZeroCross = (TextView) rootView.findViewById(R.id.strokeRateZeroCross);
            SensorData.Get().mHitsView = (TextView) rootView.findViewById(R.id.hitCount);
            SensorData.Get().mDistanceView = (TextView) rootView.findViewById(R.id.distance);
            SensorData.Get().setSpeedView(speedText);
            GraphView speedView = (GraphView) rootView.findViewById(R.id.speedGraph);
            SensorData.Get().mSpeedGraphView = speedView;
            speedView.getViewport().setScalable(true);
            speedView.getViewport().setScalableY(true);
            speedView.getViewport().setScrollable(true);
            speedView.getViewport().setScrollableY(true);

            speedView.addSeries(SensorData.Get().seriesSpeed);
            speedView.getSecondScale().addSeries(SensorData.Get().seriesRate);
            speedView.getSecondScale().setMinY(20.0d);
            speedView.getSecondScale().setMaxY(80.0d);
            chrono = (Chronometer) rootView.findViewById(R.id.chronometer2);
            chronoPaused = (Chronometer) rootView.findViewById(R.id.chronometerRest);
            chrono.setBackgroundColor(Color.argb(255, 255, 128, 128));
            chrono.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(chronoStarted == true)
                    {
                        StopTiming();
                    }
                    else
                    {
                        StartTiming();
                    }
                }
            });
            chrono.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
                @Override
                public void onChronometerTick(Chronometer chronometer) {
                    float elapsedSeconds = (float)(SystemClock.elapsedRealtime() - chronometer.getBase()) / 1000.0f;
                    SensorData.Get().recordPoint(elapsedSeconds);
                    //SensorData.Get().adjustHorizontalScale(0.95f);
                }
            });

            ConnectButtons(rootView);
            SensorData.Get().ResetGraphAxies(0.0f);
            return rootView;
        }
    }

    public static void StartTiming() {
        SensorData.Get().resetData();
        if(chrono != null)
        {
            chrono.setBase(SystemClock.elapsedRealtime());
            chrono.start();
            chrono.setBackgroundColor(Color.argb(255, 128, 255, 128));
            chronoStarted = true;
            chronoPaused.stop();
            chronoPaused.setBackgroundColor(Color.argb(255, 255, 128, 128));
        }
    }

    public static void StopTiming() {
        if(chrono != null)
        {
            chrono.stop();
            chronoStarted = false;
            chrono.setBackgroundColor(Color.argb(255, 255, 128, 128));
            SensorData.Get().cutTimePeriod();
            chronoPaused.setBase(SystemClock.elapsedRealtime());
            chronoPaused.start();
            chronoPaused.setBackgroundColor(Color.argb(255, 128, 255, 128));

        }
    }

    public static void ConnectButtons(View rootView) {
        left = (Button) rootView.findViewById(R.id.moveLeft);
        right = (Button) rootView.findViewById(R.id.moveRight);
        zoomIn = (Button) rootView.findViewById(R.id.zoomIn);
        zoomOut = (Button) rootView.findViewById(R.id.zoomOut);
        left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SensorData.Get().adjustHorizontalPosition(-0.1f);
            }
        });
        right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SensorData.Get().adjustHorizontalPosition(0.1f);
            }
        });
        zoomIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SensorData.Get().adjustHorizontalScale(0.9f);
                            }
        });
        zoomOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SensorData.Get().adjustHorizontalScale(1.1f);
            }
        });
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return 1;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "SECTION 1";
                case 1:
                    return "SECTION 2";
                case 2:
                    return "SECTION 3";
            }
            return null;
        }
    }

    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetData() {
        DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_list);
        //DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager()
        //        .findFragmentById(R.id.frag_detail);
        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
        //if (fragmentDetails != null) {
        //    fragmentDetails.resetViews();
        //}
    }

  //  @Override
    public void showDetails(WifiP2pDevice device) {
        //DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
        //fragment.showDetails(device);

    }

  //  @Override
    public void connect(WifiP2pConfig config) {
        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(MainActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

   // @Override
    public void disconnect() {
        //final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager()
        //        .findFragmentById(R.id.frag_detail);
        //fragment.resetViews();
        mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);

            }

            @Override
            public void onSuccess() {
                //fragment.getView().setVisibility(View.GONE);
            }

        });
    }

  //  @Override
    public void onChannelDisconnected() {
        // we will try once more
        if (mManager != null && !retryChannel) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            resetData();
            retryChannel = true;
            mManager.initialize(this, getMainLooper(), null);
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }

  //  @Override
    public void cancelDisconnect() {

        /*
         * A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (mManager != null) {
            final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager()
                    .findFragmentById(R.id.frag_list);
            if (fragment.getDevice() == null
                    || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else {
                if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
                        || fragment.getDevice().status == WifiP2pDevice.INVITED) {

                    mManager.cancelConnect(mChannel, new WifiP2pManager.ActionListener() {

                        @Override
                        public void onSuccess() {
                            Toast.makeText(MainActivity.this, "Aborting connection",
                                    Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(int reasonCode) {
                            Toast.makeText(MainActivity.this,
                                    "Connect abort request failed. Reason Code: " + reasonCode,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }

    }
}
