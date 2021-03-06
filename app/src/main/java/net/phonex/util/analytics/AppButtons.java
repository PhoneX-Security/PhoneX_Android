package net.phonex.util.analytics;

/**
 * Created by miroc on 2.9.15.
 */
public enum AppButtons {
    LOGIN,
    ENTER_CODE,
    APPLY_CODE,
    NEW_ACCOUNT,
    REFRESH_CAPTCHA,
    CREATE_ACCOUNT,
    NAV_DRAWER_BROADCAST_MESSAGE,
    NAV_DRAWER_MY_ACCOUNT,
    NAV_DRAWER_FILE_MANAGER,
    NAV_DRAWER_SETTINGS,
    NAV_DRAWER_HELP,
    NAV_DRAWER_LOGOUT,
    NAV_DRAWER_CAMERA,
    MAIN_ACTIVITY_ADD_CONTACT,
    MAIN_ACTIVITY_SEARCH,
    MAIN_ACTIVITY_BROADCAST_MESSAGE,
    MAIN_ACTIVITY_OPEN_CONVERSATION,
    MAIN_ACTIVITY_OPEN_NOTIFICATION_MENU,
    ADD_CONTACT_ADD,
    ADD_CONTACT_CHOOSE_ALIAS,
    CHANGE_STATUS,
    USER_MENU_CALL,
    USER_MENU_SEND_MESSAGE,
    USER_MENU_MORE,
    USER_MENU_DETAIL,
    USER_MENU_RENAME,
    USER_MENU_DELETE,
    MESSAGE_ACTIVITY_CALL,
    MESSAGE_ACTIVITY_ADD_ATTACHMENT,
    MESSAGE_ACTIVITY_SEND,
    ATTACHMENT_MENU_TAKE_PHOTO,
    ATTACHMENT_MENU_SEND_PICTURES,
    ATTACHMENT_MENU_SEND_FILES,
    MESSAGE_CONTEXT_MENU_FORWARD,
    MESSAGE_CONTEXT_MENU_DELETE,
    MESSAGE_CONTEXT_MENU_COPY,
    MESSAGE_CONTEXT_MENU_CANCEL_FILE_TRANSFER,
    MESSAGE_CONTEXT_MENU_DELETE_FILES,
    CALL_TERMINATE,
    CALL_DROP,
    CALL_ZRTP_TRUST,
    CALL_ZRTP_REVOKE,
    CALL_TAKE,
    CALL_NOT_TAKE_CALL,
    FILE_PICKER_CHANGE_DISPLAY_MODE,
    FILE_PICKER_SORT,
    BROADCAST_ACTIVITY_ADD_ATTACHMENT,
    BROADCAST_ACTIVITY_SEND,
    CAMERA_CAPTURE,
    CAMERA_ROTATE,
    CAMERA_SETTINGS,
    CAMERA_FLASH,
    CAMERA_CONFIRM,
    CAMERA_DELETE;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
