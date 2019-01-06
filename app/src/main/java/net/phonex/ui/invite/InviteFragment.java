package net.phonex.ui.invite;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import net.phonex.R;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * @author miroc
 */
public class InviteFragment extends Fragment{
    private static final String TAG = "InviteFragment";
    public static final String FRAGMENT_TAG = "InviteFragment";
    public static final String APP_STORE_REDIRECT_URL = "http://phone-x.net/get";

    @InjectView(R.id.button_share) Button shareButton;
    @InjectView(R.id.share_text) EditText shareEditText;

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_invite_friend, container, false);
        ButterKnife.inject(this, view);

        shareEditText.setText(getString(R.string.invite_friend_text, APP_STORE_REDIRECT_URL));

        shareButton.setOnClickListener(v -> onShareClick());
        return view;
    }

    private void onShareClick(){
        String textToShare = shareEditText.getText().toString();

        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.phonex_invitation));
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, textToShare);
        startActivity(Intent.createChooser(sharingIntent, getString(R.string.invite_to_phonex)));
    }
}
