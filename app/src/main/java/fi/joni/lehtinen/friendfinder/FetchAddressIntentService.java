package fi.joni.lehtinen.friendfinder;

import android.app.IntentService;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FetchAddressIntentService extends IntentService {

    private static final String TAG = "FetchAddressIS";

    public static final int SUCCESS_RESULT = 0;
    public static final int FAILURE_RESULT = 1;

    public static final String PACKAGE_NAME = "fi.joni.lehtinen.friendfinder";
    public static final String EXTRA_RECEIVER = PACKAGE_NAME + ".extra_receiver";
    public static final String EXTRA_LATLNG = PACKAGE_NAME + ".extra_latlng";
    public static final String EXTRA_USER_ID = PACKAGE_NAME + ".extra_user_id";
    public static final String RESULT_ADDRESS = PACKAGE_NAME + ".result_address";
    public static final String RESULT_USER_ID = PACKAGE_NAME + ".result_user_íd";

    private ResultReceiver mReceiver;
    private long mUserID = -1;

    public FetchAddressIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String errorMessage = "";

        mReceiver = intent.getParcelableExtra(EXTRA_RECEIVER);

        if (mReceiver == null) {
            Log.wtf(TAG, "No receiver received. There is nowhere to send the results.");
            return;
        }

        LatLng position = intent.getParcelableExtra(EXTRA_LATLNG);

        if (position == null) {
            errorMessage = getString(R.string.no_location_data_provided);
            Log.wtf(TAG, errorMessage);
            deliverResultToReceiver(FAILURE_RESULT, errorMessage);
            return;
        }

        mUserID = intent.getLongExtra( EXTRA_USER_ID, -1 );

        if (mUserID == -1) {
            errorMessage = getString(R.string.no_user_id_provided);
            Log.wtf(TAG, errorMessage);
            deliverResultToReceiver(FAILURE_RESULT, errorMessage);
            return;
        }

        // Locale.getDefault() gives swedish street addresses
        Geocoder geocoder = new Geocoder(this, Locale.forLanguageTag( "fi-FI" ));

        List<Address> addresses = null;

        try {
            // Using getFromLocation() returns an array of Addresses for the area immediately
            // surrounding the given latitude and longitude. The results are a best guess and are
            // not guaranteed to be accurate.
            addresses = geocoder.getFromLocation(
                    position.latitude,
                    position.longitude,
                    // In this sample, we get just a single address.
                    1);
        } catch (IOException ioException) {
            // Catch network or other I/O problems.
            errorMessage = getString(R.string.service_not_available);
            Log.e(TAG, errorMessage, ioException);
        } catch (IllegalArgumentException illegalArgumentException) {
            // Catch invalid latitude or longitude values.
            errorMessage = getString(R.string.invalid_lat_long_used);
            Log.e(TAG, errorMessage + ". " +
                    "Latitude = " + position.latitude +
                    ", Longitude = " + position.longitude, illegalArgumentException);
        }

        // Handle case where no address was found.
        if (addresses == null || addresses.size()  == 0) {
            if (errorMessage.isEmpty()) {
                errorMessage = getString(R.string.no_address_found);
                Log.e(TAG, errorMessage);
            }
            deliverResultToReceiver(FAILURE_RESULT, errorMessage);
        } else {
            Address address = addresses.get(0);
            ArrayList<String> addressFragments = new ArrayList<>();

            for(int i = 0; i < address.getMaxAddressLineIndex(); i++) {
                addressFragments.add(address.getAddressLine(i));
            }
            deliverResultToReceiver(SUCCESS_RESULT,
                    TextUtils.join(System.getProperty("line.separator"), addressFragments));
        }
    }

    private void deliverResultToReceiver(int resultCode, String message) {
        Bundle bundle = new Bundle();
        bundle.putString(RESULT_ADDRESS, message);
        bundle.putLong(RESULT_USER_ID, mUserID);
        mReceiver.send(resultCode, bundle);
    }
}