package com.puuga.opennote;

import android.Manifest;
import android.app.Dialog;
import android.app.SearchManager;
import android.app.assist.AssistContent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.facebook.appevents.AppEventsLogger;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.puuga.opennote.helper.SettingHelper;
import com.puuga.opennote.manager.APIService;
import com.puuga.opennote.model.Message;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.fabric.sdk.android.Fabric;
import retrofit.Call;
import retrofit.Callback;
import retrofit.Response;
import retrofit.Retrofit;

public class MainActivity extends AppCompatActivity implements
        MapFragment.OnFragmentReadyListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

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
    private ViewPager mViewPager;

    // SharedPreferences
    SettingHelper settingHelper;

    // Google Analytic
    Tracker mTracker;

    // Google API
    GoogleApiClient mGoogleApiClient;
    LocationRequest mLocationRequest;
    Location mCurrentLocation;
    Location mLastLocation;
    // Request code to use when launching the resolution activity
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    // Unique tag for the error dialog fragment
    private static final String DIALOG_ERROR = "dialog_error";
    // Bool to track whether the app is already resolving an error
    private boolean mResolvingError = false;
    // REQUEST_LOCATION code
    private static final int REQUEST_LOCATION = 2;

    // Retrofit
    APIService service;

    // widget
    FloatingActionButton fab;

    Dialog denyLocationPermissionDialog;
    Dialog requestLocationPermissionDialog;
    Dialog submitMessageDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_main);

        initSharedPreferences();

        initGoogleAnalytic();

        initInstances();

        initRetrofit();

        createLocationRequest();
        buildGoogleApiClient();

        initPager();
    }

    private void initPager() {
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), fab);

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
    }

    private void initRetrofit() {
        AnalyticsApplication application = (AnalyticsApplication) getApplication();
        service = application.getAPIService();
    }

    private void initInstances() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
                makeSubmitMessageDialog().show();
                if (submitMessageDialog == null) {
                    submitMessageDialog = makeSubmitMessageDialog();
                }
                submitMessageDialog.show();
            }
        });
    }

    void submitMessage(String message, final View view) {
        Log.d("submit", message);
        Log.d("location", mCurrentLocation.toString());

        String lat = String.valueOf(mCurrentLocation.getLatitude());
        String lng = String.valueOf(mCurrentLocation.getLongitude());

        Call<Message> call = service.submitMessage(settingHelper.getAppId(), message, lat, lng);
        call.enqueue(new Callback<Message>() {
            @Override
            public void onResponse(Response<Message> response, Retrofit retrofit) {
                Message message = response.body();
                Snackbar.make(fab, "Submitted", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                Log.d("submitted", message.toString());
                loadMessage();

                EditText edtMessage = ((EditText) view.findViewById(R.id.edt_message));
                edtMessage.setText("");
            }

            @Override
            public void onFailure(Throwable t) {

            }
        });
    }

    void loadMessage() {
        Call<Message[]> call = service.loadMessages();
        call.enqueue(new Callback<Message[]>() {
            @Override
            public void onResponse(Response<Message[]> response, Retrofit retrofit) {
                try {
                    response.errorBody().string();
                    Log.d("response_error", response.errorBody().string());
                } catch (Exception ignored) {
                }
                Message[] messages = response.body();
//                Toast.makeText(getApplicationContext(), "response", Toast.LENGTH_SHORT).show();
//                Snackbar.make(fab, "Messages loaded", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
                Log.d("response", "messages count:" + String.valueOf(messages.length));
                for (Message message : messages) {
                    Log.d("response", "messages :" + message.getMessage());
                }
                makeMarker(messages);
                setAdapter(messages);
            }

            @Override
            public void onFailure(Throwable t) {
                Log.d("response_failure", t.getMessage());
            }
        });
    }

    void makeMarker(Message[] messages) {
        mSectionsPagerAdapter.mapFragment.makeMarkers(messages);
    }

    void setAdapter(Message[] messages) {
        List<Message> messageList = new ArrayList<>(Arrays.asList(messages));
        mSectionsPagerAdapter.messageFragment.setAdapter(messageList);
    }

    private Dialog makeDenyLocationPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(R.string.message_deny_location_permission)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }

    private Dialog makeRequestLocationPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(R.string.message_request_location_permission)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                REQUEST_LOCATION);
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }

    private Dialog makeSubmitMessageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        // Get the layout inflater
        LayoutInflater inflater = getLayoutInflater();
        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        final View v = inflater.inflate(R.layout.dialog_submit_message, null);
        builder.setView(v)
                .setPositiveButton(R.string.publish, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        EditText edtMessage = ((EditText) v.findViewById(R.id.edt_message));
                        String message = edtMessage.getText().toString();
                        submitMessage(message, v);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {  // more about this later
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Logs 'install' and 'app activate' App Events.
        AppEventsLogger.activateApp(this);

        // check login
        if (!settingHelper.isFacebookLogin()) {
            Intent i = new Intent(this, FacebookLoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Logs 'app deactivate' App Event.
        AppEventsLogger.deactivateApp(this);

        stopLocationUpdates();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_RESOLVE_ERROR) {
            mResolvingError = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mGoogleApiClient.isConnecting() &&
                        !mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.connect();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length == 1
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // We can now safely use the API we requested access to
                LocationServices.FusedLocationApi.requestLocationUpdates(
                        mGoogleApiClient, mLocationRequest, this);
            } else {
                // Permission was denied or request was cancelled
                if (denyLocationPermissionDialog == null) {
                    denyLocationPermissionDialog = makeDenyLocationPermissionDialog();
                }
                denyLocationPermissionDialog.show();
            }
        }
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
        switch (id) {
            case R.id.action_settings:
                return true;
            case R.id.action_logout:
                Intent i = new Intent(this, FacebookLoginActivity.class);
                startActivity(i);
                return true;
            case R.id.action_profile:
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(2000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    protected void startLocationUpdates() {
        if (ActivityCompat
                .checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Check Permissions Now
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Display UI and wait for user interaction
                if (requestLocationPermissionDialog == null) {
                    requestLocationPermissionDialog = makeRequestLocationPermissionDialog();
                }
                requestLocationPermissionDialog.show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_LOCATION);
            }
        } else {
            // permission has been granted, continue as usual
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient, mLocationRequest, this);
        }
    }

    protected void stopLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, this);
        }
    }

    private void initSharedPreferences() {
        AnalyticsApplication application = (AnalyticsApplication) getApplication();
        settingHelper = application.getSettingHelper();
    }

    private void initGoogleAnalytic() {
        // Obtain the shared Tracker instance.
        AnalyticsApplication application = (AnalyticsApplication) getApplication();
        mTracker = application.getDefaultTracker();
    }

    @Override
    public void OnFragmentReady() {
        loadMessage();

    }

    @Override
    public void onConnected(Bundle bundle) {
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (connectionResult.hasResolution()) {
            try {
                mResolvingError = true;
                connectionResult.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            // Show dialog using GoogleApiAvailability.getErrorDialog()
            // showErrorDialog(result.getErrorCode());
            Log.d("GoogleAPI", "code: " + connectionResult.getErrorCode());
            mResolvingError = true;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (mLastLocation == null) {
            mLastLocation = location;
            mSectionsPagerAdapter.mapFragment.moveCameraToMyLocation(location, 15, true);
        }
        mCurrentLocation = location;
        if (mCurrentLocation.distanceTo(mLastLocation) > 100) {
            // change position
            mLastLocation = location;
            // load new message
            loadMessage();
        }
        Log.d("location", location.toString());
    }

    @Override
    public void onProvideAssistData(Bundle data) {
        String q = String.valueOf(mCurrentLocation.getLatitude()) + "," + String.valueOf(mCurrentLocation.getLongitude());
        data.putString(SearchManager.QUERY, q);
        super.onProvideAssistData(data);
    }

    @Override
    public void onProvideAssistContent(AssistContent outContent) {


        if (mCurrentLocation.hasAccuracy() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                JSONObject geo = new JSONObject()
                        .put("@type", "GeoCoordinates")
                        .put("latitude", String.valueOf(mCurrentLocation.getLatitude()))
                        .put("longitude", String.valueOf(mCurrentLocation.getLongitude()));
                JSONObject structuredJson = new JSONObject()
                        .put("@type", "Place")
                        .put("geo", geo);
                Log.d("json_provider", geo.toString());
                outContent.setStructuredData(structuredJson.toString());

            } catch (JSONException e) {
                Log.d("json_error", e.getMessage());
            }
        }

        super.onProvideAssistContent(outContent);
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        FloatingActionButton fab;

        MapFragment mapFragment;
        MessageFragment messageFragment;

        public SectionsPagerAdapter(FragmentManager fm, FloatingActionButton fab) {
            super(fm);
            this.fab = fab;

            mapFragment = MapFragment.newInstance();
            messageFragment = MessageFragment.newInstance();
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            switch (position) {
                case 0:
                    fab.hide();
                    return mapFragment;
                default:
                    fab.show();
                    return messageFragment;
            }
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "MAP";
                case 1:
                    return "MESSAGES";
            }
            return null;
        }
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

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            TextView textView = (TextView) rootView.findViewById(R.id.section_label);
            textView.setText(getString(R.string.section_format, getArguments().getInt(ARG_SECTION_NUMBER)));
            return rootView;
        }
    }
}
