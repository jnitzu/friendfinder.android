package fi.joni.lehtinen.friendfinder.authentication;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toolbar;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import fi.joni.lehtinen.friendfinder.DatabaseProvider;
import fi.joni.lehtinen.friendfinder.OnLoginListener;
import fi.joni.lehtinen.friendfinder.ServerConnectionResultReceiver;
import fi.joni.lehtinen.friendfinder.MainActivity;
import fi.joni.lehtinen.friendfinder.R;
import fi.joni.lehtinen.friendfinder.ServerConnectionService;

public class AuthenticationActivity
        extends Activity
        implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        ServiceConnection,
        OnLoginListener {

    private static final int PERMISSIONS_REQUEST_FINE_LOCATION = 101;
    private static final int REQUEST_CHECK_LOCATION_SETTINGS = 201;
    private static final String TAG = "AuthenticationActivity";

    private ServerConnectionService.ServerConnectionBinder mBinder;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private LogInResultReceiver mLogInResultReceiver;
    private ServerConnectionResultReceiver mServerConnectionResultReceiver;
    private boolean mIsPermissionCheckOnProgress = false;

    private long mUserID = -1;
    private boolean mShouldLogIn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setNavigationBarColor(getResources().getColor(R.color.navigationBarColor));

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi( LocationServices.API)
                    .build();
        }

        permissionCheck();

        mLogInResultReceiver = new LogInResultReceiver( null );
        mServerConnectionResultReceiver = new ServerConnectionResultReceiver( null );

        mServerConnectionResultReceiver.setOnLocationChange( new ServerConnectionResultReceiver.OnLocationChange() {
            @Override
            public void onLocationChange( boolean isLocationOn ) {
                Log.d( TAG, "onLocationChange: " + isLocationOn );
                if(!mIsPermissionCheckOnProgress)
                    permissionCheck();

                if(!mGoogleApiClient.isConnected())
                    mGoogleApiClient.connect();
            }
        } );

        // Check if service is running if not then start it
        if(!ServerConnectionService.isRunning()){
            Intent myIntent = new Intent(this, ServerConnectionService.class);
            myIntent.putExtra( ServerConnectionService.LOGIN_RESULT_RECEIVER_EXTRA, mLogInResultReceiver);
            myIntent.putExtra( ServerConnectionService.ALLOW_SELF_SHUTDOWN_EXTRA, false);
            startService( myIntent );

        } else if (ServerConnectionService.isLoggedIn()){
            // We will wait for activity to bind to service before we will start the mainactivity
            mShouldLogIn = true;
        } else {
            Log.e("AUTHENTICATION_ACTIVITY", "SERVER IS RUNNING BUT USER NOT LOGGED IN. USER ID: " + mUserID );
        }

        setContentView(R.layout.activity_authentication);

        Toolbar mToolbar = (Toolbar)findViewById(R.id.toolbar);

        setActionBar(mToolbar);

        final ActionBar mActionBar = getActionBar();

        if(mActionBar != null){
            mActionBar.setHomeButtonEnabled(false);
            mActionBar.setDisplayHomeAsUpEnabled(false);
        }

        getFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                if(mActionBar == null) return;

                if(getFragmentManager().getBackStackEntryCount() > 0){
                    mActionBar.setHomeButtonEnabled(true);
                    mActionBar.setDisplayHomeAsUpEnabled(true);
                } else {
                    mActionBar.setHomeButtonEnabled(false);
                    mActionBar.setDisplayHomeAsUpEnabled(false);
                    mActionBar.setTitle(R.string.app_name);
                }
            }
        });

        getFragmentManager().beginTransaction()
                .replace(R.id.authentication_fragment_container, new AuthenticationFragment())
                .commit();
    }

    @Override
    public void onStart() {
        mGoogleApiClient.connect();
        super.onStart();

        Intent myIntent = new Intent( this, ServerConnectionService.class );
        bindService( myIntent, this, 0 );
    }

    @Override
    public void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();

        if( mUserID == -1 ){
            Intent myIntent = new Intent( this, ServerConnectionService.class );
            stopService( myIntent );
        }

        unbindService( this );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()){
            case android.R.id.home:
                if (getFragmentManager().getBackStackEntryCount() > 0) {

                    getFragmentManager().popBackStack();
                }
                break;
        }

        return super.onOptionsItemSelected(menuItem);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        final LocationSettingsStates states = LocationSettingsStates.fromIntent(data);
        switch (requestCode) {
            case REQUEST_CHECK_LOCATION_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        // All required changes were successfully made
                        mGoogleApiClient.disconnect();

                        if( mUserID != -1 && !mIsPermissionCheckOnProgress )
                            openMainActivity();
                        break;
                    default:
                        new AlertDialog.Builder(this)
                                .setTitle(getString(R.string.alert_dialog_location_turned_off_title))
                                .setMessage(getString(R.string.alert_dialog_location_turned_off_msg))
                                .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // Looks better when dialog is dismissed first
                                        dialog.dismiss();

                                        shutdownApplication();
                                    }

                                })
                                .show();
                        break;
                }
                break;
        }
    }

    @Override
    @SuppressWarnings({"MissingPermission"})
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_FINE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mIsPermissionCheckOnProgress = false;

                    if( mUserID != -1 && !mGoogleApiClient.isConnected() )
                        openMainActivity();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.alert_dialog_force_exit_title))
                            .setMessage(getString(R.string.alert_dialog_force_exit_msg))
                            .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // Looks better when dialog is dismissed first
                                    dialog.dismiss();

                                    shutdownApplication();
                                }

                            })
                            .show();
                }
                return;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            // First entry in the backstack is from empty screen to account list view
            // that is why we will always keep that state there to go back to it.

            getFragmentManager().popBackStack();
        } else {
                // Exit application dialog
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.alert_dialog_exit_application_title))
                        .setMessage(getString(R.string.alert_dialog_exit_application_msg))
                        .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Looks better when dialog is dismissed first
                                dialog.dismiss();

                                AuthenticationActivity.super.finishAffinity();
                            }

                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        mLocationRequest = new LocationRequest()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                        builder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                final LocationSettingsStates states = result.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // We don't want to use location api here. Just to check that necessary privileges has been set
                        mGoogleApiClient.disconnect();

                        if( mUserID != -1 && !mIsPermissionCheckOnProgress )
                            openMainActivity();
                        return;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(
                                    AuthenticationActivity.this,
                                    REQUEST_CHECK_LOCATION_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                            // Ignore the error.
                        }
                        break;
                    default:
                        new AlertDialog.Builder(AuthenticationActivity.this)
                                .setTitle(getString(R.string.alert_dialog_location_turned_off_title))
                                .setMessage(getString(R.string.alert_dialog_location_turned_off_msg))
                                .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // Looks better when dialog is dismissed first
                                        dialog.dismiss();

                                        shutdownApplication();
                                    }

                                })
                                .show();
                }
            }
        });


    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }


    @Override
    public void onServiceConnected( ComponentName name, IBinder iBinder ) {
        mBinder = ( ServerConnectionService.ServerConnectionBinder ) iBinder;

        mBinder.registerLocationResultReceiver( mServerConnectionResultReceiver );

        if( mShouldLogIn ){
            Cursor c = getContentResolver().query( DatabaseProvider.CONTENT_URI_LOGIN, null, null, null, null );

            if( c == null || c.getCount() == 0 ){
                mShouldLogIn = false;
                return;
            }

            c.moveToNext();
            mUserID = c.getLong( 0 );
            c.close();

            mBinder.startRequests();

            if( !mGoogleApiClient.isConnected() && !mIsPermissionCheckOnProgress )
                openMainActivity();
        }
    }

    @Override
    public void onServiceDisconnected( ComponentName name ) {

    }

    public class LogInResultReceiver extends ResultReceiver {

        public static final int LOGIN_RESULT = 101;

        public LogInResultReceiver( Handler handler ) {
            super( handler );
        }

        @Override
        protected void onReceiveResult( int resultCode, Bundle resultData ) {
            switch( resultCode ){
                case LOGIN_RESULT:
                    mUserID = resultData.getLong( ServerConnectionService.RESULT_DATA_USER_ID );

                    if( !mGoogleApiClient.isConnected() && !mIsPermissionCheckOnProgress )
                        openMainActivity();
                    break;
            }

        }
    }

    private void openMainActivity(){
        Intent myIntent = new Intent( this, MainActivity.class );
        myIntent.putExtra( MainActivity.EXTRA_USER_ID, mUserID );
        startActivity( myIntent );

        finishAffinity();
    }

    @Override
    public void onLogin( long user_id ) {
        mUserID = user_id;
        openMainActivity();
    }

    private void permissionCheck(){
        mIsPermissionCheckOnProgress = true;

        // Permission checks. If we don't have permissions app will terminate
        if ( ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if ( ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {

                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.alert_dialog_location_permission_title))
                        .setMessage(getString(R.string.alert_dialog_location_permission_msg))
                        .setPositiveButton(R.string.alert_dialog_button_grant, new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Looks better when dialog is dismissed first
                                dialog.dismiss();

                                ActivityCompat.requestPermissions( AuthenticationActivity.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        PERMISSIONS_REQUEST_FINE_LOCATION );
                            }

                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Looks better when dialog is dismissed first
                                dialog.dismiss();

                                shutdownApplication();
                            }

                        })
                        .show()
                        .setCanceledOnTouchOutside( false );

            } else {
                ActivityCompat.requestPermissions( this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSIONS_REQUEST_FINE_LOCATION );

            }
        } else {
            mIsPermissionCheckOnProgress = false;

            if( mUserID != -1 && !mGoogleApiClient.isConnected() )
                openMainActivity();
        }
    }

    /**
     * Closes the Authentication activity and ServerConnection Service
     */
    private void shutdownApplication(){
        AuthenticationActivity.this.finishAffinity();

        Intent myIntent = new Intent(this, ServerConnectionService.class);
        stopService( myIntent );
    }
}

