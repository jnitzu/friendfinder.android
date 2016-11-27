package fi.joni.lehtinen.friendfinder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.Semaphore;

public class ConnectionChangeReceiver extends BroadcastReceiver {

    private final Semaphore mServiceNoNetworkGate;

    public ConnectionChangeReceiver( Semaphore semaphore ) {
        mServiceNoNetworkGate = semaphore;
    }

    @Override
    public void onReceive( Context context, Intent intent ) {
        // This will unblock service
        mServiceNoNetworkGate.release();
    }
}
