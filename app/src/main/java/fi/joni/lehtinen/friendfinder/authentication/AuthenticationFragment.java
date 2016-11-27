package fi.joni.lehtinen.friendfinder.authentication;


import android.app.ActionBar;
import android.app.Fragment;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import fi.joni.lehtinen.friendfinder.R;


public class AuthenticationFragment extends Fragment {

    private Button mLogInButton;
    private Button mRegisterButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_authentication, container, false);

        final ActionBar mActionBar = getActivity().getActionBar();


        TextView title = (TextView) view.findViewById( R.id.authentication_title );
        Typeface myTypeface = Typeface.createFromAsset(getActivity().getAssets(), "fonts/thinking_of_betty.ttf");
        title.setTypeface(myTypeface);

        mLogInButton = (Button)view.findViewById(R.id.open_log_in);
        mLogInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(mActionBar != null)
                    mActionBar.setTitle(R.string.login_title);

                getFragmentManager().beginTransaction()
                        .replace(R.id.authentication_fragment_container, new LoginFragment())
                        .addToBackStack(null)
                        .commit();
            }
        });
        mRegisterButton = (Button)view.findViewById(R.id.open_registration);
        mRegisterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(mActionBar != null)
                    mActionBar.setTitle(R.string.registration_title);

                getFragmentManager().beginTransaction()
                        .replace(R.id.authentication_fragment_container, new RegistrationFragment())
                        .addToBackStack(null)
                        .commit();
            }
        });
        return view;
    }

}
