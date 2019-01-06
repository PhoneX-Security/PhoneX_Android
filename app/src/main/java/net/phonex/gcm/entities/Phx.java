
package net.phonex.gcm.entities;

import java.util.ArrayList;
import java.util.List;
//import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import net.phonex.gcm.entities.GcmMessage;

//@Generated("org.jsonschema2pojo")
public class Phx {

    @SerializedName("msg")
    @Expose
    private List<GcmMessage> msg = new ArrayList<GcmMessage>();

    /**
     * 
     * @return
     *     The msg
     */
    public List<GcmMessage> getMsg() {
        return msg;
    }

    /**
     * 
     * @param msg
     *     The msg
     */
    public void setMsg(List<GcmMessage> msg) {
        this.msg = msg;
    }

}
