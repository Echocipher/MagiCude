package com.tiji.center.dao;

import com.tiji.center.pojo.Assetip;
import com.tiji.center.pojo.Projectinfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * projectinfo数据访问接口
 *
 * @author 贰拾壹
 */
public interface ProjectinfoDao extends JpaRepository<Projectinfo, String>, JpaSpecificationExecutor<Projectinfo> {
    Projectinfo findByProjectname(String ProjectName);

    @Query(value =
            "SELECT id,projectname FROM tb_projectinfo;", nativeQuery = true)
    List<String> findIdAndProjectname();

    Projectinfo findByDepartmentidAndProjectname(String departmentid, String projectname);


    @Modifying
    @Query(value = "DELETE FROM `tb_projectinfo` WHERE id IN(?1)", nativeQuery = true)
    void deleteAllByIds(List<String> ids);

    @Modifying
    @Query(value = "UPDATE tb_projectinfo SET departmentid =NULL WHERE departmentid=?1", nativeQuery = true)
    void updateProjectInfoByDepartidSetDepartid2Null(String departid);
}
