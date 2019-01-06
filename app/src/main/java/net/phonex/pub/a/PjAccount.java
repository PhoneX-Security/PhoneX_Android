package net.phonex.pub.a;

import android.content.Context;
import android.text.TextUtils;

import net.phonex.pref.PhonexConfig;
import net.phonex.db.entity.SipProfile;
import net.phonex.core.SipUri;
import net.phonex.core.SipUri.ParsedSipContactInfos;
import net.phonex.pref.PreferencesConnector;
import net.phonex.sip.PjConfig;
import net.phonex.sip.PjUtils;
import net.phonex.util.Log;

import net.phonex.xv.SWIGTYPE_p_pj_stun_auth_cred;
import net.phonex.xv.Xvi;
import net.phonex.xv.pj_qos_params;
import net.phonex.xv.pj_qos_type;
import net.phonex.xv.pj_str_t;
import net.phonex.xv.pjmedia_srtp_use;
import net.phonex.xv.pjsip_auth_clt_pref;
import net.phonex.xv.pjsip_cred_info;
import net.phonex.xv.XviConstants;
import net.phonex.xv.pjsua_acc_config;
import net.phonex.xv.pjsua_ice_config;
import net.phonex.xv.pjsua_ice_config_use;
import net.phonex.xv.pjsua_stun_use;
import net.phonex.xv.pjsua_transport_config;
import net.phonex.xv.pjsua_turn_config;
import net.phonex.xv.pjsua_turn_config_use;
import net.phonex.xv.sipstack_acc_config;

public class PjAccount {
    private static final String TAG = "PjAccount";
    // For now everything is public, easiest to manage
    public String accountManager;
    public boolean active;
    public pjsua_acc_config cfg;
    public sipstack_acc_config css_cfg;
    public Long id;
    public Integer transport = 0;
    private String displayName;
    private int profile_vid_auto_show = -1;
    private int profile_vid_auto_transmit = -1;
    private int profile_enable_qos;
    private int profile_qos_dscp;
    private boolean profile_default_rtp_port = true;

    private PjAccount() {
        cfg = new pjsua_acc_config();
        Xvi.acc_config_default(cfg);

        css_cfg = new sipstack_acc_config();
        Xvi.sipstack_acc_config_default(css_cfg);
    }

    /**
     * Initialize from a SipProfile (public api) object
     *
     * @param profile the sip profile to use
     */
    public PjAccount(SipProfile profile) {
        this();

        Log.d(TAG, "PjAccount from PjProfile");
        if (profile.getId() != SipProfile.INVALID_ID) {
            id = profile.getId();
        }

        displayName = profile.getDisplay_name();
        accountManager = profile.getAccountManager();
        transport = profile.getTransport();
        active = profile.isActive();
        transport = profile.getTransport();


        cfg.setPriority(profile.getPriority());
        if (profile.getAcc_id() != null) {
            cfg.setId(Xvi.pj_str_copy(profile.getAcc_id()));
        }
        if (profile.getReg_uri() != null) {
            cfg.setReg_uri(Xvi.pj_str_copy(profile.getReg_uri()));
        }
//        if (profile.publish_enabled != -1) {
//        }

        // Setting PJSIP enabled
        cfg.setPublish_enabled(0);

        if (profile.getReg_timeout() != -1) {
            cfg.setReg_timeout(profile.getReg_timeout());
        }
        if (profile.getReg_delay_before_refresh() != -1) {
            cfg.setReg_delay_before_refresh(profile.getReg_delay_before_refresh());
        }
        if (profile.getKa_interval() != -1) {
            cfg.setKa_interval(profile.getKa_interval());
            Log.df(TAG, "KeepAlive interval set to: %d", profile.getKa_interval());
        }
        if (profile.getPidf_tuple_id() != null) {
            cfg.setPidf_tuple_id(Xvi.pj_str_copy(profile.getPidf_tuple_id()));
        }
        if (profile.getForce_contact() != null) {
            cfg.setForce_contact(Xvi.pj_str_copy(profile.getForce_contact()));
        }

        cfg.setAllow_contact_rewrite(profile.isAllow_contact_rewrite() ? XviConstants.PJ_TRUE : XviConstants.PJ_FALSE);
        cfg.setContact_rewrite_method(profile.getContact_rewrite_method());
        cfg.setAllow_via_rewrite(profile.isAllow_via_rewrite() ? XviConstants.PJ_TRUE : XviConstants.PJ_FALSE);


        if (profile.getUse_srtp() != -1) {
            cfg.setUse_srtp(pjmedia_srtp_use.swigToEnum(profile.getUse_srtp()));
            cfg.setSrtp_secure_signaling(0);
        }

        css_cfg.setUse_zrtp(profile.getUse_zrtp());


        if (profile.getProxies() != null) {
            Log.df(TAG, "Create proxy %s", profile.getProxies().length);
            cfg.setProxy_cnt(profile.getProxies().length);
            pj_str_t[] proxies = cfg.getProxy();
            int i = 0;
            for (String proxy : profile.getProxies()) {
                Log.df(TAG, "Add proxy %s", proxy);
                proxies[i] = Xvi.pj_str_copy(proxy);
                i += 1;
            }
            cfg.setProxy(proxies);
        } else {
            cfg.setProxy_cnt(0);
        }
        cfg.setReg_use_proxy(profile.getReg_use_proxy());

        if (profile.getUsername() != null || profile.getData() != null) {
            cfg.setCred_count(1);
            pjsip_cred_info cred_info = cfg.getCred_info();

            if (profile.getRealm() != null) {
                cred_info.setRealm(Xvi.pj_str_copy(profile.getRealm()));
            }
            if (profile.getUsername() != null) {
                cred_info.setUsername(Xvi.pj_str_copy(profile.getUsername()));
            }
            if (profile.getDatatype() != -1) {
                cred_info.setData_type(profile.getDatatype());
            }
            if (profile.getData() != null) {
                cred_info.setData(Xvi.pj_str_copy(profile.getData()));
            }
        } else {
            cfg.setCred_count(0);
        }

        // Registration settings.
        cfg.setRegister_tsx_timeout(30000l);
        // Re-registration retry time, set to 60 seconds.
        cfg.setReg_retry_interval(60);
        cfg.setReg_first_retry_interval(5); // Let first 4 re-registration attempts perform quick reconnection.
        cfg.setReg_attempts_quick_retry(4);

        // Auth prefs
        {
            pjsip_auth_clt_pref authPref = cfg.getAuth_pref();
            authPref.setInitial_auth(profile.isInitial_auth() ? XviConstants.PJ_TRUE : XviConstants.PJ_FALSE);
            if (!TextUtils.isEmpty(profile.getAuth_algo())) {
                authPref.setAlgorithm(Xvi.pj_str_copy(profile.getAuth_algo()));
            }
            cfg.setAuth_pref(authPref);
        }

        cfg.setMwi_enabled(profile.isMwi_enabled() ? XviConstants.PJ_TRUE : XviConstants.PJ_FALSE);
        /*cfg.setIpv6_media_use(profile.ipv6_media_use == 1 ? pjsua_ipv6_use.PJSUA_IPV6_ENABLED
	                : pjsua_ipv6_use.PJSUA_IPV6_DISABLED);*/

        // RFC5626
        cfg.setUse_rfc5626(profile.isUse_rfc5626() ? XviConstants.PJ_TRUE : XviConstants.PJ_FALSE);
        Log.df(TAG, "RFC5626: %s", profile.isUse_rfc5626());

        if (!TextUtils.isEmpty(profile.getRfc5626_instance_id())) {
            cfg.setRfc5626_instance_id(Xvi.pj_str_copy(profile.getRfc5626_instance_id()));
        }
        if (!TextUtils.isEmpty(profile.getRfc5626_reg_id())) {
            cfg.setRfc5626_reg_id(Xvi.pj_str_copy(profile.getRfc5626_reg_id()));
        }


        // Video
        profile_vid_auto_show = profile.getVid_in_auto_show();
        profile_vid_auto_transmit = profile.getVid_out_auto_transmit();


        // Rtp cfg
        pjsua_transport_config rtpCfg = cfg.getRtp_cfg();
        if (profile.getRtp_port() >= 0) {
            rtpCfg.setPort(profile.getRtp_port());
            profile_default_rtp_port = false;
        }
        if (!TextUtils.isEmpty(profile.getRtp_public_addr())) {
            rtpCfg.setPublic_addr(Xvi.pj_str_copy(profile.getRtp_public_addr()));
        }
        if (!TextUtils.isEmpty(profile.getRtp_bound_addr())) {
            rtpCfg.setBound_addr(Xvi.pj_str_copy(profile.getRtp_bound_addr()));
        }

        profile_enable_qos = profile.getRtp_enable_qos();
        profile_qos_dscp = profile.getRtp_qos_dscp();

        cfg.setSip_stun_use(profile.getSip_stun_use() == 0 ? pjsua_stun_use.PJSUA_STUN_USE_DISABLED : pjsua_stun_use.PJSUA_STUN_USE_DEFAULT);
        cfg.setMedia_stun_use(profile.getMedia_stun_use() == 0 ? pjsua_stun_use.PJSUA_STUN_USE_DISABLED : pjsua_stun_use.PJSUA_STUN_USE_DEFAULT);
        if (profile.getIce_cfg_use() == 1) {
            Log.df(TAG, "ICE_CFG_USE=1; enable=[%s]", profile.getIce_cfg_enable());

            cfg.setIce_cfg_use(pjsua_ice_config_use.PJSUA_ICE_CONFIG_USE_CUSTOM);
            pjsua_ice_config iceCfg = cfg.getIce_cfg();
            iceCfg.setEnable_ice((profile.getIce_cfg_enable() == 1) ? XviConstants.PJ_TRUE : XviConstants.PJ_FALSE);

        } else {
            cfg.setIce_cfg_use(pjsua_ice_config_use.PJSUA_ICE_CONFIG_USE_DEFAULT);
        }

        if (profile.getTurn_cfg_use() == 1) {
            Log.df(TAG, "TURN_CFG_USE=1; enable=[%s] - profile", profile.getTurn_cfg_enable());

            cfg.setTurn_cfg_use(pjsua_turn_config_use.PJSUA_TURN_CONFIG_USE_CUSTOM);
            pjsua_turn_config turnCfg = cfg.getTurn_cfg();
            SWIGTYPE_p_pj_stun_auth_cred creds = turnCfg.getTurn_auth_cred();
            turnCfg.setEnable_turn((profile.getTurn_cfg_enable() == 1) ? XviConstants.PJ_TRUE : XviConstants.PJ_FALSE);
            turnCfg.setTurn_server(Xvi.pj_str_copy(profile.getTurn_cfg_server()));
            Xvi.set_turn_credentials(
                    Xvi.pj_str_copy(profile.getTurn_cfg_user()),
                    Xvi.pj_str_copy(profile.getTurn_cfg_password()),
                    Xvi.pj_str_copy("*"),
                    creds);

            Log.df(TAG, "TURN cred-profile; user=[%s]; ISNULL(pwd)=%s",
                    profile.getTurn_cfg_user(),
                    ((profile.getTurn_cfg_password()) == (null)));
            // Normally this step is useless as manipulating a pointer in C memory at this point, but in case this changes reassign
            turnCfg.setTurn_auth_cred(creds);
        } else {
            cfg.setTurn_cfg_use(pjsua_turn_config_use.PJSUA_TURN_CONFIG_USE_DEFAULT);
        }
    }


    /**
     * Automatically apply specific parameters to the account
     *
     * @param ctxt
     */
    public void applyExtraParams(Context ctxt) {

        // Transport
        String regUri = "";
        String argument = "";
        switch (transport) {
            case SipProfile.TRANSPORT_UDP:
                argument = ";transport=udp;lr";
                break;
            case SipProfile.TRANSPORT_TCP:
                argument = ";transport=tcp;lr";
                break;
            case SipProfile.TRANSPORT_TLS:
                //TODO : differentiate ssl/tls ?
                argument = ";transport=tls;lr";
                break;
            default:
                break;
        }

        if (!TextUtils.isEmpty(argument)) {
            regUri = PjUtils.pjStrToString(cfg.getReg_uri());
            if (!TextUtils.isEmpty(regUri)) {
                long initialProxyCnt = cfg.getProxy_cnt();
                pj_str_t[] proxies = cfg.getProxy();

                //TODO : remove lr and transport from uri
                //		cfg.setReg_uri(pjsua.pj_str_copy(proposed_server));
                String firstProxy = PjUtils.pjStrToString(proxies[0]);
                if (initialProxyCnt == 0 || TextUtils.isEmpty(firstProxy)) {
                    cfg.setReg_uri(Xvi.pj_str_copy(regUri + argument));
                    cfg.setProxy_cnt(0);
                } else {
                    proxies[0] = Xvi.pj_str_copy(firstProxy + argument);
                    cfg.setProxy(proxies);
                }
//				} else {
//					proxies[0] = pjsua.pj_str_copy(proxies[0].getPtr() + argument);
//					cfg.setProxy(proxies);
//				}
            }
        }

        //Caller id
        PreferencesConnector prefs = new PreferencesConnector(ctxt);
        String defaultCallerid = prefs.getString(PhonexConfig.DEFAULT_CALLER_ID);
        // If one default caller is set
        if (!TextUtils.isEmpty(defaultCallerid)) {
            String accId = PjUtils.pjStrToString(cfg.getId());
            ParsedSipContactInfos parsedInfos = SipUri.parseSipContact(accId);
            if (TextUtils.isEmpty(parsedInfos.displayName)) {
                // Apply new display name
                parsedInfos.displayName = defaultCallerid;
                cfg.setId(Xvi.pj_str_copy(parsedInfos.toString()));
            }
        }

        // Keep alive
        int ka = prefs.getUdpKeepAliveInterval();
        Log.df(TAG, "Setting KA to: %d", ka);
        cfg.setKa_interval(ka);

        // Video
        if (profile_vid_auto_show >= 0) {
            cfg.setVid_in_auto_show((profile_vid_auto_show == 1) ? XviConstants.PJ_TRUE : XviConstants.PJ_FALSE);
        } else {
            cfg.setVid_in_auto_show(XviConstants.PJ_TRUE);
        }
        if (profile_vid_auto_transmit >= 0) {
            cfg.setVid_out_auto_transmit((profile_vid_auto_transmit == 1) ? XviConstants.PJ_TRUE : XviConstants.PJ_FALSE);
        } else {
            cfg.setVid_out_auto_transmit(XviConstants.PJ_TRUE);
        }


        // RTP cfg
        pjsua_transport_config rtpCfg = cfg.getRtp_cfg();
        if (profile_default_rtp_port) {
            rtpCfg.setPort(prefs.getRTPPort());
        }
        boolean hasQos = prefs.getBoolean(PhonexConfig.ENABLE_QOS);
        if (profile_enable_qos >= 0) {
            hasQos = (profile_enable_qos == 1);
        }
        if (hasQos) {
            // TODO - video?
            rtpCfg.setQos_type(pj_qos_type.PJ_QOS_TYPE_VOICE);
            pj_qos_params qosParam = rtpCfg.getQos_params();
            // Default for RTP layer is different than default for SIP layer.
            short dscpVal = (short) prefs.getInteger(PhonexConfig.DSCP_RTP_VAL);
            if (profile_qos_dscp >= 0) {
                // If not set, we don't need to change dscp value
                dscpVal = (short) profile_qos_dscp;
                qosParam.setDscp_val(dscpVal);
                qosParam.setFlags((short) 1); // DSCP
            }
        }

        // P2P forbidden?
        // ICE candidates blacklist.
        if (prefs.getBoolean(PhonexConfig.P2P_DISABLE)) {
            long ice_blacklist = PjConfig.getICEBlacklistMap(true);
            cfg.getIce_cfg().getIce_opt().setCand_blacklist_map(ice_blacklist);
            Log.vf(TAG, "Disabling P2P on acount basis, mask: 0x%x", ice_blacklist);
        }
    }

    /**
     * @return the displayName
     */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (o != null && o.getClass() == PjAccount.class) {
            PjAccount oAccount = (PjAccount) o;
            return oAccount.id == id;
        }
        return super.equals(o);
    }
}
