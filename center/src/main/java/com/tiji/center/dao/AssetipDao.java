package com.tiji.center.dao;

import com.tiji.center.pojo.Assetip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * assetip数据访问接口
 *
 * @author 贰拾壹
 */
public interface AssetipDao extends JpaRepository<Assetip, String>, JpaSpecificationExecutor<Assetip> {

    Assetip findByIpaddressv4AndPassivetimeIsNull(String ip);

    List<Assetip> findAllByPassivetimeIsNull();

    @Query(value = "select distinct ipaddressv4 from Assetip where passivetime is null")
    List<String> findAllDistinctIpaddressv4ListAndPassivetimeIsNull();

    //for test
    //getContactByIp
    @Query(value = "SELECT taip.ipaddressv4,tpi.projectname,tcn.name,tcn.email FROM tb_assetip taip,tb_contact tcn ,tb_projectinfo tpi WHERE tcn.projectinfoid=taip.projectinfoid and tpi.id=tcn.projectinfoid and ipaddressv4=?1", nativeQuery = true)
    List<String> findContactInfoByIpv4(String ipaddressv4);

    Assetip findByIdAndCheckwhitelistIsFalseAndPassivetimeIsNull(String id);


    @Modifying
    @Query(value = "UPDATE tb_assetip taip SET taip.checkwhitelist=?2 , taip.assetnotifywhitelist=?3 WHERE taip.projectinfoid=?1", nativeQuery = true)
    void updateByProjectinfoidAndCheckwhitelistAndAssetNotifywhitelist(String projectinfoid, Boolean checkwhitelist, Boolean assetNotifywhitelist);

    @Modifying
    @Query(value = "UPDATE tb_assetip SET projectinfoid = null WHERE projectinfoid=?1", nativeQuery = true)
    void updateAssetipByProjectinfoidSetProjectinfoid2Null(String projectinfoid);

    @Modifying
    @Query(value = "UPDATE tb_assetip SET projectinfoid = null", nativeQuery = true)
    void updateAssetipSetProjectinfoidNull();

    @Modifying
    @Query(value = "DELETE FROM `tb_assetip` WHERE id IN(?1)", nativeQuery = true)
    void deleteAllByIds(List<String> ids);

    @Query(value = "SELECT taip.id,COUNT(*) as portCount FROM tb_assetip taip,tb_assetport tp WHERE tp.assetipid = taip.id and taip.id in (?1) GROUP BY taip.id", nativeQuery = true)
    List<String> findPortCountByIds(List<String> ids);

    @Query(value = "SELECT taip.id,COUNT(*) as portCount FROM tb_assetip taip,tb_assetport tp WHERE tp.assetipid = taip.id and taip.id in (?1) and ISNULL(tp.downtime) GROUP BY taip.id", nativeQuery = true)
    List<String> findPortCountOnlineByIds(List<String> ids);

    @Query(value = "SELECT tp.assetipid,COUNT(*) as vulnCount FROM tb_assetport tp,tb_checkresult tcl  WHERE tcl.assetportid IN(tp.id)  and tp.assetipid IN (?1) GROUP BY tp.assetipid", nativeQuery = true)
    List<String> findVulnCountByIds(List<String> ids);


    @Query(value = "SELECT tp.assetipid,COUNT(*) as vulnCount FROM tb_assetport tp,tb_checkresult tcl  WHERE tcl.assetportid IN(tp.id)  and tp.assetipid IN (?1) and ISNULL(tcl.passivetime) GROUP BY tp.assetipid", nativeQuery = true)
    List<String> findVulnCountOnlineByIds(List<String> ids);

}
