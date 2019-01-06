package net.phonex.ui.interfaces;

import net.phonex.soap.PasswordChangeParams;
import net.phonex.soap.entities.PasswordChangeV2Response;

public interface OnPasswordChangeCompleted{
	void onPasswordChangeCompleted(PasswordChangeV2Response response, PasswordChangeParams params);
}

