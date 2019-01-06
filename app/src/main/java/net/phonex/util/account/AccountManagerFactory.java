package net.phonex.util.account;

import net.phonex.R;

/**
 * Created by ph4r05 on 7/1/14.
 */
public class AccountManagerFactory {
    private static AccountManagerDesc defaultManager;

    /**
     * Returns information about default account manager.
     * @return
     */
    public static AccountManagerDesc getDefaultManager(){
        if (defaultManager!=null){
            return defaultManager;
        }

        defaultManager = getManager(getDefaultManagerId());
        return defaultManager;
    }

    /**
     * Returns default manager ID.
     * @return
     */
    public static String getDefaultManagerId(){
        return PhonexAccountManager.getId();
    }

    /**
     * Constructs manager from string identifier.
     * @param id
     * @return
     */
    public static AccountManagerDesc getManager(String id){
        if (id==null){
            throw new IllegalArgumentException("Null id");
        }

        if (PhonexAccountManager.idMatches(id)){
            if (defaultManager!=null){
                return defaultManager;
            }

            return buildManager(id);
        } else {
            return buildManager(id);
        }
    }

    /**
     * Creates a new object for defined manager ID.
     * @param id
     * @return
     */
    private static AccountManagerDesc buildManager(String id){
        if (id==null){
            throw new IllegalArgumentException("Null id");
        }

        if (PhonexAccountManager.idMatches(id)){
            return new AccountManagerDesc(
                    PhonexAccountManager.getLabel(),
                    PhonexAccountManager.getId(),
//                    R.drawable.icon,
                    R.drawable.svg_logo_square_small,
                    R.drawable.svg_logo_square_small_bw,
                    3,
                    PhonexAccountManager.class);
        } else {
            return null;
        }
    }

}
