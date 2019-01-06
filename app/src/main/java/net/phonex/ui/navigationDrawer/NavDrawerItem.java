package net.phonex.ui.navigationDrawer;

/**
 * Created by miroc on 9.4.15.
 */
public class NavDrawerItem {
    private String title;
    private int icon;
    private int id;

    public NavDrawerItem(String title, int icon, int id) {
        this.title = title;
        this.icon = icon;
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getIcon() {
        return icon;
    }

    public void setIcon(int icon) {
        this.icon = icon;
    }
}
