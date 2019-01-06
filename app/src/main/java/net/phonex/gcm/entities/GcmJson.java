
package net.phonex.gcm.entities;

//import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

//@Generated("org.jsonschema2pojo")
public class GcmJson {

    @SerializedName("phx")
    @Expose
    private Phx phx;

    /**
     * 
     * @return
     *     The phx
     */
    public Phx getPhx() {
        return phx;
    }

    /**
     * 
     * @param phx
     *     The phx
     */
    public void setPhx(Phx phx) {
        this.phx = phx;
    }

}
