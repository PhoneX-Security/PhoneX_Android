package net.phonex.ui.pairingRequest;

import net.phonex.db.entity.PairingRequest;

/**
 * Created by Matus on 26-Aug-15.
 */
public interface PairingRequestResolutionListener {
    void onOptions(PairingRequest pairingRequest);
}
