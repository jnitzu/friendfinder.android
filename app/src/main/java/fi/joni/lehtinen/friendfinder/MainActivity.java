package fi.joni.lehtinen.friendfinder;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.OperationApplicationException;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fi.joni.lehtinen.friendfinder.authentication.AuthenticationActivity;
import fi.joni.lehtinen.friendfinder.circle.AddFriendFragment;
import fi.joni.lehtinen.friendfinder.circle.ChooseCircleFragment;
import fi.joni.lehtinen.friendfinder.circle.CreateCircleFragment;
import fi.joni.lehtinen.friendfinder.circle.JoinRequestFragment;
import fi.joni.lehtinen.friendfinder.circle.ManageCircleFragment;
import fi.joni.lehtinen.friendfinder.connectionprotocol.ConnectionProtocol;
import fi.joni.lehtinen.friendfinder.connectionprotocol.Reply;
import fi.joni.lehtinen.friendfinder.connectionprotocol.dto.Circle;

public class MainActivity
        extends
        AppCompatActivity
        implements
        NavigationView.OnNavigationItemSelectedListener,
        ServiceConnection,
        LoaderManager.LoaderCallbacks<Cursor>,
        OnCircleChangeListener {

    public static final String EXTRA_USER_ID = "fi.joni.lehtinen.friendfinder.MainActivity.user_id";

    private static final String TAG = "MainActivity";
    private static final String BACKSTACK_MAP = "MAP";

    private static final String LOCATION_KEY = "fi.joni.lehtinen.friendfinder.currentlocation";
    private static final String LAST_UPDATED_TIME_KEY = "fi.joni.lehtinen.friendfinder.lastupdatetime";
    private static final int MAP_PADDING = 30;

    private ActionBarDrawerToggle mDrawerToggle;
    private GoogleMap mMap;
    private Map<Long,Marker> mMarkers = new HashMap<>();
    private AddressResultReceiver mAddressResultReceiver;
    private ServerConnectionService.ServerConnectionBinder mBinder;
    private FloatingActionButton mFab;
    private Toolbar mToolbar;
    private View mContainer;
    private NavigationView mDrawerNavigationView;
    private MenuItem mManageCircleMenuItem;
    private ServerConnectionResultReceiver mServerConnectionResultReceiver;

    private long mUserID = -1;
    private Circle mCurrentCircle = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().setNavigationBarColor(getResources().getColor(R.color.navigationBarColor));

        mUserID = getIntent().getLongExtra( EXTRA_USER_ID, -1 );

        Log.d( TAG, "USER ID: " + mUserID );

        mAddressResultReceiver = new AddressResultReceiver(new Handler());

        mContainer = findViewById( R.id.fragment_container );
        mDrawerNavigationView = (NavigationView)findViewById( R.id.nav_view );
        mManageCircleMenuItem = mDrawerNavigationView.getMenu().findItem( R.id.nav_manage );

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        View headerView = mDrawerNavigationView.getHeaderView(0);
        TextView logo = (TextView) headerView.findViewById( R.id.header_logo_text );
        Typeface myTypeface = Typeface.createFromAsset(getAssets(), "fonts/thinking_of_betty.ttf");
        logo.setTypeface(myTypeface);

        TextView name = (TextView) headerView.findViewById( R.id.header_user_name );
        TextView email = (TextView) headerView.findViewById( R.id.header_email );

        Cursor c = getContentResolver().query( DatabaseProvider.CONTENT_URI_LOGIN, null, null, null, null, null );

        if( c!= null && c.moveToNext() ){
            email.setText( c.getString( c.getColumnIndex( DatabaseProvider.LOGIN.EMAIL.toString() ) ) );
            c.close();
        } else {
            Log.wtf( TAG, "Main activity started without login data." );
            // TODO: Main activity started without user data. Error handling though this should not ever happen
        }

        c = getContentResolver().query( DatabaseProvider.CONTENT_URI_FRIEND, null, DatabaseProvider.FRIEND.ID.toString() + " = " + mUserID, null, null, null );

        if( c!= null && c.moveToNext() ){
            name.setText( c.getString( c.getColumnIndex( DatabaseProvider.FRIEND.FIRSTNAME.toString() ) ) + " " + c.getString( c.getColumnIndex( DatabaseProvider.FRIEND.LASTNAME.toString() ) ) );
            c.close();
        } else {
            Log.wtf( TAG, "Main activity started without friend data." );
            // TODO: Main activity started without user data. Error handling though this should not ever happen
        }

        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mFab.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                AddFriendFragment mAddFriendFragment = new AddFriendFragment();
                mAddFriendFragment.setGroupID( mCurrentCircle.mID );
                getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, mAddFriendFragment)
                        .addToBackStack(null)
                        .commit();
            }

        });

        final DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(
                this, drawer, mToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(mDrawerToggle);

        // Set up listener for situation where drawer indicator is disabled
        mDrawerToggle.setToolbarNavigationClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getFragmentManager().getBackStackEntryCount() > 1) {
                    // First entry in the backstack is from empty screen to account list view
                    // that is why we will always keep that state there to go back to it.

                    getFragmentManager().popBackStack();

                    // If we are at last transaction then enable drawer indicator
                    if (getFragmentManager().getBackStackEntryCount() == 2) {
                        mDrawerToggle.setDrawerIndicatorEnabled(true);
                    }
                }
            }
        });

        mDrawerToggle.syncState();

        mDrawerNavigationView.setNavigationItemSelectedListener(this);

        findNewCircle();

        FragmentManager fragmentManager = getFragmentManager();

        // Listener that enables/disables drawer indicator depending if we are on account list or not
        fragmentManager.addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                int entryCount = getFragmentManager().getBackStackEntryCount();

                mFab.setVisibility( entryCount <= 1 ? View.VISIBLE : View.INVISIBLE );
                //mDrawerToggle.setDrawerIndicatorEnabled( entryCount <= 1);

                if(getFragmentManager().getBackStackEntryCount() < 2) {
                    ActionBar mActionBar = getSupportActionBar();
                    if( mActionBar != null ) {
                        if( mCurrentCircle == null ) {
                            mActionBar.setTitle( R.string.app_name );
                        } else {
                            mActionBar.setTitle( mCurrentCircle.mName );
                        }
                    }
                }

            }
        });

        MapFragment mapFragment = new MapFragment();
        mapFragment.getMapAsync(new OnMapReadyCallback() {

            @Override
            public void onMapReady(GoogleMap googleMap) {

                mMap = googleMap;
                mMap.setInfoWindowAdapter( new InfoWindow() );
                mMap.getUiSettings().setMapToolbarEnabled(false);
                mMap.getUiSettings().setZoomControlsEnabled(false);

                mMap.setMapStyle( MapStyleOptions.loadRawResourceStyle( MainActivity.this, R.raw.google_map_style ));

                getLoaderManager().initLoader(0, null, MainActivity.this);

            }
        });

        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, mapFragment)
                .addToBackStack(BACKSTACK_MAP)
                .commit();


        mServerConnectionResultReceiver = new ServerConnectionResultReceiver( null );

        mServerConnectionResultReceiver.setOnLocationChange( new ServerConnectionResultReceiver.OnLocationChange() {
            @Override
            public void onLocationChange( boolean isLocationOn ) {
                if(!isLocationOn)
                    logout();
            }
        } );

        mServerConnectionResultReceiver.setOnLoginError( new ServerConnectionResultReceiver.OnLoginError() {
            @Override
            public void onLoginError() {
                logout();
            }
        } );

        // This will wait for circle table to have its first value.
        // If that is done by requesting data from server and mCurrentCircle isn't set
        // then this will update mCurrentCircle and be killed after that
        getLoaderManager().initLoader( 1, null, this );
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent myIntent = new Intent( this, ServerConnectionService.class );
        bindService( myIntent, this, 0 );
    }

    @Override
    protected void onResume() {
        super.onResume();

        if( mBinder != null )
            mBinder.startRequests();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d( TAG, "onRestart: " +ServerConnectionService.isRunning() + " " +  ServerConnectionService.isLoggedIn());
        // TODO: We might need to restart service here
    }

    @Override
    public void onPause(){
        super.onPause();

        if( mBinder != null ) {
            mBinder.stopRequests();
            mBinder.allowSelfShutdown( true );
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        unbindService( this );
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            if (getFragmentManager().getBackStackEntryCount() > 1) {

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

                                MainActivity.super.finishAffinity();
                            }

                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch( item.getItemId() ){
            case R.id.action_reload:
                if( !mBinder.requestData() ){
                    Snackbar.make( findViewById(R.id.fragment_container), "Refresh on cooldown!", Snackbar.LENGTH_LONG).show();
                }

                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        ActionBar mActionBar = getSupportActionBar();
        switch( item.getItemId() ){
            case R.id.nav_map:
                getFragmentManager().popBackStack(BACKSTACK_MAP, 0);

                if(mActionBar != null){
                    if(mCurrentCircle == null){
                        mActionBar.setTitle( R.string.app_name );
                    } else {
                        mActionBar.setTitle( mCurrentCircle.mName );
                    }
                }

                break;
            case R.id.nav_manage:
                getFragmentManager().popBackStack(BACKSTACK_MAP, 0);

                if(mActionBar != null)
                    mActionBar.setTitle( mCurrentCircle.mName );

                ManageCircleFragment mManageCircleFragment = new ManageCircleFragment();
                mManageCircleFragment.setUserID( mUserID );
                mManageCircleFragment.setCircle( mCurrentCircle );
                getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, mManageCircleFragment)
                        .addToBackStack(null)
                        .commit();
                break;
            case R.id.nav_circle_create:
                getFragmentManager().popBackStack(BACKSTACK_MAP, 0);

                if(mActionBar != null)
                    mActionBar.setTitle( R.string.title_create_circle );

                CreateCircleFragment mCreateCircleFragment = new CreateCircleFragment();
                mCreateCircleFragment.setUserID( mUserID );
                getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, mCreateCircleFragment)
                        .addToBackStack(null)
                        .commit();
                break;
            case R.id.nav_circle_choose:
                getFragmentManager().popBackStack(BACKSTACK_MAP, 0);

                if(mActionBar != null)
                    mActionBar.setTitle( R.string.title_choose_group );

                ChooseCircleFragment mChooseCircleFragment = new ChooseCircleFragment();
                mChooseCircleFragment.setUserID( mUserID );
                getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, mChooseCircleFragment)
                        .addToBackStack(null)
                        .commit();
                break;
            case R.id.nav_circle_join_requests:
                mBinder.transmit( ConnectionProtocol.Protocols.JOIN_REQUESTS, null, new RequestResultCallbacks() {
                    @Override
                    public void OnRequestResult( Reply reply ) {
                        switch( reply.mReplyCode ){
                            case JOIN_REQUEST_SUCCESSFUL:

                                List<byte[]> messages = reply.getMessages();

                                ArrayList<ContentProviderOperation> batch = new ArrayList<>();

                                // Clear join_request table before adding
                                batch.add( ContentProviderOperation.newDelete( DatabaseProvider.CONTENT_URI_JOIN_REQUEST ).build() );

                                for(byte[] message : messages){
                                    String[] parts = new String(message, StandardCharsets.UTF_8).split( "," );

                                    batch.add( ContentProviderOperation
                                            .newInsert( DatabaseProvider.CONTENT_URI_JOIN_REQUEST )
                                            .withValue( DatabaseProvider.JOIN_REQUEST.CIRCLE_ID.toString(), Long.parseLong( parts[0] ) )
                                            .withValue( DatabaseProvider.JOIN_REQUEST.NAME.toString(), parts[1] )
                                            .build()
                                    );

                                }

                                try {
                                    getContentResolver().applyBatch( DatabaseProvider.AUTHORITY, batch );
                                } catch( RemoteException | OperationApplicationException e ){

                                }

                                break;
                            default:
                        }
                    }
                } );

                getFragmentManager().popBackStack(BACKSTACK_MAP, 0);

                if(mActionBar != null)
                    mActionBar.setTitle( R.string.title_invites );

                JoinRequestFragment mJoinRequestFragment = new JoinRequestFragment();
                mJoinRequestFragment.setUserID( mUserID );
                getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, mJoinRequestFragment)
                        .addToBackStack(null)
                        .commit();
                break;
            case R.id.nav_logout:
                logout();
                break;
            case R.id.nav_db_debug:
                getContentResolver().query( DatabaseProvider.CONTENT_URI_DEBUG, null, null, null, null );
                break;
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void logout(){

        // destroy loader before emptying database
        getLoaderManager().destroyLoader( 0 );
        getContentResolver().delete( DatabaseProvider.CONTENT_URI_ALL, null, null );

        mBinder.logout();

        Intent myIntent = new Intent( this, AuthenticationActivity.class );
        startActivity( myIntent );

        finishAffinity();
    }

    @Override
    public Loader<Cursor> onCreateLoader( int id, Bundle args ) {
        switch( id ){
            case 0:
                return new CursorLoader(
                        this,
                        DatabaseProvider.CONTENT_URI_CIRCLE_LOCATION,
                        new String[]{
                                DatabaseProvider.FRIEND.ID.fullName(),
                                DatabaseProvider.FRIEND.FIRSTNAME.fullName(),
                                DatabaseProvider.FRIEND.LASTNAME.fullName(),
                                DatabaseProvider.LOCATION.LATITUDE.fullName(),
                                DatabaseProvider.LOCATION.LONGITUDE.fullName(),
                                DatabaseProvider.LOCATION.ACCURACY.fullName(),
                                DatabaseProvider.LOCATION.TIME_RECORDED.fullName() },
                        DatabaseProvider.CIRCLE_MEMBERS.CIRCLE_ID + " = " + mCurrentCircle.mID,
                        null,
                        null);
            case 1:
                return new CursorLoader(
                        this,
                        DatabaseProvider.CONTENT_URI_CIRCLE,
                        null,
                        null,
                        null,
                        null);
        }

        return null;
    }

    @Override
    public void onLoadFinished( Loader<Cursor> loader, Cursor data ) {
        switch( loader.getId() ){
            case 0:

                if( data.getCount() == 0 || mMap == null )
                    return;

                LatLngBounds.Builder builder = new LatLngBounds.Builder();

                data.moveToFirst();

                do {
                    Log.d( "LOADER", data.getLong( 0 ) + " " + data.getString( 1 ) + " " + data.getString( 2 ) + " " + data.getDouble( 3 ) + " " + data.getDouble( 4 ) + " " + data.getDouble( 5 ) + " " + new Date(data.getLong( 6 )) );

                    MarkerData mMarkerData = new MarkerData( data.getLong( 0 ), data.getString( 1 ), data.getString( 2 ), data.getDouble( 3 ), data.getDouble( 4 ), data.getDouble( 5 ), data.getLong( 6 ) );
                    builder.include( mMarkerData.mPosition );
                    updateLocation( mMarkerData );
                } while(data.moveToNext());

                LatLngBounds bounds = builder.build();

                // Calculation is done with height including toolbar without specifying width and heigth.
                // With them calculation is correct but map is centered as there where no toolbar
                CameraUpdate mCameraUpdate = CameraUpdateFactory.newLatLngBounds( bounds, mContainer.getWidth(), mContainer.getHeight() - mToolbar.getHeight(), MAP_PADDING );
                mMap.moveCamera( mCameraUpdate );

                // Move camera half a toolbar to down so that it is centered to map fragment's visible area.
                mMap.moveCamera( CameraUpdateFactory.scrollBy( 0, -mToolbar.getHeight() / 2 ) );

                // If zoom level is too high zoom back to 16
                if( mMap.getCameraPosition().zoom > 16)
                    mMap.moveCamera( CameraUpdateFactory.zoomTo( 16 ) );

                break;
            case 1:
                // Wait for circles to be loaded from server
                Log.d( TAG, "onLoadFinished: 1" );
                if( data != null && data.getCount() != 0 ){
                    Log.d( TAG, "onLoadFinished: 1 | " + data.getCount() + " " + mCurrentCircle.mID + " " + mCurrentCircle.mName );
                    if( mCurrentCircle.mID == -1 ){
                        findNewCircle();
                    }

                    if( getLoaderManager().getLoader( 1 ) != null )
                        getLoaderManager().destroyLoader( 1 );
                }
                break;
        }
    }

    @Override
    public void onLoaderReset( Loader<Cursor> loader ) {

    }

    private void updateLocation( MarkerData markerData ){

            Marker marker;
            if(mMarkers.containsKey( markerData.mUserID )){
                marker = mMarkers.get( markerData.mUserID );
                marker.setPosition( markerData.mPosition );
            } else {
                final int RADIUS = 50;
                final float STROKE = 0.1f;
                final float CENTER = RADIUS * ( 1 + STROKE / 2 );
                String initials = new String( new char[]{ markerData.mFirstname.charAt( 0 ), markerData.mLastname.charAt( 0 )});

                Bitmap b = Bitmap.createBitmap( (int)( RADIUS * 2 * ( 1 + STROKE )), (int)( RADIUS * 2 * ( 1 + STROKE )), Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(b);

                Paint paint = new Paint();
                Paint circlePaint = new Paint();

                paint.setColor(Color.WHITE);
                paint.setTextSize(36f);
                paint.setAntiAlias(true);
                paint.setTextAlign(Paint.Align.CENTER);

                Rect bounds = new Rect();
                paint.getTextBounds(initials, 0, initials.length(), bounds);

                circlePaint.setColor(Color.parseColor( "#009688" ));
                circlePaint.setAntiAlias(true);

                c.drawCircle(CENTER, CENTER, RADIUS, circlePaint);

                circlePaint.setColor(Color.BLACK);
                circlePaint.setStyle(Paint.Style.STROKE);
                circlePaint.setStrokeWidth( RADIUS * STROKE / 2 );

                c.drawCircle(CENTER, CENTER, RADIUS, circlePaint);
                c.drawText(initials, CENTER, CENTER + bounds.height()/2, paint);
                c.drawBitmap(b, RADIUS * 2 * ( 1 + STROKE ), RADIUS * 2 * ( 1 + STROKE ), paint);

                marker = mMap.addMarker(new MarkerOptions()
                        .position( markerData.mPosition )
                        .icon( BitmapDescriptorFactory.fromBitmap( b ) ));

                mMarkers.put( markerData.mUserID, marker );
            }

            marker.setTag( markerData );

            fetchAddress( markerData );
    }


    @Override
    public void onServiceConnected( ComponentName name, IBinder iBinder ) {
        mBinder = ( ServerConnectionService.ServerConnectionBinder ) iBinder;

        mBinder.registerLocationResultReceiver( mServerConnectionResultReceiver );
        mBinder.startRequests();
    }

    @Override
    public void onServiceDisconnected( ComponentName name ) {

    }

    @Override
    public void changeCircle( Circle circle ) {

        if( getLoaderManager().getLoader( 1 ) != null )
            getLoaderManager().destroyLoader( 1 );

        mCurrentCircle = circle;
        mMap.clear();
        mMarkers.clear();

        getSupportActionBar().setTitle( mCurrentCircle.mName );
        mManageCircleMenuItem.setEnabled( true );

        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void removeCircleMember( long userId ){
        mMarkers.remove( userId ).remove();
    }

    @Override
    public void removeCircle( Circle circle ) {
        Snackbar.make(mContainer, "Circle " + circle.mName + "deleted!", Snackbar.LENGTH_LONG);

        if( mCurrentCircle.mID == circle.mID ){
            findNewCircle();

            mMap.clear();
            mMarkers.clear();

            getLoaderManager().restartLoader(0, null, this);
        }
    }

    /**
     * Find last used circle, any circle or no circle with only user location shown
     */
    private void findNewCircle(){
        Cursor c = getContentResolver().query( DatabaseProvider.CONTENT_URI_CIRCLE_LAST, null, null, null, null );

        if( c != null && c.moveToNext() && c.getCount() != 0 ){
            mCurrentCircle = new Circle( c.getLong(0), c.getString( 1 ) );
            mManageCircleMenuItem.setEnabled( true );
            getSupportActionBar().setTitle( mCurrentCircle.mName );
            Log.d( TAG, "findNewCircle: " + mCurrentCircle.mID + " " + mCurrentCircle.mName );
        } else {
            mCurrentCircle = new Circle( -1, "" );
            mManageCircleMenuItem.setEnabled( false );
            getSupportActionBar().setTitle( R.string.app_name );
            Log.d( TAG, "findNewCircle: empty" );
        }

        if( c != null )
            c.close();
    }

    protected void fetchAddress( MarkerData markerData ) {
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(FetchAddressIntentService.EXTRA_RECEIVER, mAddressResultReceiver);
        intent.putExtra(FetchAddressIntentService.EXTRA_LATLNG, markerData.mPosition);
        intent.putExtra(FetchAddressIntentService.EXTRA_USER_ID, markerData.mUserID);
        startService(intent);
    }

    @SuppressLint("ParcelCreator")
    class AddressResultReceiver extends ResultReceiver {

        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

            String address = resultData.getString( FetchAddressIntentService.RESULT_ADDRESS );
            long user_id = resultData.getLong( FetchAddressIntentService.RESULT_USER_ID );

            switch(resultCode) {
                case FetchAddressIntentService.SUCCESS_RESULT:
                    if( mMarkers.containsKey( user_id )) {
                        ((MarkerData)mMarkers.get( user_id ).getTag()).setAddress( address );
                    }
                    break;
                case  FetchAddressIntentService.FAILURE_RESULT:
                    if( mMarkers.containsKey( user_id )) {
                        ((MarkerData)mMarkers.get( user_id ).getTag()).setAddress( "N/A" );
                    }
                    break;
            }
        }
    }

    private class InfoWindow implements GoogleMap.InfoWindowAdapter {

        public TextView mFirstname;
        public TextView mLastname;
        public TextView mAddress;
        public TextView mAccuracy;
        public TextView mTime;

        @Override
        public View getInfoContents( Marker marker ) {
            return null;
        }

        @Override
        public View getInfoWindow( Marker marker ) {
            View view = getLayoutInflater().inflate(R.layout.info_window, null);

            mFirstname = (TextView)view.findViewById( R.id.info_window_firstname );
            mLastname = (TextView)view.findViewById( R.id.info_window_lastname );
            mAddress = (TextView)view.findViewById( R.id.info_window_address );
            mAccuracy = (TextView)view.findViewById( R.id.info_window_accuracy );
            mTime = (TextView)view.findViewById( R.id.info_window_time );

            MarkerData mMarkerData = (MarkerData)marker.getTag();

            mFirstname.setText( mMarkerData.mFirstname );
            mLastname.setText( mMarkerData.mLastname );

            if( mMarkerData.getAddress() == null ){
                mAddress.setText( "..." );
                mMarkerData.setAddressTextView( mAddress );
            } else {
                mAddress.setText( mMarkerData.getAddress() );
            }

            mAccuracy.setText( String.format( Locale.getDefault(), "%.2f m", mMarkerData.mAccuracy ) );

            long timePassed = ( new Date().getTime() - mMarkerData.mTimeRecorded ) / 1000;
            mTime.setText( MessageFormat.format( "{0,choice,0#|0<'{0}h '}{1,choice,0#|0<'{1}min '}{2,choice,0#|0<'{2}s'}", timePassed / 3600, (timePassed / 60) % 60, timePassed % 60 ) );

            return view;
        }
    }
}
