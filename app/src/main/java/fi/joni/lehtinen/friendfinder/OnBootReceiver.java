package fi.joni.lehtinen.friendfinder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class OnBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive( Context context, Intent intent ) {
        Intent myIntent = new Intent(context, ServerConnectionService.class);
        Log.d( "OnBootReceiver", "Starting service!" );
        context.startService(myIntent);
    }
}
