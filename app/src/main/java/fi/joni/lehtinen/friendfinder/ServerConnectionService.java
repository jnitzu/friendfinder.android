package fi.joni.lehtinen.friendfinder;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import fi.joni.lehtinen.friendfinder.authentication.AuthenticationActivity;
import fi.joni.lehtinen.friendfinder.connectionprotocol.ConnectionProtocol;
import fi.joni.lehtinen.friendfinder.connectionprotocol.PacketParser;
import fi.joni.lehtinen.friendfinder.connectionprotocol.Reply;
import fi.joni.lehtinen.friendfinder.connectionprotocol.Sendable;
import fi.joni.lehtinen.friendfinder.connectionprotocol.Utility;
import fi.joni.lehtinen.friendfinder.connectionprotocol.dto.Login;

/**
 * Service to communicate between client and server.
 * Started: When system boots or when user opens FriendFinder app
 * Closed: When this service is started without valid credential data stored in database or
 *         when FriendFinder app is closed while not logged in.
 */
public class ServerConnectionService
        extends
        Service
        implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener{

    public static final String LOGIN_RESULT_RECEIVER_EXTRA = "fi.joni.lehtinen.friendfinder.ServerConnectionService.result_receiver_extra";
    public static final String ALLOW_SELF_SHUTDOWN_EXTRA = "fi.joni.lehtinen.friendfinder.ServerConnectionService.self_shutdown_extra";
    public static final String RESULT_DATA_USER_ID = "fi.joni.lehtinen.friendfinder.ServerConnectionService.result_data_user_id";

    private static final String HOST_NAME = "192.168.1.1";
    private static final int PORT = 8000;
    private static final String TAG = "ServerConnectionService";

    private static final int LOCATION_UPDATE_INTERVAL = 1*60*1000;

    private static boolean mIsRunning = false;

    // This is accessed from ServiceThread thus need for volatile
    private volatile static boolean mIsConnected = false;

    private static boolean mIsLoggedIn = false;
    private static boolean mWasLoggedIn = false;

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private final Semaphore mNoNetworkGate = new Semaphore(0);
    private BroadcastReceiver mNetworkReceiver;
    private LocationReceiver mLocationReceiver;
    private final ServerConnectionBinder mBinder = new ServerConnectionBinder();
    private Handler mHandler;
    private ConnectionThread mConnectionThread;
    private ResultReceiver mLogInResultReceiver = null;
    private ResultReceiver mLocationResultReceiver = null;
    private int mBindCount = 0;
    private boolean mAllowSelfShutdown = true;
    private boolean mLocationSettingsValid = false;
    private boolean mLocationUpdateStarted = false;
    private DataRequestThread mRequestThread;
    private long mUserID = -1;



    public static boolean isRunning() { return mIsRunning; }
    public static boolean isLoggedIn() { return mIsLoggedIn; }

    @Override
    public void onCreate() {

        mConnectionThread = new ConnectionThread();
        mHandler = new Handler( Looper.getMainLooper());

        Log.d( TAG,"REGISTER RECEIVER");

        // Register connection change receiver
        mNetworkReceiver = new ConnectionChangeReceiver( mNoNetworkGate );
        registerReceiver( mNetworkReceiver, new IntentFilter( ConnectivityManager.CONNECTIVITY_ACTION ) );

        // Register location change receiver
        mLocationReceiver = new LocationReceiver();
        registerReceiver( mLocationReceiver, new IntentFilter( LocationManager.PROVIDERS_CHANGED_ACTION ) );

        Log.d( TAG,"CREATED");

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi( LocationServices.API)
                    .build();
        }



        mIsRunning = true;
    }

    @Override
    public int onStartCommand( Intent intent, int flags, int startId ) {
        Log.d( TAG,"START");

        if( intent != null && intent.hasExtra( LOGIN_RESULT_RECEIVER_EXTRA ))
            mLogInResultReceiver = intent.getParcelableExtra( LOGIN_RESULT_RECEIVER_EXTRA );

        if( intent != null && intent.hasExtra( ALLOW_SELF_SHUTDOWN_EXTRA ))
            mAllowSelfShutdown = intent.getBooleanExtra( ALLOW_SELF_SHUTDOWN_EXTRA, true );

        if( !mGoogleApiClient.isConnected() && !mGoogleApiClient.isConnecting() )
            mGoogleApiClient.connect();

        // Check if this is the first time this method is called
        if(!mConnectionThread.isAlive()) {

            // Start service thread
            mConnectionThread.start();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind( Intent intent ) {
        mBindCount++;

        return mBinder;
    }

    @Override
    public boolean onUnbind( Intent intent ) {
        mBindCount--;
        Log.d( TAG, "onUnbind: ");
        return false;
    }

    @Override
    public void onDestroy() {

        Log.d( TAG,"DESTROY");

        stopLocationUpdates();

        mGoogleApiClient.disconnect();

        mConnectionThread.mIsRunning = false;
        mIsRunning = false;
        mWasLoggedIn = mIsLoggedIn;
        mIsLoggedIn = false;
        mLocationSettingsValid = false;
        mUserID = -1;

        // Unregister connection change receiver
        if( mNetworkReceiver != null ){
            Log.d( TAG,"UNREGISTER CONNECTION RECEIVER");
            unregisterReceiver( mNetworkReceiver );
        }

        // Unregister location change receiver
        if( mLocationReceiver != null ){
            Log.d( TAG,"UNREGISTER LOCATION RECEIVER");
            unregisterReceiver( mLocationReceiver );
        }

        super.onDestroy();
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {

        mLocationRequest = new LocationRequest()
            .setInterval(LOCATION_UPDATE_INTERVAL)
            .setFastestInterval(LOCATION_UPDATE_INTERVAL)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
            .addLocationRequest(mLocationRequest);

        PendingResult<LocationSettingsResult> result = LocationServices
            .SettingsApi
            .checkLocationSettings(mGoogleApiClient, builder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                switch (result.getStatus().getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        mLocationSettingsValid = true;

                        if(mIsLoggedIn)
                            startLocationUpdates();

                        break;
                    default:
                        // Location turned off. If this service was started on boot stop it.
                        //  If it was started by App let the App handle service shutdown
                        Log.d( TAG, "Location is turned off. Bind count: " + mBindCount );
                        mGoogleApiClient.disconnect();
                        permissionError();

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
        Log.e( TAG, "onConnectionFailed" );
    }

    @Override
    public void onLocationChanged( Location location ) {
        Log.d( TAG, "onLocationChanged: " + location.getLatitude() + " " + location.getLongitude() + " " + location.getTime() );

        Cursor c;

        if( mUserID == -1 ){
            c = getContentResolver().query( DatabaseProvider.CONTENT_URI_LOGIN, null, null, null, null );

            if( c == null || c.getCount() == 0 )
                return;

            c.moveToFirst();
            mUserID = c.getInt( 0 );

            c.close();
        }

        Sendable mSendable = new fi.joni.lehtinen.friendfinder.connectionprotocol.dto.Location(
                mUserID,
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getTime()
        );

        ContentValues mContentValues = new ContentValues();
        mContentValues.put( DatabaseProvider.LOCATION.FRIEND_ID.toString(), mUserID );
        mContentValues.put( DatabaseProvider.LOCATION.LATITUDE.toString(), location.getLatitude() );
        mContentValues.put( DatabaseProvider.LOCATION.LONGITUDE.toString(), location.getLongitude() );
        mContentValues.put( DatabaseProvider.LOCATION.ACCURACY.toString(), location.getAccuracy() );
        mContentValues.put( DatabaseProvider.LOCATION.TIME_RECORDED.toString(), location.getTime() );


        c = getContentResolver().query( DatabaseProvider.CONTENT_URI_LOCATION, null, DatabaseProvider.LOCATION.FRIEND_ID + " = " + mUserID, null, null );

        if( c != null && c.getCount() != 0 ){
            getContentResolver().update( DatabaseProvider.CONTENT_URI_LOCATION, mContentValues, DatabaseProvider.LOCATION.FRIEND_ID + " = " + mUserID, null );
        } else {
            getContentResolver().insert( DatabaseProvider.CONTENT_URI_LOCATION, mContentValues );
        }

        if( c != null )
            c.close();

        mBinder.transmit( ConnectionProtocol.Protocols.LOCATION, mSendable, null );
    }


    private void startLocationUpdates() {
        if ( ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocationUpdateStarted = true;
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        } else {
            // No ACCESS_FINE_LOCATION permission. If this service was started on boot stop it.
            // If it was started by App let the App handle service shutdown
            Log.d( TAG, "No ACCESS_FINE_LOCATION permission. Bind count: " + mBindCount );
            permissionError();
        }
    }

    private void stopLocationUpdates() {
        if( mLocationUpdateStarted && mGoogleApiClient.isConnected() )
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    public class ServerConnectionBinder extends Binder {
        private Date mLastRequestTime;

        public Future<Sendable> transmit( ConnectionProtocol.Protocols protocol, Sendable sendable, RequestResultCallbacks callback ){

            if(mIsConnected){
                FutureRequest mFutureRequest = new FutureRequest( protocol, sendable, callback );
                mConnectionThread.mRequestQueue.add( mFutureRequest );

                return mFutureRequest;
            }
            return null;
        }

        public void login( long user_id ){

            if(mLocationSettingsValid)
                startLocationUpdates();

            mIsLoggedIn = true;
            mUserID = user_id;

        }

        public void logout(){
            stopLocationUpdates();

            mIsLoggedIn = false;
            mWasLoggedIn = false;
            mUserID = -1;

        }

        /**
         * This is called from Login and Register fragments when user logs in.
         * Or from Main activity when it connects to this service
         */
        public void startRequests(){
            if(mRequestThread == null || !mRequestThread.isAlive()){
                mRequestThread = new DataRequestThread();
                mRequestThread.start();
            }
        }

        public void stopRequests(){
            mRequestThread.shutdown();
        }

        public boolean requestData(){
            if( mLastRequestTime == null || new Date().getTime() - mLastRequestTime.getTime() > 5000 ){
                mLastRequestTime = new Date();
                mRequestThread.requestNow();
                return true;
            } else {
                return false;
            }
        }

        // Only activities should call this
        public void registerLocationResultReceiver( ServerConnectionResultReceiver serverConnectionResultReceiver ){
            mLocationResultReceiver = serverConnectionResultReceiver;
        }

        public void allowSelfShutdown( boolean allow ){
            mAllowSelfShutdown = allow;
        }
    }

    private class ConnectionThread extends Thread {

        private static final int NO_SERVER_SLEEP_TIME = 5*60*1000; // 5 Minutes
        private static final String TAG = "TransmitPackageThread";

        // This value is changed by the service which operates in UI thread
        // thus this needs to be volatile
        public volatile boolean mIsRunning = false;

        private ConnectivityManager mConnectivityManager;
        private SSLContext mSSLContext;
        private SSLSocketFactory mSSLSocketFactory;
        private SSLSocket mSSLSocket;
        private OutputStream mOut;
        private BufferedReader mIn;

        private boolean mIsServerRunning = true;
        private boolean mIsInit = false;
        private BlockingQueue<FutureRequest> mRequestQueue = new LinkedBlockingQueue<>();

        public ConnectionThread(){
            mConnectivityManager = (ConnectivityManager) getSystemService( Context.CONNECTIVITY_SERVICE );
        }

        @Override
        public void run() {
            Log.d( TAG, "STARTED" );
            mIsRunning = true;

            // Initialize connection. Only done once
            if(!mIsInit){
                Log.d( TAG, "INIT" );
                createSSLContext(getResources());

                mSSLSocketFactory = mSSLContext.getSocketFactory();
            }

            Reply reply;
            List<String> mReplyData = new ArrayList<>();
            String dataSegment;
            FutureRequest mFutureRequest = null;

            Log.d( TAG, "MAIN LOOP" );
            while(mIsRunning) {

                // Connect to server
                while( mSSLSocket == null || mSSLSocket.isClosed() ) {

                    if(!mIsServerRunning){
                        try {
                            Log.d( TAG, "CONNECTION RESET BY PEER. SLEEP TIME: " + NO_SERVER_SLEEP_TIME/1000 + "s");
                            Thread.sleep( NO_SERVER_SLEEP_TIME );
                        } catch( InterruptedException e ) {
                            Log.getStackTraceString( e );
                        }
                    }

                    NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();

                    if (networkInfo != null && networkInfo.isConnected()) {

                        Log.d( TAG, "CONNECTING" );
                        try {
                            mSSLSocket = (SSLSocket) mSSLSocketFactory.createSocket( HOST_NAME, PORT );
                            mSSLSocket.setUseClientMode( true );

                            mIsServerRunning = true;

                            Log.d( TAG, "HANDSHAKING" );
                            // Start handshake and establish connection to server
                            mSSLSocket.startHandshake();

                            mOut = mSSLSocket.getOutputStream();
                            mIn = new BufferedReader( new InputStreamReader( mSSLSocket.getInputStream(), StandardCharsets.UTF_8 ) );

                            mIsConnected = true;

                        } catch( IOException e ) {

                            // Set isConnected to false so that new task cannot be added
                            mIsConnected = false;

                            // Server is not running.
                            if( e instanceof ConnectException ) {
                                mIsServerRunning = false;
                            }

                            Log.e( TAG, Log.getStackTraceString( e ) );

                            if( mSSLSocket != null && !mSSLSocket.isClosed() ) {
                                try {
                                    mSSLSocket.close();
                                } catch( IOException e1 ) {
                                    Log.e( TAG, Log.getStackTraceString( e1 ) );
                                }
                            }

                            reply = new Reply();
                            reply.mReplyCode = Reply.ReplyCode.CONNECTION_RESET_BY_PEER;

                            // Empty pending request
                            while( ( mFutureRequest = mRequestQueue.poll() ) != null ) {

                                // Try switching state of future request. If successful mark as done
                                // else request is cancelled and does not need to be processed
                                if( mFutureRequest.setStarted() ) {
                                    mFutureRequest.done( reply );
                                }
                            }
                        }
                    } else {
                        // If there is no internet connection we will wait for system to
                        // wake us when network becomes available
                        try {
                            mNoNetworkGate.acquire();
                        } catch( InterruptedException e ){
                            Log.e( TAG, Log.getStackTraceString( e ) );
                        }
                    }
                }

                // Try logging in
                logIn();

                while( mIsRunning ) {
                    Log.d( TAG, "WAITING FOR REQUEST MESSAGE FROM APP" );
                    // Clear reply
                    reply = null;
                    mReplyData.clear();

                    try {
                        mFutureRequest = mRequestQueue.take();

                        if ( ContextCompat.checkSelfPermission(ServerConnectionService.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            permissionError();
                            break;
                        } else {
                            if(!mLocationUpdateStarted){
                                if( mLocationSettingsValid ){
                                    mLocationSettingsValid = false;
                                    mHandler.post( new Runnable() {
                                        @Override
                                        public void run() {
                                            // This needs to run on main thread
                                            startLocationUpdates();
                                        }
                                    } );
                                } else {
                                    if(!mGoogleApiClient.isConnected() && !mGoogleApiClient.isConnecting()){
                                        mGoogleApiClient.connect();
                                        mLocationSettingsValid = false;
                                    }
                                }
                            }
                        }

                        NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();

                        if (networkInfo != null && networkInfo.isConnected()) {

                            Log.d( TAG, "REQUEST MESSAGE RECEIVED" );
                            byte[] message = mFutureRequest.getMessage();

                            // If message is cancelled this is null
                            if( message != null ) {

                                Log.d( TAG, "TRANSMITTING MESSAGE" );
                                String v = new String( message, StandardCharsets.UTF_8 );
                                for( String t : v.split( "\n" ) )
                                    Log.d( TAG, t + " " + t.length() );
                                mOut.write( message );

                                Log.d( TAG, "RECEIVING REPLY" );
                                while( !( dataSegment = mIn.readLine() ).equals( "" ) ) {
                                    mReplyData.add( dataSegment );
                                    Log.d( TAG, dataSegment );
                                }

                                reply = (Reply) PacketParser.build( ConnectionProtocol.Protocols.REPLY, mReplyData.toArray( new String[mReplyData.size()] ) );
                            }
                        } else {
                            reply = new Reply();
                            reply.mReplyCode = Reply.ReplyCode.NO_NETWORK_CONNECTION;

                            // Break out from communication loop and make a new connection
                            break;
                        }

                    } catch( InterruptedException | IOException e ) {
                        Log.e( TAG, Log.getStackTraceString( e ) );

                        // Close Connection
                        if( mSSLSocket != null && !mSSLSocket.isClosed() ) {
                            try {
                                mSSLSocket.close();
                            } catch( IOException e1 ) {
                                Log.e( TAG, Log.getStackTraceString( e1 ) );
                            }
                        }

                        reply = new Reply();
                        reply.mReplyCode = Reply.ReplyCode.CONNECTION_RESET_BY_PEER;

                        // Break out from communication loop and make a new connection
                        break;

                    } finally {
                        Log.d( TAG, "SENDING REPLY TO COMPONENT" );
                        // Mark future as done and execute callback on UI thread
                        // If reply is null then message was cancelled
                        if( reply != null )
                            mFutureRequest.done( reply );
                    }
                }
            }

            // Thread is closing release resources
            if(mSSLSocket != null && !mSSLSocket.isClosed()){
                try{
                    Log.d( TAG, "CLOSE SOCKET" );
                    mSSLSocket.close();
                } catch( IOException e1 ){
                    Log.e(TAG,Log.getStackTraceString( e1 ));
                }
            }

        }

        private boolean logIn(){

            Cursor c = null;

            try {
                Log.d( TAG, "FETCHING LOGIN DATA" );
                c = getContentResolver().query( DatabaseProvider.CONTENT_URI_LOGIN, null, null, null, null );
                if( c != null && c.getCount() == 1 ) {
                    Log.d( TAG, "LOGIN DATA FOUND" );
                    c.moveToFirst();
                    Login l = new Login( c.getString( 1 ), "", c.getBlob( 2 ) );

                    mBinder.transmit( ConnectionProtocol.Protocols.LOGIN_HASH, l, new RequestResultCallbacks() {
                        @Override
                        public void OnRequestResult( Reply reply ) {
                            switch( reply.mReplyCode ){
                                case LOGIN_SUCCESSFUL:
                                    Log.d( TAG, "LOGIN SUCCESSFUL" );

                                    List<byte[]> messages = reply.getMessages();

                                    mUserID = Utility.byteArrayToLong( messages.get( 0 ) );

                                    ContentValues mContentValues = new ContentValues();
                                    mContentValues.put( DatabaseProvider.FRIEND.ID.toString(), mUserID );
                                    mContentValues.put( DatabaseProvider.FRIEND.FIRSTNAME.toString(), new String(messages.get( 1 ), StandardCharsets.UTF_8 ));
                                    mContentValues.put( DatabaseProvider.FRIEND.LASTNAME.toString(), new String(messages.get( 2 ), StandardCharsets.UTF_8 ));

                                    getContentResolver().update( DatabaseProvider.CONTENT_URI_FRIEND, mContentValues, DatabaseProvider.FRIEND.ID + " = " + mUserID, null );

                                    if(mLogInResultReceiver != null){
                                        Bundle mBundle = new Bundle();
                                        mBundle.putLong( RESULT_DATA_USER_ID, mUserID );

                                        mLogInResultReceiver.send( AuthenticationActivity.LogInResultReceiver.LOGIN_RESULT, mBundle );

                                        // This is single use result receiver to inform Authentication
                                        // activity when we have logged in so it can switch to mainactivity
                                        mLogInResultReceiver = null;

                                        mBinder.startRequests();
                                    }

                                    mBinder.login( mUserID );

                                    break;
                                default:
                                    Log.d( TAG, "LOGIN FAILED. STOPPING SERVICE" );
                                    logInError();
                            }
                        }
                    } );

                    return true;
                } else {
                    Log.d( TAG, "NO LOGIN DATA ON DATABASE. STOPPING SERVICE" );
                    // No user login info found. Stopping service
                    logInError();

                    return false;
                }
            } finally {
                if(c != null)
                    c.close();
            }
        }

        private void createSSLContext(Resources resources){
            try {
                char[] keyStorePassphrase = "removed".toCharArray();
                KeyStore ksKeys = KeyStore.getInstance("PKCS12");
                ksKeys.load(resources.openRawResource(R.raw.clientkeystore), keyStorePassphrase);

                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ksKeys, keyStorePassphrase);

                char[] trustStorePassphrase = "removed".toCharArray();
                KeyStore ksTrust = KeyStore.getInstance("PKCS12");
                ksTrust.load(resources.openRawResource(R.raw.clienttruststore), trustStorePassphrase);

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ksTrust);

                mSSLContext = SSLContext.getInstance("TLS");
                mSSLContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            } catch ( KeyStoreException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException | KeyManagementException | IOException e ) {
                e.printStackTrace();
            }
        }
    }

    public class FutureRequest implements Future<Sendable> {


        private volatile int state;

        public static final int NEW = 0;
        public static final int STARTED = 1;
        public static final int CANCELLED = 2;
        public static final int COMPLETED = 3;

        private ConnectionProtocol.Protocols mProtocol;
        private Sendable mRequest;
        private RequestResultCallbacks mCallback;

        public FutureRequest(ConnectionProtocol.Protocols protocol, Sendable sendable, RequestResultCallbacks callback){
            mProtocol = protocol;
            mRequest = sendable;
            mCallback = callback;
            state = NEW; // Visibility. Keep it last
        }

        /***
         * Get request message. Volatile state variable provides visibility for mRequest as it
         * is written from another thread in constructor and it is read from current thread.
         * @return Request message if not cancelled
         */
        public byte[] getMessage(){
            if( setStarted() ){
                return PacketParser.getMessage( mProtocol, mRequest ).array();
            }
            return null;
        }

        public synchronized boolean setStarted(){
            if(state != CANCELLED)
                this.state = STARTED;

            return state == STARTED;
        }

        @Override
        public synchronized boolean cancel( boolean b ) {
            if (state == NEW ){
                state = CANCELLED;
                return true;
            }
            return false;
        }

        @Override
        public boolean isCancelled() {
            return state == CANCELLED;
        }

        @Override
        public boolean isDone() {
            return state == COMPLETED;
        }

        private void done(final Reply reply){
            state = COMPLETED;
            mHandler.post( new Runnable() {
                @Override
                public void run() {
                    if( mCallback != null )
                        mCallback.OnRequestResult( reply );
                }
            } );
        }

        @Override
        public Sendable get() throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        public Sendable get( long l, TimeUnit timeUnit ) throws InterruptedException, ExecutionException, TimeoutException {
            return null;
        }
    }

    private class DataRequestThread extends Thread {

        private volatile boolean mIsRunning = true;
        private static final int REQUEST_TIME = 2*60*1000;

        @Override
        public void run() {
            while(mIsRunning){
                mBinder.transmit( ConnectionProtocol.Protocols.CIRCLE_DATA, null, new RequestResultCallbacks() {

                    @Override
                    public void OnRequestResult( Reply reply ) {
                        if(reply == null){
                            return;
                        }

                        switch( reply.mReplyCode ){
                            case DATA_REQUEST_SUCCESSFUL:

                                List<byte[]> messages = reply.getMessages();

                                String[] circles = new String( messages.get( 0 ), StandardCharsets.UTF_8 ).split( ";" );
                                String[] circleMembers = new String( messages.get( 1 ), StandardCharsets.UTF_8 ).split( ";" );
                                String[] users = new String( messages.get( 2 ), StandardCharsets.UTF_8 ).split( ";" );
                                String[] invites = new String( messages.get( 3 ), StandardCharsets.UTF_8 ).split( ";" );
                                String[] locations = new String( messages.get( 4 ), StandardCharsets.UTF_8 ).split( ";" );

                                ArrayList<ContentProviderOperation> batch = new ArrayList<>();

                                // No need to delete CircleMembers table as it has ON DELETE CASCADE.
                                // Deleting circles or friends will empty whole CircleMembers table
                                batch.add( ContentProviderOperation.newDelete( DatabaseProvider.CONTENT_URI_CIRCLE ).build() );

                                // This will delete location table entries except for user location
                                batch.add( ContentProviderOperation
                                        .newDelete( DatabaseProvider.CONTENT_URI_FRIEND )
                                        .withSelection( DatabaseProvider.FRIEND.ID + " <> " + mUserID, null )
                                        .build() );

                                batch.add( ContentProviderOperation.newDelete( DatabaseProvider.CONTENT_URI_JOIN_REQUEST ).build() );

                                for( String circle : circles ){
                                    String[] parts = circle.split( "," );

                                    batch.add( ContentProviderOperation
                                            .newInsert( DatabaseProvider.CONTENT_URI_CIRCLE )
                                            .withValue( DatabaseProvider.CIRCLE.ID.toString(), Long.parseLong( parts[0] ) )
                                            .withValue( DatabaseProvider.CIRCLE.NAME.toString(), parts[1] )
                                            .build()
                                    );
                                }

                                for( String user : users ){
                                    String[] parts = user.split( "," );

                                    batch.add( ContentProviderOperation
                                            .newInsert( DatabaseProvider.CONTENT_URI_FRIEND )
                                            .withValue( DatabaseProvider.FRIEND.ID.toString(), Long.parseLong( parts[0] ) )
                                            .withValue( DatabaseProvider.FRIEND.FIRSTNAME.toString(), parts[1] )
                                            .withValue( DatabaseProvider.FRIEND.LASTNAME.toString(), parts[2] )
                                            .build()
                                    );
                                }

                                for( String circleMember : circleMembers ){
                                    String[] parts = circleMember.split( "," );

                                    batch.add( ContentProviderOperation
                                            .newInsert( DatabaseProvider.CONTENT_URI_CIRCLE_MEMBERS )
                                            .withValue( DatabaseProvider.CIRCLE_MEMBERS.ID.toString(), Long.parseLong( parts[0] ) )
                                            .withValue( DatabaseProvider.CIRCLE_MEMBERS.CIRCLE_ID.toString(), Long.parseLong( parts[1] ) )
                                            .withValue( DatabaseProvider.CIRCLE_MEMBERS.FRIEND_ID.toString(), Long.parseLong( parts[2] ) )
                                            .build()
                                    );
                                }

                                for( String invite : invites ){
                                    String[] parts = invite.split( "," );

                                    batch.add( ContentProviderOperation
                                            .newInsert( DatabaseProvider.CONTENT_URI_JOIN_REQUEST )
                                            .withValue( DatabaseProvider.JOIN_REQUEST.CIRCLE_ID.toString(), Long.parseLong( parts[0] ) )
                                            .withValue( DatabaseProvider.JOIN_REQUEST.NAME.toString(), parts[1] )
                                            .build()
                                    );
                                }

                                for( String location : locations ){
                                    String[] parts = location.split( "," );

                                    batch.add( ContentProviderOperation
                                            .newInsert( DatabaseProvider.CONTENT_URI_LOCATION )
                                            .withValue( DatabaseProvider.LOCATION.FRIEND_ID.toString(), Long.parseLong( parts[0] ) )
                                            .withValue( DatabaseProvider.LOCATION.LATITUDE.toString(), Double.parseDouble( parts[1] ) )
                                            .withValue( DatabaseProvider.LOCATION.LONGITUDE.toString(), Double.parseDouble( parts[2] ) )
                                            .withValue( DatabaseProvider.LOCATION.ACCURACY.toString(), Double.parseDouble( parts[3] ) )
                                            .withValue( DatabaseProvider.LOCATION.TIME_RECORDED.toString(), Long.parseLong( parts[4] ) )
                                            .build()
                                    );
                                }

                                try {
                                    getContentResolver().applyBatch( DatabaseProvider.AUTHORITY, batch );
                                } catch( RemoteException | OperationApplicationException e ){
                                    Log.e(TAG,Log.getStackTraceString( e ));
                                }

                                break;
                            default:
                        }
                    }
                } );
                try {
                    // Can't be interrupted on back to back sleep cycles
                    Thread.sleep( REQUEST_TIME );
                } catch( InterruptedException e ){
                }
            }
            Log.d( TAG, "DataRequestThread terminating " );
        }

        public void requestNow(){
            interrupt();
        }

        public void shutdown(){
            mIsRunning = false;
            interrupt();
        }
    }

    private class LocationReceiver extends BroadcastReceiver {

        public static final String EXTRA_FROM_RECEIVER = "fi.joni.lehtinen.friendfinder.OnBootReceiver";

        @Override
        public void onReceive( Context context, Intent intent ) {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {

                Log.d( TAG, "Location turned on" );
            } else {
                Log.d( TAG, "Location turned off. Bind count: " + mBindCount );
                permissionError();
            }
        }
    }

    private void permissionError(){
        if( mBindCount == 0 && mAllowSelfShutdown ){
            stopSelf();
        } else {
            Bundle mBundle = new Bundle();
            mBundle.putBoolean( ServerConnectionResultReceiver.LOCATION_STATE_EXTRA, false );
            mLocationResultReceiver.send( ServerConnectionResultReceiver.LOCATION_RESULT, mBundle );
        }
    }

    private void logInError(){
        if( mBindCount == 0 && mAllowSelfShutdown ){
            stopSelf();
        } else {
            mLocationResultReceiver.send( ServerConnectionResultReceiver.LOGIN_FAIL_RESULT, null );
        }
    }
}
