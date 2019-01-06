package net.phonex.soap.jsonEntities;
import com.google.gson.annotations.Expose;

public class Event {

    @Expose
    private Integer id;
    @Expose
    private Long date;
    @Expose
    private Integer type;

    /**
     *
     * @return
     * The id
     */
    public Integer getId() {
        return id;
    }

    /**
     *
     * @param id
     * The id
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     *
     * @return
     * The date
     */
    public Long getDate() {
        return date;
    }

    /**
     *
     * @param date
     * The date
     */
    public void setDate(Long date) {
        this.date = date;
    }

    /**
     *
     * @return
     * The type
     */
    public Integer getType() {
        return type;
    }

    /**
     *
     * @param type
     * The type
     */
    public void setType(Integer type) {
        this.type = type;
    }

}
