package com.world.timezone.converter.core;


import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Toast;

import com.world.timezone.converter.model.TimeZoneModel;
import com.world.timezone.core.BuildConfig;
import com.world.timezone.core.R;

import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Vector;

public class MainActivity extends ActionBarListActivity {

    static final String TAG = BuildConfig.APPLICATION_ID + ".MainActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    public static Map<String, Typeface> fonts = new HashMap<String, Typeface>();
    public static String KEY_CITIES = TAG + "cities";
    static SharedPreferences timeZonePreferences;
    static String VALUE_CITIES;
    static String IS_SET = "isset";
    static String CURRENT_LOCATION = "current_city";
    static ListViewAdapter listViewAdapter;
    private static String currentLocation;
    private TimeZoneImpl timeZoneImpl;
    private Context context;
    private String defValue = "New York, NY;" + "Sydney;" + "Paris;";
    private LocationManager locationManager;
    private String bestProvider;
    private boolean doubleBackToExitPressedOnce = false;

    /**
     * Method to update Preferences
     */
    public static void updatePreference() {
        String newPreference = null;
        if (listViewAdapter.getCount() > 0) {
            for (int i = 0; i < listViewAdapter.getCount(); i++) {
                if (!listViewAdapter.getItem(i).getCity().equals(currentLocation)) {
                    if (newPreference == null) {
                        newPreference = listViewAdapter.getItem(i).getCity() + ";";
                    } else {
                        newPreference = newPreference + listViewAdapter.getItem(i).getCity() + ";";
                    }
                }
            }
        }

        timeZonePreferences.edit().putString(KEY_CITIES, newPreference).commit();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;

        registerForContextMenu(getListView());

        timeZonePreferences = getPreferences(Context.MODE_PRIVATE);

        if (!timeZonePreferences.getBoolean(MainActivity.IS_SET, false)) {
            MainActivity.VALUE_CITIES = defValue;
            timeZonePreferences.edit().putString(MainActivity.KEY_CITIES, MainActivity.VALUE_CITIES).commit();
            timeZonePreferences.edit().putBoolean(MainActivity.IS_SET, true).commit();
        } else {
            MainActivity.VALUE_CITIES = timeZonePreferences.getString(KEY_CITIES, defValue);
        }

        getListView().setItemsCanFocus(true);

        //List view on Touch listener
        getListView().setOnItemSelectedListener(new OnItemSelectedListener() {

            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                view.setBackgroundColor(context.getResources().getColor(R.color.holo_blue_light));
            }

            public void onNothingSelected(AdapterView<?> parent) {

            }

        });

        getListView().setOnItemClickListener(new OnItemClickListener() {

            public void onItemClick(AdapterView<?> parent, View view, int position,
                                    long id) {
                openContextMenu(view);
            }

        });

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            checkPermissions();
        } else {

            getTimeZoneData();
        }
    }

    private void checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {

                showDialog("Permission Required", "You need to allow ACCESS to device location", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                REQUEST_CODE_PERMISSIONS);
                    }
                });

                return;
            }
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_PERMISSIONS);
        } else {
            getTimeZoneData();
        }
    }

    private void showDialog(String title, String msg, DialogInterface.OnClickListener listener) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(android.R.string.yes, listener)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getTimeZoneData();
                } else {
                    Toast.makeText(this, "LOCATION permissions not granted", Toast.LENGTH_LONG).show();
                    finish();
                }
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_add_time_zone:
                goToSearchView();
                break;
            case R.id.menu_help:
                Intent intent = new Intent(this, HelpActivity.class);
                startActivity(intent);
                break;
            case R.id.menu_refresh_time_zone:
                listViewAdapter.resetTime();
                getListView().setAdapter(listViewAdapter);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return super.onContextItemSelected(item);
    }

    /**
     * Method to go to Search Activity
     */
    public void goToSearchView() {
        Intent intent = new Intent(getApplicationContext(), SearchActivity.class);
        startActivity(intent);
    }

    /**
     * Method to get Time Zone data from the saved Shared Preferences
     */
    public void getTimeZoneData() {

        timeZoneImpl = new TimeZoneImpl(this);
        Vector<TimeZoneModel> zones = new Vector<TimeZoneModel>();
        zones.add(0, useLocation());
        TimeZoneModel timeZoneModel = new TimeZoneModel();
        timeZoneModel.setCalendar(new GregorianCalendar());
        timeZoneModel.setCity("Sydney");
        timeZoneModel.setCountry("Australia");
        timeZoneModel.setId(111);
        timeZoneModel.setMorning(false);
        timeZoneModel.setTimeZoneId("1");
        zones.add(timeZoneModel);

        listViewAdapter = new ListViewAdapter(this, 0, zones);
        setListAdapter(listViewAdapter);
    }

    public TimeZoneModel useLocation() {
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        bestProvider = locationManager.getBestProvider(criteria, false);
        Location location = null;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            checkPermissions();
        } else {
            location = locationManager.getLastKnownLocation(bestProvider);
        }

        TimeZoneModel timeZoneModel = new TimeZoneModel();
        TimeZone timeZone = TimeZone.getDefault();
        currentLocation = "Current Zone";
        timeZoneModel.setCity(currentLocation);
        timeZoneModel.setCountry(timeZone.getDisplayName());
        timeZoneModel.setTimeZoneId(timeZone.getID());
        timeZoneModel.setCalendar(Calendar.getInstance());

        timeZoneImpl = new TimeZoneImpl(this);

        Geocoder geoCoder = new Geocoder(this);

        try {
            if (location != null) {
                List<Address> address = geoCoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                getPreferences(MODE_PRIVATE).edit().putString(MainActivity.CURRENT_LOCATION, address.get(0).getLocality()).commit();

                if (timeZoneImpl.getSingleCity(address.get(0).getLocality()) != null) {
                    currentLocation = address.get(0).getLocality();
                    timeZoneModel = timeZoneImpl.getSingleCity(address.get(0).getLocality());
                }
            } else {
                return timeZoneModel;
            }

        } catch (IOException e) {
            Log.d("GeoCoder", "Error getting location.");

            String city = getPreferences(MODE_PRIVATE).getString(MainActivity.CURRENT_LOCATION, null);
            if (timeZoneImpl.getSingleCity(city) != null) {
                timeZoneModel = timeZoneImpl.getSingleCity(city);
                currentLocation = city;
            }
        }

        return timeZoneModel;
    }

    @Override
    protected void onPause() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
//            checkPermissions();
        } else {
            updatePreference();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }
}


