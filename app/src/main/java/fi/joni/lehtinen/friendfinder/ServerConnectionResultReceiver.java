package fi.joni.lehtinen.friendfinder;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

public class ServerConnectionResultReceiver extends ResultReceiver {

    public static final int LOCATION_RESULT = 10;
    public static final int LOGIN_FAIL_RESULT = 20;
    public static final String LOCATION_STATE_EXTRA = "fi.joni.lehtinen.friendfinder.ServerConnectionResultReceiver.location_state";

    private OnLocationChange mOnLocationChange;
    private OnLoginError mOnLoginError;

    public ServerConnectionResultReceiver( Handler handler ) {
        super( handler );
    }

    public void setOnLocationChange( OnLocationChange mOnLocationChange ) {
        this.mOnLocationChange = mOnLocationChange;
    }

    public void setOnLoginError( OnLoginError mOnLoginError ) {
        this.mOnLoginError = mOnLoginError;
    }

    @Override
    protected void onReceiveResult( int resultCode, Bundle resultData ) {
        switch( resultCode ){
            case LOCATION_RESULT:
                if( mOnLocationChange != null )
                    mOnLocationChange.onLocationChange(resultData.getBoolean( LOCATION_STATE_EXTRA ));
                break;
            case LOGIN_FAIL_RESULT:
                if( mOnLoginError != null )
                    mOnLoginError.onLoginError();
                break;
        }
    }

    public interface OnLocationChange {
        void onLocationChange(boolean isLocationOn);
    }
    public interface OnLoginError {
        void onLoginError();
    }
}
