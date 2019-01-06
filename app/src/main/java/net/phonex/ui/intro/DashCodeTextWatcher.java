package net.phonex.ui.intro;

import android.text.Editable;
import android.text.TextWatcher;

/**
 * Text watcher for entering dash separated code
 */
public class DashCodeTextWatcher implements TextWatcher {
    private boolean deleting;

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (count > after){
            deleting = true;
        } else {
            deleting = false;
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable text) {
        if (deleting){
            return;
        }

        if (text.length() == 3 || text.length() == 7) {
            text.append('-');
        }
    }
}
