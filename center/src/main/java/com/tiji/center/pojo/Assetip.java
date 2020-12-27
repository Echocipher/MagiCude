package com.tiji.center.pojo;

import com.tiji.center.pojo.category.CategoryTab;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * assetip实体类
 *
 * @author 贰拾壹
 */
@Entity
@Table(name = "tb_assetip")
public class Assetip implements Serializable {

    @Id
    private String id;//资产ip编号

    @Transient
    private String statistic;
    @Transient
    private String projectname;

    private String projectinfoid;//联系表编号
    private String ipaddressv4;//ip地址
    private String ipaddressv6;//ipaddressv6
    private Boolean checkwhitelist;//安全检测白名单
    private Boolean assetnotifywhitelist;//资产提醒白名单
    private java.util.Date activetime;//ip发现时间
    private java.util.Date passivetime;//ip下线时间
    private String remark;//备注
    //标签bitmap
    private String tabbitmap;
    //标签名
    @Transient
    private String tabname;
    //标签
    @Transient
    private List<CategoryTab> tabList;
    @Transient
    //应用系统名称
    private String appsysname;
    public Assetip() {
    }

    public Assetip(String id, String projectinfoid, String ipaddressv4, String ipaddressv6, Boolean checkwhitelist, Boolean assetnotifywhitelist, Date activetime, Date passivetime, String remark) {
        this.id = id;
        this.projectinfoid = projectinfoid;
        this.ipaddressv4 = ipaddressv4;
        this.ipaddressv6 = ipaddressv6;
        this.checkwhitelist = checkwhitelist;
        this.assetnotifywhitelist = assetnotifywhitelist;
        this.activetime = activetime;
        this.passivetime = passivetime;
        this.remark = remark;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatistic() {
        return statistic;
    }

    public void setStatistic(String statistic) {
        this.statistic = statistic;
    }

    public String getProjectname() {
        return projectname;
    }

    public void setProjectname(String projectname) {
        this.projectname = projectname;
    }

    public String getProjectinfoid() {
        return projectinfoid;
    }

    public void setProjectinfoid(String projectinfoid) {
        this.projectinfoid = projectinfoid;
    }

    public String getIpaddressv4() {
        return ipaddressv4;
    }

    public void setIpaddressv4(String ipaddressv4) {
        this.ipaddressv4 = ipaddressv4;
    }

    public String getIpaddressv6() {
        return ipaddressv6;
    }

    public void setIpaddressv6(String ipaddressv6) {
        this.ipaddressv6 = ipaddressv6;
    }

    public Boolean getCheckwhitelist() {
        return checkwhitelist;
    }

    public void setCheckwhitelist(Boolean checkwhitelist) {
        this.checkwhitelist = checkwhitelist;
    }

    public Boolean getAssetnotifywhitelist() {
        return assetnotifywhitelist;
    }

    public void setAssetnotifywhitelist(Boolean assetnotifywhitelist) {
        this.assetnotifywhitelist = assetnotifywhitelist;
    }

    public java.util.Date getActivetime() {
        return activetime;
    }

    public void setActivetime(java.util.Date activetime) {
        this.activetime = activetime;
    }

    public java.util.Date getPassivetime() {
        return passivetime;
    }

    public void setPassivetime(java.util.Date passivetime) {
        this.passivetime = passivetime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getTabbitmap() {
        return tabbitmap;
    }

    public void setTabbitmap(String tabbitmap) {
        this.tabbitmap = tabbitmap;
    }

    public String getTabname() {
        return tabname;
    }

    public void setTabname(String tabname) {
        this.tabname = tabname;
    }

    public List<CategoryTab> getTabList() {
        return tabList;
    }

    public void setTabList(List<CategoryTab> tabList) {
        this.tabList = tabList;
    }

    public String getAppsysname() {
        return appsysname;
    }

    public void setAppsysname(String appsysname) {
        this.appsysname = appsysname;
    }
}
