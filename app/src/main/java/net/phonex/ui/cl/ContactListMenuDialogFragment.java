package net.phonex.ui.cl;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;
import net.phonex.core.Constants;
import net.phonex.core.IService;
import net.phonex.db.entity.SipClist;
import net.phonex.db.entity.SipProfile;
import net.phonex.core.SipUri;
import net.phonex.pub.a.Compatibility;
import net.phonex.service.XService;
import net.phonex.ui.call.CallActivity;
import net.phonex.ui.chat.MessageActivity;
import net.phonex.ui.chat.MessageFragment;
import net.phonex.ui.dialogs.AlertDialogFragment;
import net.phonex.ui.dialogs.ContactRenameDialogFragment;
import net.phonex.ui.dialogs.DeleteContactDialogFragment;
import net.phonex.license.LicenseInformation;
import net.phonex.util.Log;
import net.phonex.util.analytics.AnalyticsReporter;
import net.phonex.util.analytics.AppButtons;

import java.util.ArrayList;
import java.util.List;

public class ContactListMenuDialogFragment extends AlertDialogFragment implements OnItemClickListener {
	private static final String THIS_FILE = "ContactListItemMenu";
	private SipClist clist;
    private LicenseInformation licenseInformation;

    private ArrayList<MenuEntry> items = new ArrayList<>();
    private MenuArrayAdapter itemsAdapter;

    private IService service;
    private String storagePass;
    private boolean shownMore = false;
    private boolean showEdit;

    private static final String EXTRA_CLIST = "extra_clist";
    private static final String EXTRA_SHOW_EDIT = "extra_show_edit";
    private static final String EXTRA_SHOWN_MORE = "extra_shown_more";

    // Help choices
    private final static int CALL = 0;
    private final static int TEXT = 1;
    private final static int PREFS = 2;
    private final static int DEL = 3;
    private final static int MORE = 4;
    private final static int RENAME = 5;
	
	public static ContactListMenuDialogFragment newInstance(SipClist clist) {
        return newInstance(clist, true);
    }

	public static ContactListMenuDialogFragment newInstance(SipClist clist, boolean showEdit) {
        ContactListMenuDialogFragment instance = new ContactListMenuDialogFragment();

        instance.addFirstMenu(showEdit);

        Bundle args = new Bundle();
        args.putParcelable(EXTRA_CLIST, clist);
        args.putBoolean(EXTRA_SHOW_EDIT, showEdit);
        instance.setArguments(args);
        return instance;
    }
	
    @Override
    public void onAttach(Activity activity) {
    	super.onAttach(activity);
    	getActivity().bindService(new Intent(getActivity(), XService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        clist = getArguments().getParcelable(EXTRA_CLIST);
        showEdit = getArguments().getBoolean(EXTRA_SHOW_EDIT);

        if (savedInstanceState != null){
            shownMore = savedInstanceState.getBoolean(EXTRA_SHOWN_MORE);
        }

        licenseInformation = SipProfile.getCurrentProfile(getActivity()).getLicenseInformation();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {    	
    	if (clist==null){
    		Log.w(THIS_FILE, "Clist is null");
    		dismissAllowingStateLoss();
    	}

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View bodyView = inflater.inflate(R.layout.clist_item_menu, null);
        ListView lv = (ListView) bodyView.findViewById(android.R.id.list);
        lv.setOnItemClickListener(this);
        itemsAdapter = new MenuArrayAdapter(getActivity(), items);
        lv.setAdapter(itemsAdapter);


        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .title(clist.getDisplayName())
                .iconRes(R.drawable.ic_person_black_24px)
//                .iconRes(R.drawable.svg_user)
                .customView(bodyView, false);
//                .negativeText(R.string.cancel);
        return builder.build();
    }

    private synchronized void addSecondMenu(){
        items.clear();
        // PREFERENCES
//        items.add(new MenuEntry(android.R.drawable.ic_menu_info_details, R.string.contact_details, PREFS));
        items.add(new MenuEntry(R.drawable.ic_info_black_24px, R.string.contact_details, PREFS));
        // Rename
//        items.add(new MenuEntry(android.R.drawable.ic_menu_edit, R.string.rename_account, RENAME));
        items.add(new MenuEntry(R.drawable.ic_mode_edit_black_24px, R.string.rename_account, RENAME));
        // DEL
//        items.add(new MenuEntry(android.R.drawable.ic_menu_delete, R.string.delete_account, DEL));
        items.add(new MenuEntry(R.drawable.ic_delete_black_24px, R.string.delete_account, DEL));
    }

    private synchronized void addFirstMenu(boolean showEdit){
        items.clear();

        // CALL
        if (Compatibility.isCallSupported()) {
//            items.add(new MenuEntry(android.R.drawable.ic_menu_call, R.string.call, CALL));
            items.add(new MenuEntry(R.drawable.ic_call_black_24px, R.string.call, CALL));
        }

        // TEXT
//        items.add(new MenuEntry(android.R.drawable.ic_menu_send, R.string.compose_title, TEXT));
        items.add(new MenuEntry(R.drawable.ic_message_black_24px, R.string.compose_title, TEXT));
        if (showEdit){
            // MORE
//            items.add(new MenuEntry(android.R.drawable.ic_menu_edit, R.string.edit, MORE));
            items.add(new MenuEntry(R.drawable.ic_mode_edit_black_24px, R.string.edit, MORE));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_SHOWN_MORE, shownMore);
    }

    private class MenuArrayAdapter extends ArrayAdapter<MenuEntry> {
    	public MenuArrayAdapter(Context ctxt, List<MenuEntry> items) {
			super(ctxt, R.layout.contact_menu_list_row, 0, items);
		}
    	
    	@Override
    	public View getView(int position, View convertView, ViewGroup parent) {

            if (convertView == null){
                convertView = LayoutInflater.from(getActivity()).inflate(R.layout.contact_menu_list_row, parent, false);
            }

    		bindView(convertView, getItem(position));
    		return convertView;
    	}
    	
    	/**
    	 * Bind the fiew to the help entry content
    	 * @param v the view to bind info to
    	 * @param he the help entry to display info of
    	 */
    	private void bindView(View v, MenuEntry he) {
    		TextView tv = (TextView) v.findViewById(R.id.title);
    		tv.setText(he.textRes);
            ImageView iv = (ImageView) v.findViewById(R.id.icon);
            iv.setImageResource(he.iconRes);
    	}
    }

	@Override
	public void onItemClick(AdapterView<?> av, View v, int position, long id) {
        Log.inf(THIS_FILE, "Item clicked : %s %s", id, position);
        MenuArrayAdapter haa = (MenuArrayAdapter) av.getAdapter();
		MenuEntry he = haa.getItem(position);
		
		switch (he.choiceTag) {
		case CALL:
		{
            AnalyticsReporter.from(this).buttonClick(AppButtons.USER_MENU_CALL);
            // Permission check
//            if (licenseInformation.isLicenseExpired()){
//                ManageLicenseActivity.redirectFrom(getActivity());
//                return;
//            }

            if (service != null){
                if(!TextUtils.isEmpty(clist.getSip())) {
                    CallActivity.invokeActivityForCall(getActivity(), clist.getSip(), clist.getAccount());
                    dismiss();
                }
            }
			break;
		}	
		case TEXT:
		{
            AnalyticsReporter.from(this).buttonClick(AppButtons.USER_MENU_SEND_MESSAGE);
			String sipCanonical = SipUri.getCanonicalSipContact(clist.getSip(), false);
			String sipUri = Constants.PROTOCOL_SIPS + ":" + sipCanonical;
			
			Bundle b = MessageFragment.getArguments(sipUri);
            Intent it = new Intent(getActivity(), MessageActivity.class);
            it.putExtras(b);
            getActivity().startActivity(it);
            dismiss();
            break;
		}		
		case PREFS:
		{
            AnalyticsReporter.from(this).buttonClick(AppButtons.USER_MENU_DETAIL);
			ContactInfoDialogFragment inst = ContactInfoDialogFragment.newInstance(getString(R.string.contact_details), clist);
			inst.setStoragePass(getStoragePass());
			inst.preLoad(getFragmentManager(), getActivity(), "");
			dismiss();			
			break;
		}	
		case RENAME:
		{
            AnalyticsReporter.from(this).buttonClick(AppButtons.USER_MENU_RENAME);
            ContactRenameDialogFragment fragment = ContactRenameDialogFragment.newInstance(clist, getStoragePass());
            fragment.show(getFragmentManager(), "");
            dismiss();
			break;
		}				
		case MORE:
		{
            AnalyticsReporter.from(this).buttonClick(AppButtons.USER_MENU_MORE);
            synchronized (this){
                shownMore = true;
                items.clear();
                addSecondMenu();
                itemsAdapter.notifyDataSetChanged();
            }
			break;
		}	
		case DEL:
		{
            AnalyticsReporter.from(this).buttonClick(AppButtons.USER_MENU_DELETE);
            DeleteContactDialogFragment fragment = DeleteContactDialogFragment.newInstance(clist, getStoragePass());
            fragment.show(getFragmentManager(), "");
            dismiss();
            break;
		}
		default:
			break;
		}
	}

	@Override
	public void onResume() {
		super.onResume();
	}

    // Service connection
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            service = IService.Stub.asInterface(arg1);
            loadStoragePassword();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            service = null;
        }
    };
    
    private void loadStoragePassword(){
    	if (service!=null){
    		try {
    			storagePass  = service.getStoragePassword();
			} catch (Exception e) {
				Log.e(THIS_FILE, "Cannot load storage password",e);
			}
    	}
    	
    }   
    
    @Override
    public void onDetach() {
        try {
            getActivity().unbindService(connection);
        } catch (Exception e) {
            // Just ignore that
        }
        service = null;
        super.onDetach();
    }

    public SipClist getClist() {
        return clist;
    }

    public void setClist(SipClist clist) {
        this.clist = clist;
    }

    public String getStoragePass(){
        return storagePass;
    }

    private class MenuEntry {
        public int iconRes;
        public int textRes;
        public int choiceTag;
        public MenuEntry(int icon, int text, int choice) {
            iconRes = icon;
            textRes = text;
            choiceTag = choice;
        }
    }

}
