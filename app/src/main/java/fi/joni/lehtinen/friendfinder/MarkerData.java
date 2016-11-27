package fi.joni.lehtinen.friendfinder;

import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;

public class MarkerData {

    public final long mUserID;
    public final String mFirstname;
    public final String mLastname;
    public final LatLng mPosition;
    public final double mAccuracy;
    public final long mTimeRecorded;
    private String mAddress;
    private TextView mAddressTextView;

    public MarkerData( long mUserID, String mFirstname, String mLastname, double mLatitude, double mLongitude, double mAccuracy, long mTimeRecorded ) {
        this.mAccuracy = mAccuracy;
        this.mUserID = mUserID;
        this.mFirstname = mFirstname;
        this.mLastname = mLastname;
        this.mPosition = new LatLng( mLatitude, mLongitude ) ;
        this.mTimeRecorded = mTimeRecorded;
    }

    public void setAddress( String address ){
        mAddress = address;
        if( mAddressTextView != null )
            mAddressTextView.setText( mAddress );
    }

    public String getAddress(){
        return mAddress;
    }

    public void setAddressTextView( TextView addressTextView ){
        mAddressTextView = addressTextView;
    }

}
