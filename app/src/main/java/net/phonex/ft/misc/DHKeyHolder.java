package net.phonex.ft.misc;

import net.phonex.db.entity.DHOffline;
import net.phonex.soap.entities.FtDHKey;

/**
 * Class represents DH key.
 * Stores both database representation and server representation.
 * @author ph4r05
 *
 */
public class DHKeyHolder {
    public FtDHKey serverKey;
    public DHOffline dbKey;
}
